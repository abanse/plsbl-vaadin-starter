package com.hydro.plsbl.service;

import com.hydro.plsbl.dto.CalloffDTO;
import com.hydro.plsbl.dto.IngotDTO;
import com.hydro.plsbl.dto.StockyardDTO;
import com.hydro.plsbl.entity.transdata.Shipment;
import com.hydro.plsbl.plc.PlcService;
import com.hydro.plsbl.plc.dto.JobState;
import com.hydro.plsbl.simulator.CraneSimulatorCommand;
import com.hydro.plsbl.simulator.CraneSimulatorService;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Hintergrund-Service für die Beladungs-Verarbeitung.
 *
 * Dieser Service läuft unabhängig von der BeladungView und stellt sicher,
 * dass der Beladungsprozess auch weiterläuft, wenn der Benutzer zu einer
 * anderen View wechselt. Session-Scoped damit er zur gleichen Session gehört
 * wie der BeladungStateService.
 */
@Service
@VaadinSessionScope
public class BeladungProcessorService {

    private static final Logger log = LoggerFactory.getLogger(BeladungProcessorService.class);

    // Trailer-Position (in mm) - muss mit BeladungView übereinstimmen
    private static final int TRAILER_X = 40000;
    private static final int TRAILER_Y = 2000;
    private static final int TRAILER_Z = 2000;

    private final BeladungStateService stateService;
    private final PlcService plcService;
    private final CraneSimulatorService simulatorService;
    private final StockyardService stockyardService;
    private final IngotService ingotService;
    private final BeladungBroadcaster broadcaster;
    private final DataBroadcaster dataBroadcaster;
    private final ShipmentService shipmentService;
    private final CalloffService calloffService;
    private final MessageService messageService;

    private ScheduledExecutorService executor;
    private ScheduledFuture<?> processorTask;
    private volatile boolean processing = false;

    // Tracking für aktuelle Beladung
    private IngotDTO currentIngot = null;
    private boolean commandSent = false;
    private boolean jobStarted = false;  // Wurde der Kran tatsächlich gestartet (nicht mehr IDLE)?
    private int waitTicksAfterCommand = 0;  // Warte-Ticks nach Kommando-Sendung

    // Calloff-Info für Lieferschein
    private CalloffDTO currentCalloff = null;
    private String currentDestination = null;

    public BeladungProcessorService(BeladungStateService stateService,
                                     PlcService plcService,
                                     CraneSimulatorService simulatorService,
                                     StockyardService stockyardService,
                                     IngotService ingotService,
                                     BeladungBroadcaster broadcaster,
                                     DataBroadcaster dataBroadcaster,
                                     ShipmentService shipmentService,
                                     CalloffService calloffService,
                                     MessageService messageService) {
        this.stateService = stateService;
        this.plcService = plcService;
        this.simulatorService = simulatorService;
        this.stockyardService = stockyardService;
        this.ingotService = ingotService;
        this.broadcaster = broadcaster;
        this.dataBroadcaster = dataBroadcaster;
        this.shipmentService = shipmentService;
        this.calloffService = calloffService;
        this.messageService = messageService;
    }

    @PreDestroy
    public void shutdown() {
        stop();
    }

