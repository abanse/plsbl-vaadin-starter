package com.hydro.plsbl.service;

import com.hydro.plsbl.dto.IngotDTO;
import com.hydro.plsbl.dto.StockyardDTO;
import com.hydro.plsbl.dto.TransportOrderDTO;
import com.hydro.plsbl.entity.enums.OrderStatus;
import com.hydro.plsbl.plc.PlcException;
import com.hydro.plsbl.plc.PlcService;
import com.hydro.plsbl.plc.dto.JobState;
import com.hydro.plsbl.plc.dto.PlcCommand;
import com.hydro.plsbl.plc.dto.PlcStatus;
import com.hydro.plsbl.plc.dto.WorkPhase;
import com.hydro.plsbl.service.DataBroadcaster.DataEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Automatische Verarbeitung von Transportaufträgen
 *
 * Dieser Service:
 * - Holt wartende Aufträge aus der Datenbank
 * - Sendet Kran-Befehle an PlcService
 * - Überwacht den Fortschritt
 * - Aktualisiert den Auftragsstatus
 */
@Service
public class TransportOrderProcessor {

    private static final Logger log = LoggerFactory.getLogger(TransportOrderProcessor.class);

    private static final int MAX_RETRIES = 3;

    private final TransportOrderService orderService;
    private final StockyardService stockyardService;
    private final IngotService ingotService;
    private final PlcService plcService;
    private final DataBroadcaster dataBroadcaster;

    // Aktueller Auftrag in Bearbeitung
    private final AtomicReference<TransportOrderDTO> currentOrder = new AtomicReference<>();
    private final AtomicBoolean autoProcessingEnabled = new AtomicBoolean(false);
    private final AtomicBoolean processing = new AtomicBoolean(false);

    // Event-Listener
    private final List<Consumer<TransportOrderDTO>> orderStartedListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<TransportOrderDTO>> orderCompletedListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<TransportOrderDTO>> orderFailedListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<String>> statusChangeListeners = new CopyOnWriteArrayList<>();

    public TransportOrderProcessor(TransportOrderService orderService,
                                   StockyardService stockyardService,
                                   IngotService ingotService,
                                   PlcService plcService,
                                   DataBroadcaster dataBroadcaster) {
        this.orderService = orderService;
        this.stockyardService = stockyardService;
        this.ingotService = ingotService;
        this.plcService = plcService;
        this.dataBroadcaster = dataBroadcaster;

        // PlcService Status-Listener für Fortschritts-Tracking
        plcService.addStatusListener(this::onPlcStatusUpdate);
    }

    // ========================================================================
    // Öffentliche API
    // ========================================================================

    /**
     * Startet die automatische Auftragsverarbeitung
     */
    public void startAutoProcessing() {
        if (!plcService.isConnected() && !plcService.isSimulatorMode()) {
            log.warn("Kann Auto-Processing nicht starten: Kein Kran verfügbar");
            notifyStatusChange("Auto-Processing nicht möglich: Kein Kran verfügbar");
            return;
        }

        autoProcessingEnabled.set(true);
        log.info("Automatische Auftragsverarbeitung gestartet");
        notifyStatusChange("Auto-Processing gestartet");
    }

    /**
     * Stoppt die automatische Auftragsverarbeitung
     */
    public void stopAutoProcessing() {
        autoProcessingEnabled.set(false);
        log.info("Automatische Auftragsverarbeitung gestoppt");
        notifyStatusChange("Auto-Processing gestoppt");
    }

    /**
     * Prüft ob Auto-Processing aktiv ist
     */
    public boolean isAutoProcessingEnabled() {
        return autoProcessingEnabled.get();
    }

    /**
     * Verarbeitet einen einzelnen Auftrag manuell
     */
    public void processOrder(TransportOrderDTO order) {
        if (processing.get()) {
            log.warn("Bereits ein Auftrag in Bearbeitung");
            notifyStatusChange("Bereits ein Auftrag in Bearbeitung");
            return;
        }

        executeOrder(order);
    }

