package com.hydro.plsbl.service;

import com.hydro.plsbl.plc.PlcService;
import com.hydro.plsbl.plc.dto.PlcStatus;
import com.hydro.plsbl.simulator.CraneSimulatorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Service für System-Meldungen (Alarme, Warnungen, Info).
 *
 * Speichert alle Meldungen mit Historie und ermöglicht Quittierung.
 * Meldungen werden persistent gehalten und können gefiltert werden.
 * Überwacht auch Tür-/Tor-Status und erzeugt entsprechende Alarme.
 */
@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);
    private static final int MAX_HISTORY_SIZE = 1000;

    private final PlcService plcService;
    private final CraneSimulatorService simulatorService;

    // Alle Meldungen (aktive + Historie)
    private final List<SystemMessage> allMessages = new CopyOnWriteArrayList<>();

    // Aktive (nicht quittierte oder noch anliegende) Meldungen
    private final Map<String, SystemMessage> activeMessages = new ConcurrentHashMap<>();

    // ID-Generator
    private final AtomicLong idGenerator = new AtomicLong(1);

    // Listener für Meldungs-Änderungen
    private final List<Consumer<MessageEvent>> listeners = new CopyOnWriteArrayList<>();

    // Letzter bekannter Tür-Status für Änderungserkennung
    private boolean lastDoor1Open = false;
    private boolean lastDoor7Open = false;
    private boolean lastDoor10Open = false;
    private boolean lastGatesOpen = false;
    private boolean lastDoorsOpen = false;

    public MessageService(PlcService plcService, CraneSimulatorService simulatorService) {
        this.plcService = plcService;
        this.simulatorService = simulatorService;
    }

    /**
     * Erzeugt eine neue Meldung
     */
    public SystemMessage createMessage(MessageType type, MessageCategory category,
                                        String code, String text, String source) {
        SystemMessage msg = new SystemMessage(
            idGenerator.getAndIncrement(),
            type, category, code, text, source
        );

        // Prüfen ob gleiche Meldung bereits aktiv ist
        String key = category + "_" + code;
        if (activeMessages.containsKey(key)) {
            // Bereits aktiv - nur Counter erhöhen
            SystemMessage existing = activeMessages.get(key);
            existing.incrementCount();
            log.debug("Meldung bereits aktiv: {} (count={})", key, existing.getCount());
            notifyListeners(new MessageEvent(MessageEventType.UPDATED, existing));
            return existing;
        }

        // Neue Meldung hinzufügen
        allMessages.add(0, msg);  // Neueste zuerst
        activeMessages.put(key, msg);

        // Historie begrenzen
        while (allMessages.size() > MAX_HISTORY_SIZE) {
            allMessages.remove(allMessages.size() - 1);
        }

        log.info("MELDUNG: [{}] {} - {} ({})", type, category, text, code);
        notifyListeners(new MessageEvent(MessageEventType.CREATED, msg));

        return msg;
    }

    /**
     * Alarm erstellen
     */
    public SystemMessage alarm(MessageCategory category, String code, String text, String source) {
        return createMessage(MessageType.ALARM, category, code, text, source);
    }

    /**
     * Warnung erstellen
     */
    public SystemMessage warning(MessageCategory category, String code, String text, String source) {
        return createMessage(MessageType.WARNING, category, code, text, source);
    }

    /**
     * Info erstellen
     */
    public SystemMessage info(MessageCategory category, String code, String text, String source) {
        return createMessage(MessageType.INFO, category, code, text, source);
    }

    /**
     * Tür-Alarm erstellen
     */
    public SystemMessage doorAlarm(int doorNumber, String description) {
        return alarm(MessageCategory.DOOR, "DOOR_" + doorNumber, description + " ist offen", "SPS");
    }

    /**
     * Meldung quittieren
     */
    public boolean acknowledge(Long messageId) {
        for (SystemMessage msg : allMessages) {
            if (msg.getId().equals(messageId) && !msg.isAcknowledged()) {
                msg.setAcknowledged(true);
                msg.setAcknowledgedAt(LocalDateTime.now());
                log.info("Meldung quittiert: {} - {}", msg.getCode(), msg.getText());
                notifyListeners(new MessageEvent(MessageEventType.ACKNOWLEDGED, msg));

                // Wenn Bedingung behoben ist, aus aktiven entfernen
                if (msg.isConditionCleared()) {
                    String key = msg.getCategory() + "_" + msg.getCode();
                    activeMessages.remove(key);
                    notifyListeners(new MessageEvent(MessageEventType.CLEARED, msg));
                }

                // Bei Tür-Alarm prüfen ob Kran fortgesetzt werden kann
                if (msg.getCategory() == MessageCategory.DOOR) {
                    checkAndResumeCrane();
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Alle unquittierten Meldungen quittieren
     */
    public int acknowledgeAll() {
        int count = 0;
        boolean hadDoorAlarms = false;
        for (SystemMessage msg : allMessages) {
            if (!msg.isAcknowledged()) {
                msg.setAcknowledged(true);
                msg.setAcknowledgedAt(LocalDateTime.now());
                notifyListeners(new MessageEvent(MessageEventType.ACKNOWLEDGED, msg));

                if (msg.getCategory() == MessageCategory.DOOR) {
                    hadDoorAlarms = true;
                }

                if (msg.isConditionCleared()) {
                    String key = msg.getCategory() + "_" + msg.getCode();
                    activeMessages.remove(key);
                    notifyListeners(new MessageEvent(MessageEventType.CLEARED, msg));
                }
                count++;
            }
        }
        if (count > 0) {
            log.info("{} Meldungen quittiert", count);
        }
        // Nach Quittieren aller Meldungen prüfen ob Kran fortgesetzt werden kann
        if (hadDoorAlarms) {
            checkAndResumeCrane();
        }
        return count;
    }

    /**
     * Markiert eine Meldung als behoben (Bedingung nicht mehr aktiv)
     */
    public void clearCondition(String category, String code) {
        String key = category + "_" + code;
        SystemMessage msg = activeMessages.get(key);
        if (msg != null) {
            msg.setConditionCleared(true);
            msg.setClearedAt(LocalDateTime.now());
            log.info("Meldungs-Bedingung behoben: {} - {}", code, msg.getText());
            notifyListeners(new MessageEvent(MessageEventType.CONDITION_CLEARED, msg));

            // Wenn bereits quittiert, aus aktiven entfernen
            if (msg.isAcknowledged()) {
                activeMessages.remove(key);
                notifyListeners(new MessageEvent(MessageEventType.CLEARED, msg));
            }
        }
    }

    /**
     * Gibt alle Meldungen zurück (neueste zuerst)
     */
    public List<SystemMessage> getAllMessages() {
        return new ArrayList<>(allMessages);
    }

    /**
     * Gibt aktive Meldungen zurück
     */
    public List<SystemMessage> getActiveMessages() {
        return new ArrayList<>(activeMessages.values());
    }

    /**
     * Gibt unquittierte Meldungen zurück
     */
    public List<SystemMessage> getUnacknowledgedMessages() {
        return allMessages.stream()
            .filter(m -> !m.isAcknowledged())
            .toList();
    }

    /**
     * Gibt Alarme zurück
     */
    public List<SystemMessage> getAlarms() {
        return allMessages.stream()
            .filter(m -> m.getType() == MessageType.ALARM)
            .toList();
    }

    /**
     * Prüft ob unquittierte Alarme existieren
     */
    public boolean hasUnacknowledgedAlarms() {
        return allMessages.stream()
            .anyMatch(m -> m.getType() == MessageType.ALARM && !m.isAcknowledged());
    }

    /**
     * Prüft ob aktive Tür-Alarme existieren (für Kran-Sperre)
     */
    public boolean hasActiveDoorAlarms() {
        return activeMessages.values().stream()
            .anyMatch(m -> m.getCategory() == MessageCategory.DOOR && !m.isAcknowledged());
    }

    /**
     * Prüft ob Kran-Operationen erlaubt sind
     */
    public boolean isCraneOperationAllowed() {
        return !hasActiveDoorAlarms();
    }

    /**
     * Prüft ob aktive (unquittierte) Meldungen existieren
     */
    public boolean hasUnacknowledgedMessages() {
        return activeMessages.values().stream().anyMatch(m -> !m.isAcknowledged());
    }

    /**
     * Prüft ob aktive Meldungen existieren
     */
    public boolean hasActiveMessages() {
        return !activeMessages.isEmpty();
    }

    /**
     * Prüft den aktuellen Status und aktualisiert Tür-Alarme.
     * Sollte regelmäßig vom Polling aufgerufen werden.
     */
    public void checkStatus() {
        PlcStatus status = getCurrentStatus();
        if (status == null) return;

        // Tür-Status prüfen und Alarme erzeugen/löschen
        checkDoorStatus("DOOR_1", status.isDoor1Open(), lastDoor1Open, "Tor 1 (oben bei 17/10)");
        checkDoorStatus("DOOR_7", status.isDoor7Open(), lastDoor7Open, "Tor 7 (unten bei 17/01)");
        checkDoorStatus("DOOR_10", status.isDoor10Open(), lastDoor10Open, "Tor 10 (Fahrer-Tor)");
        checkDoorStatus("GATES_6_8", status.isGatesOpen(), lastGatesOpen, "Tore 6 & 8 (Einfahrt/Ausfahrt)");
        checkDoorStatus("DOORS_OTHER", status.isDoorsOpen(), lastDoorsOpen, "Tueren 2,3,4,5,9");

        // Status merken
        lastDoor1Open = status.isDoor1Open();
        lastDoor7Open = status.isDoor7Open();
        lastDoor10Open = status.isDoor10Open();
        lastGatesOpen = status.isGatesOpen();
        lastDoorsOpen = status.isDoorsOpen();
    }

    private void checkDoorStatus(String code, boolean isOpen, boolean wasOpen, String description) {
        if (isOpen && !wasOpen) {
            // Tür wurde geöffnet - Alarm erzeugen und Kran SOFORT stoppen!
            alarm(MessageCategory.DOOR, code, description + " ist offen", "SPS");
            pauseCrane();
        } else if (!isOpen && wasOpen) {
            // Tür wurde geschlossen - Bedingung behoben
            clearCondition(MessageCategory.DOOR.name(), code);
            // Kran wird erst fortgesetzt wenn ALLE Türen zu UND ALLE Alarme quittiert sind
            checkAndResumeCrane();
        }
    }

    /**
     * Pausiert den Kran (Sicherheitsstopp)
     */
    private void pauseCrane() {
        if (plcService.isSimulatorMode()) {
            simulatorService.pause();
            log.warn("SICHERHEITSSTOPP: Kran pausiert wegen offener Tür!");
        }
        // TODO: Bei echtem PLC entsprechenden Stopp-Befehl senden
    }

    /**
     * Prüft ob der Kran fortgesetzt werden kann und tut dies ggf.
     * Der Kran wird nur fortgesetzt wenn ALLE Tür-Alarme quittiert UND behoben sind.
     */
    private void checkAndResumeCrane() {
        // Prüfen ob noch aktive (nicht vollständig geklärte) Tür-Alarme existieren
        boolean anyActiveDoorAlarm = activeMessages.values().stream()
            .anyMatch(m -> m.getCategory() == MessageCategory.DOOR);

        if (!anyActiveDoorAlarm) {
            // Alle Tür-Alarme sind vollständig geklärt (quittiert UND behoben)
            if (plcService.isSimulatorMode()) {
                simulatorService.resume();
                log.info("Kran fortgesetzt - alle Türen geschlossen und Alarme quittiert");
            }
            // TODO: Bei echtem PLC entsprechenden Fortsetzen-Befehl senden
        }
    }

    private PlcStatus getCurrentStatus() {
        if (plcService.isSimulatorMode()) {
            // Im Simulator-Modus Status vom Simulator holen
            var simStatus = simulatorService.getSimulatorStatus();
            PlcStatus status = new PlcStatus();
            status.setDoor1Open(simStatus.door1Open());
            status.setDoor7Open(simStatus.door7Open());
            status.setDoor10Open(simStatus.door10Open());
            status.setGatesOpen(simStatus.gatesOpen());
            status.setDoorsOpen(simStatus.doorsOpen());
            return status;
        } else {
            return plcService.getCurrentStatus();
        }
    }

    /**
     * Listener registrieren
     */
    public void addListener(Consumer<MessageEvent> listener) {
        listeners.add(listener);
    }

    /**
     * Listener entfernen
     */
    public void removeListener(Consumer<MessageEvent> listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(MessageEvent event) {
        for (Consumer<MessageEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                log.error("Fehler beim Benachrichtigen des Message-Listeners: {}", e.getMessage());
            }
        }
    }

    // === Inner Classes ===

    public enum MessageType {
        ALARM("Alarm"),
        WARNING("Warnung"),
        INFO("Info");

        private final String displayName;

        MessageType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum MessageCategory {
        DOOR("Tuer/Tor"),
        SAFETY("Sicherheit"),
        CRANE("Kran"),
        SYSTEM("System"),
        COMMUNICATION("Kommunikation"),
        TRANSPORT("Transport"),
        STORAGE("Einlagerung"),
        LOADING("Beladung");

        private final String displayName;

        MessageCategory(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum MessageEventType {
        CREATED,
        UPDATED,
        ACKNOWLEDGED,
        CONDITION_CLEARED,
        CLEARED
    }

    public static class MessageEvent {
        private final MessageEventType type;
        private final SystemMessage message;

        public MessageEvent(MessageEventType type, SystemMessage message) {
            this.type = type;
            this.message = message;
        }

        public MessageEventType getType() { return type; }
        public SystemMessage getMessage() { return message; }
    }

    public static class SystemMessage {
        private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

        private final Long id;
        private final MessageType type;
        private final MessageCategory category;
        private final String code;
        private final String text;
        private final String source;
        private final LocalDateTime createdAt;
        private boolean acknowledged;
        private LocalDateTime acknowledgedAt;
        private boolean conditionCleared;
        private LocalDateTime clearedAt;
        private int count = 1;

        public SystemMessage(Long id, MessageType type, MessageCategory category,
                             String code, String text, String source) {
            this.id = id;
            this.type = type;
            this.category = category;
            this.code = code;
            this.text = text;
            this.source = source;
            this.createdAt = LocalDateTime.now();
        }

        public Long getId() { return id; }
        public MessageType getType() { return type; }
        public MessageCategory getCategory() { return category; }
        public String getCode() { return code; }
        public String getText() { return text; }
        public String getSource() { return source; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public String getCreatedAtFormatted() { return createdAt.format(TIME_FORMAT); }
        public boolean isAcknowledged() { return acknowledged; }
        public void setAcknowledged(boolean acknowledged) { this.acknowledged = acknowledged; }
        public LocalDateTime getAcknowledgedAt() { return acknowledgedAt; }
        public void setAcknowledgedAt(LocalDateTime acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }
        public String getAcknowledgedAtFormatted() { return acknowledgedAt != null ? acknowledgedAt.format(TIME_FORMAT) : ""; }
        public boolean isConditionCleared() { return conditionCleared; }
        public void setConditionCleared(boolean conditionCleared) { this.conditionCleared = conditionCleared; }
        public LocalDateTime getClearedAt() { return clearedAt; }
        public void setClearedAt(LocalDateTime clearedAt) { this.clearedAt = clearedAt; }
        public int getCount() { return count; }
        public void incrementCount() { this.count++; }

        public String getStatus() {
            if (conditionCleared && acknowledged) return "Behoben";
            if (acknowledged) return "Quittiert";
            return "Aktiv";
        }

        public boolean isActive() {
            return !conditionCleared || !acknowledged;
        }
    }
}