    /**
     * Startet die Hintergrund-Verarbeitung
     */
    public synchronized void start() {
        if (processing) {
            log.info("BeladungProcessor bereits gestartet");
            return;
        }

        log.info("=== BELADUNG PROCESSOR START ===");

        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "BeladungProcessor");
            t.setDaemon(true);
            return t;
        });

        processorTask = executor.scheduleAtFixedRate(
            this::processBeladung,
            500, 500, TimeUnit.MILLISECONDS
        );

        processing = true;
        commandSent = false;
        jobStarted = false;
        waitTicksAfterCommand = 0;
        currentIngot = null;

        log.info("BeladungProcessor gestartet (500ms Intervall)");
    }

    /**
     * Stoppt die Hintergrund-Verarbeitung
     */
    public synchronized void stop() {
        if (!processing) {
            return;
        }

        log.info("=== BELADUNG PROCESSOR STOP ===");

        processing = false;

        if (processorTask != null) {
            processorTask.cancel(false);
            processorTask = null;
        }

        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }

        commandSent = false;
        currentIngot = null;

        log.info("BeladungProcessor gestoppt");
    }

    /**
     * Prüft ob der Processor läuft
     */
    public boolean isProcessing() {
        return processing;
    }

    /**
     * Setzt die Calloff-Info für den Lieferschein
     */
    public void setCalloffInfo(CalloffDTO calloff, String destination) {
        this.currentCalloff = calloff;
        this.currentDestination = destination;
        log.info("Calloff-Info gesetzt: calloff={}, destination={}",
            calloff != null ? calloff.getCalloffNumber() : "null", destination);
    }

    /**
     * Hauptverarbeitungsschleife - wird alle 500ms aufgerufen
     */
    private void processBeladung() {
        try {
            // Tür-Status prüfen und ggf. Alarme erzeugen
            messageService.checkStatus();

            // Prüfen ob Beladung noch läuft
            if (!stateService.isBeladungLaeuft()) {
                log.debug("Beladung nicht aktiv - überspringe");
                return;
            }

            // Prüfen ob Kran-Operationen erlaubt sind (Alarme quittiert?)
            if (!messageService.isCraneOperationAllowed()) {
                log.debug("Kran-Operation blockiert - unquittierte Alarme!");
                return;
            }

            // Prüfen ob noch Barren zu laden sind
            if (!stateService.hasGeplanteBarren() && !commandSent) {
                log.info("=== BELADUNG ABGESCHLOSSEN ===");
                log.info("  hasGeplanteBarren={}, commandSent={}", stateService.hasGeplanteBarren(), commandSent);
                log.info("  geladeneCount={}", stateService.getGeladeneCount());
                stateService.setBeladungLaeuft(false);

                // Lieferschein erstellen BEVOR wir stoppen
                log.info(">>> Starte erstelleLieferschein()...");
                Shipment shipment = erstelleLieferschein();
                log.info(">>> erstelleLieferschein() returned: {}", shipment != null ? "Shipment ID=" + shipment.getId() : "NULL");

                stop();

                // Broadcast mit Shipment-Info (damit alle Views benachrichtigt werden)
                if (shipment != null) {
                    log.info(">>> BROADCAST BELADUNG_ENDED mit Shipment: ID={}, Nr={}",
                        shipment.getId(), shipment.getShipmentNumber());
                    broadcaster.broadcastBeladungEndedWithShipment(
                        stateService.getGeladeneCount(),
                        shipment.getId(),
                        shipment.getShipmentNumber()
                    );
                    log.info(">>> BROADCAST GESENDET!");
                } else {
                    log.error("!!! KEIN SHIPMENT - Sende nur Status-Broadcast !!!");
                    broadcastStatus();
                }
                return;
            }

            // Kran-Status prüfen
            JobState jobState = getJobState();

            // IMMER loggen für Debugging
            log.info("PROCESSOR TICK: jobState={}, commandSent={}, jobStarted={}, waitTicks={}, geplant={}, geladen={}, ingot={}",
                jobState, commandSent, jobStarted, waitTicksAfterCommand,
                stateService.getGeplanteBarren().size(),
                stateService.getGeladeneBarren().size(),
                currentIngot != null ? currentIngot.getIngotNo() : "null");

            if (commandSent) {
                // Warte einige Ticks nach dem Senden des Kommandos
                // bevor wir den Status prüfen (Simulator braucht Zeit zum Starten)
                if (waitTicksAfterCommand > 0) {
                    waitTicksAfterCommand--;
                    log.debug("Warte auf Simulator-Start: {} Ticks verbleibend", waitTicksAfterCommand);
                    return;
                }

                // Prüfen ob Kran gestartet hat (nicht mehr IDLE)
                if (!jobStarted) {
                    if (jobState != JobState.IDLE) {
                        jobStarted = true;
                        log.info(">>> KRAN HAT GESTARTET: jobState={}", jobState);
                    } else {
                        // Kran ist immer noch IDLE - warten
                        log.debug("Warte auf Kran-Start...");
                        return;
                    }
                }

                // Kran hat gestartet - warten bis er wieder IDLE ist (= fertig)
                if (jobStarted && jobState == JobState.IDLE) {
                    // Auftrag abgeschlossen!
                    log.info(">>> KRAN HAT BARREN ABGELEGT: {}",
                        currentIngot != null ? currentIngot.getIngotNo() : "?");

                    // Barren als geladen markieren
                    if (currentIngot != null) {
                        completeIngotLoading(currentIngot);
                    }

                    // Reset für nächsten Barren
                    commandSent = false;
                    jobStarted = false;
                    currentIngot = null;
                    stateService.setKranKommandoGesendet(false);

                    // Status broadcasten
                    broadcastStatus();

                    // Nächsten Barren nach kurzer Pause starten
                    // (wird im nächsten Tick gemacht)
                }
            } else {
                // Kein Kommando gesendet - nächsten Barren starten
                log.info("PROCESSOR: Prüfe nächsten Barren... jobState={}, hasGeplante={}",
                    jobState, stateService.hasGeplanteBarren());

                if (jobState == JobState.IDLE && stateService.hasGeplanteBarren()) {
                    IngotDTO nextIngot = stateService.peekNextBarren();
                    if (nextIngot != null) {
                        log.info(">>> STARTE NÄCHSTEN BARREN: {} von Lagerplatz {}",
                            nextIngot.getIngotNo(), nextIngot.getStockyardNo());
                        sendLoadCommand(nextIngot);
                        // Warte 3 Ticks (1.5 Sekunden) bevor wir den Status prüfen
                        waitTicksAfterCommand = 3;
                    } else {
                        log.warn("PROCESSOR: peekNextBarren() returned null obwohl hasGeplanteBarren()=true!");
                    }
                } else {
                    log.info("PROCESSOR: Warte... jobState={} (erwartet IDLE), hasGeplante={}",
                        jobState, stateService.hasGeplanteBarren());
                }
            }

        } catch (Exception e) {
            log.error("Fehler in BeladungProcessor: {}", e.getMessage(), e);
        }
    }

    /**
     * Holt den aktuellen Job-Status vom Kran
     */
    private JobState getJobState() {
        if (plcService.isSimulatorMode()) {
            var status = simulatorService.getSimulatorStatus();
            return convertSimulatorJobState(status.jobState());
        } else {
            var plcStatus = plcService.getCurrentStatus();
            return plcStatus != null ? plcStatus.getJobState() : JobState.IDLE;
        }
    }

    private JobState convertSimulatorJobState(com.hydro.plsbl.simulator.JobState simState) {
        // WICHTIG: DROPPED darf NICHT als IDLE behandelt werden!
        // Der Simulator ist erst wirklich IDLE, wenn er nach DROPPING_EMPTY
        // wieder auf IDLE wechselt. Sonst wird das nächste Kommando abgelehnt.
        return switch (simState) {
            case IDLE -> JobState.IDLE;
            case STARTED -> JobState.STARTED;
            case LOADED -> JobState.LOADED;
            case DROPPED -> JobState.LOADED;  // Noch beschäftigt - nicht IDLE!
        };
    }

    /**
     * Sendet Ladebefehl für einen Barren
     */
    private void sendLoadCommand(IngotDTO ingot) {
        if (ingot == null || ingot.getStockyardId() == null) {
            log.warn("Kann Barren nicht laden - ungültige Daten");
            return;
        }

        // Quell-Lagerplatz holen
        StockyardDTO sourceYard = stockyardService.findById(ingot.getStockyardId()).orElse(null);
        if (sourceYard == null) {
            log.error("Quell-Lagerplatz nicht gefunden: {}", ingot.getStockyardId());
            return;
        }

        log.info("Sende Ladebefehl: {} von {} -> TRAILER",
            ingot.getIngotNo(), sourceYard.getYardNumber());
        log.info("  Stockyard Koordinaten: X={}, Y={}, Z={} (Grid: {}/{})",
            sourceYard.getXPosition(), sourceYard.getYPosition(), sourceYard.getZPosition(),
            sourceYard.getXCoordinate(), sourceYard.getYCoordinate());

        int pickupZ = sourceYard.getZPosition() > 0 ? sourceYard.getZPosition() : 2000;

        // WARNUNG wenn Koordinaten nicht gesetzt sind
        if (sourceYard.getXPosition() == 0 || sourceYard.getYPosition() == 0) {
            log.error("!!! WARNUNG: Stockyard {} hat keine Kran-Koordinaten (X={}, Y={}) !!!",
                sourceYard.getYardNumber(), sourceYard.getXPosition(), sourceYard.getYPosition());
            log.error("!!! Bitte BOTTOM_CENTER_X/Y in MD_STOCKYARD setzen !!!");
        }

        // Kommando an Simulator/SPS senden
        try {
            if (plcService.isSimulatorMode()) {
                // Prüfen ob Simulator läuft und bereit ist
                var statusBefore = simulatorService.getSimulatorStatus();
                log.info("Simulator Status VOR Kommando: running={}, jobState={}, workPhase={}",
                    statusBefore.running(), statusBefore.jobState(), statusBefore.workPhase());

                if (!statusBefore.running()) {
                    log.error("SIMULATOR IST NICHT GESTARTET! Starte Simulator...");
                    simulatorService.start();
                }

                if (statusBefore.jobState() != com.hydro.plsbl.simulator.JobState.IDLE) {
                    log.warn("Simulator ist noch beschäftigt (jobState={}), warte...", statusBefore.jobState());
                    return;  // Nicht senden - nächsten Tick abwarten
                }

                CraneSimulatorCommand cmd = CraneSimulatorCommand.builder()
                    .pickup(sourceYard.getXPosition(), sourceYard.getYPosition(), pickupZ)
                    .release(TRAILER_X, TRAILER_Y, TRAILER_Z)
                    .fromStockyard(sourceYard.getId())
                    .toStockyard(null)  // Trailer hat keine ID
                    .build();
                simulatorService.sendCommand(cmd);

                // Prüfen ob Kommando angenommen wurde
                var statusAfter = simulatorService.getSimulatorStatus();
                log.info("Simulator Status NACH Kommando: jobState={}, workPhase={}",
                    statusAfter.jobState(), statusAfter.workPhase());

                if (statusAfter.jobState() == com.hydro.plsbl.simulator.JobState.IDLE) {
                    log.error("KOMMANDO WURDE NICHT ANGENOMMEN! Simulator ist immer noch IDLE!");
                    return;  // Nicht als gesendet markieren
                }
            } else {
                // Echte SPS
                com.hydro.plsbl.plc.dto.PlcCommand plcCmd = com.hydro.plsbl.plc.dto.PlcCommand.builder()
                    .pickupPosition(sourceYard.getXPosition(), sourceYard.getYPosition(), pickupZ)
                    .releasePosition(TRAILER_X, TRAILER_Y, TRAILER_Z)
                    .build();
                plcService.sendCommand(plcCmd);
            }
        } catch (Exception e) {
            log.error("Fehler beim Senden des Ladebefehls: {}", e.getMessage());
            return;
        }

        // Status merken
        currentIngot = ingot;
        commandSent = true;
        stateService.setKranKommandoGesendet(true);

        log.info("Ladebefehl erfolgreich gesendet für: {}", ingot.getIngotNo());
    }

    /**
     * Markiert einen Barren als erfolgreich geladen
     */
    private void completeIngotLoading(IngotDTO ingot) {
        log.info("Barren geladen: {}", ingot.getIngotNo());

        // Aus geplant entfernen und zu geladen hinzufügen
        stateService.moveBarrenToGeladen();

        // Barren in DB aktualisieren (Lagerplatz auf null setzen = auf LKW)
        try {
            ingotService.relocate(ingot.getId(), null);
            log.info("Barren {} aus Lager entfernt (auf LKW)", ingot.getIngotNo());
        } catch (Exception e) {
            log.error("Fehler beim Aktualisieren des Barren-Standorts: {}", e.getMessage());
        }

        // Lagerplatz-Änderung broadcasten
        dataBroadcaster.broadcast(DataBroadcaster.DataEventType.STOCKYARD_CHANGED);
    }

    /**
     * Erstellt den Lieferschein (Shipment) nach Abschluss der Beladung
     */
    private Shipment erstelleLieferschein() {
        log.info(">>> erstelleLieferschein() START");

        List<IngotDTO> geladeneBarren = stateService.getGeladeneBarren();
        log.info(">>> geladeneBarren.size() = {}", geladeneBarren.size());

        if (geladeneBarren.isEmpty()) {
            log.error("!!! KEINE GELADENEN BARREN FÜR LIEFERSCHEIN - Liste ist leer !!!");
            log.error("!!! StateService.geladeneCount = {}", stateService.getGeladeneCount());
            return null;
        }

        // Debug: Alle geladenen Barren auflisten
        for (int i = 0; i < geladeneBarren.size(); i++) {
            IngotDTO b = geladeneBarren.get(i);
            log.info(">>> Geladener Barren [{}]: id={}, nr={}, gewicht={}",
                i, b.getId(), b.getIngotNo(), b.getWeight());
        }

        try {
            String orderNumber = currentCalloff != null ? currentCalloff.getOrderNumber() : null;
            String destination = currentCalloff != null ? currentCalloff.getDestination() : currentDestination;
            String customerNumber = currentCalloff != null ? currentCalloff.getCustomerNumber() : null;
            String customerAddress = currentCalloff != null ? currentCalloff.getCustomerAddress() : null;

            log.info("=== ERSTELLE LIEFERSCHEIN ===");
            log.info("  Barren: {}", geladeneBarren.size());
            log.info("  Auftrag: {}, Ziel: {}, Kunde: {}", orderNumber, destination, customerNumber);

            log.info(">>> Rufe shipmentService.createShipment() auf...");
            Shipment shipment = shipmentService.createShipment(
                orderNumber, destination, customerNumber, customerAddress,
                new ArrayList<>(geladeneBarren)
            );
            log.info(">>> shipmentService.createShipment() erfolgreich!");

            log.info("  Shipment erstellt: ID={}, Nr={}", shipment.getId(), shipment.getShipmentNumber());

            // Abruf aktualisieren falls vorhanden
            if (currentCalloff != null) {
                try {
                    calloffService.addDeliveredAmount(currentCalloff.getId(), geladeneBarren.size());
                    log.info("  Abruf {} aktualisiert: +{} geliefert", currentCalloff.getCalloffNumber(), geladeneBarren.size());
                } catch (Exception e) {
                    log.error("  Fehler beim Aktualisieren des Abrufs: {}", e.getMessage());
                }
            }

            log.info(">>> erstelleLieferschein() ERFOLGREICH - return shipment");
            return shipment;

        } catch (Exception e) {
            log.error("!!! FEHLER beim Erstellen des Lieferscheins !!!");
            log.error("!!! Exception-Typ: {}", e.getClass().getName());
            log.error("!!! Message: {}", e.getMessage());
            log.error("!!! Stack-Trace:", e);
            return null;
        }
    }

    /**
     * Sendet Status-Update an alle Listener
     */
    private void broadcastStatus() {
        int geladen = stateService.getGeladeneCount();
        int total = stateService.getTotalCount();
        boolean loading = stateService.isBeladungLaeuft();

        broadcaster.broadcastStatusUpdate(geladen, total, loading);

        log.info("Beladungs-Status: {}/{} geladen, läuft={}", geladen, total, loading);
    }
}