    /**
     * Bricht den aktuellen Auftrag ab
     */
    public void abortCurrentOrder() {
        TransportOrderDTO order = currentOrder.get();
        if (order == null) {
            log.debug("Kein Auftrag zum Abbrechen");
            return;
        }

        try {
            log.info("Breche Auftrag {} ab", order.getTransportNo());
            plcService.abort();

            orderService.updateStatus(order.getId(), OrderStatus.CANCELLED, "Manuell abgebrochen");
            currentOrder.set(null);
            processing.set(false);

            notifyStatusChange("Auftrag " + order.getTransportNo() + " abgebrochen");
            notifyOrderFailed(order);

        } catch (PlcException e) {
            log.error("Fehler beim Abbrechen", e);
        }
    }

    /**
     * Gibt den aktuellen Auftrag zurück
     */
    public Optional<TransportOrderDTO> getCurrentOrder() {
        return Optional.ofNullable(currentOrder.get());
    }

    /**
     * Prüft ob gerade ein Auftrag verarbeitet wird
     */
    public boolean isProcessing() {
        return processing.get();
    }

    /**
     * Gibt die Anzahl der wartenden Aufträge zurück
     */
    public int getPendingOrderCount() {
        return orderService.findPendingOrders().size();
    }

    // ========================================================================
    // Scheduled Processing
    // ========================================================================

    /**
     * Prüft regelmäßig auf neue Aufträge (alle 2 Sekunden)
     */
    @Scheduled(fixedDelay = 2000)
    public void checkForPendingOrders() {
        if (!autoProcessingEnabled.get()) {
            return;
        }

        if (processing.get()) {
            return; // Bereits ein Auftrag in Bearbeitung
        }

        if (!plcService.isConnected() && !plcService.isSimulatorMode()) {
            return; // Kein Kran verfügbar
        }

        // Prüfen ob Kran bereit ist
        PlcStatus status = plcService.getCurrentStatus();
        if (status != null && status.getJobState() != JobState.IDLE) {
            return; // Kran ist beschäftigt
        }

        // Nächsten Auftrag holen
        List<TransportOrderDTO> pending = orderService.findPendingOrders();
        if (pending.isEmpty()) {
            return;
        }

        TransportOrderDTO nextOrder = pending.get(0);
        log.info("Starte automatische Verarbeitung von Auftrag {}", nextOrder.getTransportNo());

        executeOrder(nextOrder);
    }

    // ========================================================================
    // Interne Verarbeitung
    // ========================================================================

    /**
     * Führt einen Auftrag aus
     */
    private void executeOrder(TransportOrderDTO order) {
        if (!processing.compareAndSet(false, true)) {
            log.warn("Auftrag bereits in Bearbeitung");
            return;
        }

        try {
            log.info("Starte Auftrag: {} ({} → {})",
                order.getTransportNo(), order.getFromYardNo(), order.getToYardNo());

            // Positionen laden
            Optional<StockyardDTO> fromYardOpt = stockyardService.findById(order.getFromYardId());
            Optional<StockyardDTO> toYardOpt = stockyardService.findById(order.getToYardId());

            if (fromYardOpt.isEmpty()) {
                failOrder(order, "Quell-Lagerplatz nicht gefunden: " + order.getFromYardId());
                return;
            }

            if (toYardOpt.isEmpty()) {
                failOrder(order, "Ziel-Lagerplatz nicht gefunden: " + order.getToYardId());
                return;
            }

            StockyardDTO fromYard = fromYardOpt.get();
            StockyardDTO toYard = toYardOpt.get();

            // Barren-Daten laden für korrekte Dimensionen und longIngot-Flag
            int ingotLength = 5000;  // Default
            int ingotWidth = 500;
            int ingotThickness = 200;
            int ingotWeight = 1500;
            boolean isLongIngot = false;

            if (order.getIngotId() != null) {
                Optional<IngotDTO> ingotOpt = ingotService.findById(order.getIngotId());
                if (ingotOpt.isPresent()) {
                    IngotDTO ingot = ingotOpt.get();
                    ingotLength = ingot.getLength() != null ? ingot.getLength() : 5000;
                    ingotWidth = ingot.getWidth() != null ? ingot.getWidth() : 500;
                    ingotThickness = ingot.getThickness() != null ? ingot.getThickness() : 200;
                    ingotWeight = ingot.getWeight() != null ? ingot.getWeight() : 1500;
                    // Langer Barren wenn > 6000mm
                    isLongIngot = ingotLength > 6000;
                    log.info("Barren geladen: {} - Länge={}mm, longIngot={}",
                        ingot.getIngotNo(), ingotLength, isLongIngot);
                }
            }

            // Ziel-Koordinaten berechnen
            int releaseX = toYard.getXPosition();
            int releaseY = toYard.getYPosition();
            int releaseZ = toYard.getZPosition();

            // Offset für lange Barren: 500mm nach links (höhere X-Werte)
            // Dies entspricht dem Verhalten der echten SPS
            if (isLongIngot) {
                releaseX += 500;
                log.info("LONG INGOT OFFSET: X-Position um 500mm angepasst ({} -> {})",
                    toYard.getXPosition(), releaseX);
            }

            // DEBUG: Koordinaten loggen
            log.info("=== KOORDINATEN DEBUG ===");
            log.info("FROM Yard: {} (ID={}) -> X={}, Y={}, Z={}",
                fromYard.getYardNumber(), fromYard.getId(),
                fromYard.getXPosition(), fromYard.getYPosition(), fromYard.getZPosition());
            log.info("TO Yard: {} (ID={}) -> X={}, Y={}, Z={} (DB-Wert)",
                toYard.getYardNumber(), toYard.getId(),
                toYard.getXPosition(), toYard.getYPosition(), toYard.getZPosition());
            log.info("Release Position: X={}, Y={}, Z={} (mit Offset)", releaseX, releaseY, releaseZ);
            log.info("Barren: Länge={}mm, isLongIngot={}", ingotLength, isLongIngot);
            log.info("=========================");

            // Kran-Befehl erstellen mit echten Barren-Daten
            PlcCommand cmd = PlcCommand.builder()
                .pickupPosition(fromYard.getXPosition(), fromYard.getYPosition(), fromYard.getZPosition())
                .releasePosition(releaseX, releaseY, releaseZ)
                .dimensions(ingotLength, ingotWidth, ingotThickness)
                .weight(ingotWeight)
                .longIngot(isLongIngot)
                .rotate(false)
                .build();

            // Status aktualisieren
            currentOrder.set(order);
            orderService.updateStatus(order.getId(), OrderStatus.IN_PROGRESS, null);
            order.setStatus(OrderStatus.IN_PROGRESS); // Lokales DTO auch aktualisieren
            notifyOrderStarted(order);
            notifyStatusChange("Auftrag " + order.getTransportNo() + " gestartet");

            // An Kran senden
            plcService.sendCommand(cmd);

            log.info("Kran-Befehl gesendet für Auftrag {}", order.getTransportNo());

        } catch (PlcException e) {
            log.error("PLC-Fehler bei Auftrag {}", order.getTransportNo(), e);
            failOrder(order, "PLC-Fehler: " + e.getMessage());

        } catch (Exception e) {
            log.error("Fehler bei Auftrag {}", order.getTransportNo(), e);
            failOrder(order, "Fehler: " + e.getMessage());
        }
    }

    /**
     * Wird bei PLC-Status-Updates aufgerufen
     */
    private void onPlcStatusUpdate(PlcStatus status) {
        TransportOrderDTO order = currentOrder.get();
        if (order == null) {
            return;
        }

        // Fortschritt tracken
        WorkPhase phase = status.getWorkPhase();
        JobState jobState = status.getJobState();

        log.debug("Auftrag {} - Phase: {}, JobState: {}", order.getTransportNo(), phase, jobState);

        // Bei LOADED: Barren aufgenommen
        if (jobState == JobState.LOADED && order.getStatus() == OrderStatus.IN_PROGRESS) {
            orderService.updateStatus(order.getId(), OrderStatus.PICKED_UP, null);
            order.setStatus(OrderStatus.PICKED_UP);
            notifyStatusChange("Auftrag " + order.getTransportNo() + ": Barren aufgenommen");
        }

        // Bei DROPPED oder IDLE nach LOADED: Auftrag abgeschlossen
        if ((jobState == JobState.DROPPED || (jobState == JobState.IDLE && phase == WorkPhase.IDLE))
            && (order.getStatus() == OrderStatus.PICKED_UP || order.getStatus() == OrderStatus.IN_PROGRESS)) {

            // Prüfen ob wirklich fertig (Phase IDLE)
            if (phase == WorkPhase.IDLE) {
                completeOrder(order);
            }
        }
    }

    /**
     * Schließt einen Auftrag erfolgreich ab
     */
    private void completeOrder(TransportOrderDTO order) {
        log.info("Auftrag {} erfolgreich abgeschlossen", order.getTransportNo());

        // Status aktualisieren
        orderService.updateStatus(order.getId(), OrderStatus.COMPLETED, null);

        // Barren in Datenbank umlagern
        if (order.getIngotId() != null && order.getToYardId() != null) {
            try {
                ingotService.relocate(order.getIngotId(), order.getToYardId());
                log.info("Barren {} nach {} umgelagert", order.getIngotNo(), order.getToYardNo());
            } catch (Exception e) {
                log.error("Fehler beim DB-Umlagern", e);
            }
        }

        // Aufräumen
        currentOrder.set(null);
        processing.set(false);

        notifyOrderCompleted(order);
        notifyStatusChange("Auftrag " + order.getTransportNo() + " abgeschlossen");

        // UI-Views benachrichtigen, dass sich Lagerplatz-Daten geändert haben
        if (dataBroadcaster != null) {
            dataBroadcaster.broadcast(DataEventType.STOCKYARD_CHANGED);
            log.info("STOCKYARD_CHANGED broadcast gesendet");
        }
    }

    /**
     * Markiert einen Auftrag als fehlgeschlagen
     */
    private void failOrder(TransportOrderDTO order, String errorMessage) {
        log.error("Auftrag {} fehlgeschlagen: {}", order.getTransportNo(), errorMessage);

        int retryCount = order.getRetryCount() != null ? order.getRetryCount() : 0;

        if (retryCount < MAX_RETRIES) {
            // Retry
            orderService.incrementRetryCount(order.getId());
            orderService.updateStatus(order.getId(), OrderStatus.PENDING, errorMessage);
            log.info("Auftrag {} wird erneut versucht (Versuch {}/{})",
                order.getTransportNo(), retryCount + 1, MAX_RETRIES);
            notifyStatusChange("Auftrag " + order.getTransportNo() + " - Retry " + (retryCount + 1));
        } else {
            // Endgültig fehlgeschlagen
            orderService.updateStatus(order.getId(), OrderStatus.FAILED, errorMessage);
            notifyOrderFailed(order);
            notifyStatusChange("Auftrag " + order.getTransportNo() + " endgültig fehlgeschlagen");
        }

        currentOrder.set(null);
        processing.set(false);
    }

    // ========================================================================
    // Event-Listener
    // ========================================================================

    public void addOrderStartedListener(Consumer<TransportOrderDTO> listener) {
        orderStartedListeners.add(listener);
    }

    public void addOrderCompletedListener(Consumer<TransportOrderDTO> listener) {
        orderCompletedListeners.add(listener);
    }

    public void addOrderFailedListener(Consumer<TransportOrderDTO> listener) {
        orderFailedListeners.add(listener);
    }

    public void addStatusChangeListener(Consumer<String> listener) {
        statusChangeListeners.add(listener);
    }

    private void notifyOrderStarted(TransportOrderDTO order) {
        orderStartedListeners.forEach(l -> {
            try { l.accept(order); } catch (Exception e) { log.error("Listener error", e); }
        });
    }

    private void notifyOrderCompleted(TransportOrderDTO order) {
        orderCompletedListeners.forEach(l -> {
            try { l.accept(order); } catch (Exception e) { log.error("Listener error", e); }
        });
    }

    private void notifyOrderFailed(TransportOrderDTO order) {
        orderFailedListeners.forEach(l -> {
            try { l.accept(order); } catch (Exception e) { log.error("Listener error", e); }
        });
    }

    private void notifyStatusChange(String message) {
        statusChangeListeners.forEach(l -> {
            try { l.accept(message); } catch (Exception e) { log.error("Listener error", e); }
        });
    }
}
