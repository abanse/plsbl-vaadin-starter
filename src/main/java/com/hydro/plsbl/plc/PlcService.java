package com.hydro.plsbl.plc;

import com.hydro.plsbl.plc.dto.*;
import com.hydro.plsbl.service.SettingsService;
import com.hydro.plsbl.simulator.CraneSimulatorCommand;
import com.hydro.plsbl.simulator.CraneSimulatorService;
import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.PlcDriverManager;
import org.apache.plc4x.java.api.messages.PlcReadRequest;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.apache.plc4x.java.api.messages.PlcWriteRequest;
import org.apache.plc4x.java.api.messages.PlcWriteResponse;
import org.apache.plc4x.java.api.types.PlcResponseCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Service fuer die SPS-Kommunikation via Apache PLC4X
 *
 * Stellt Verbindung zur Siemens S7 SPS her und bietet:
 * - Zyklisches Lesen des Kran-Status
 * - Senden von Kran-Kommandos
 * - Verbindungsmanagement (connect, disconnect, reconnect)
 * - Event-basierte Status-Updates via Listener
 */
@Service
public class PlcService {

    private static final Logger log = LoggerFactory.getLogger(PlcService.class);

    // === PLC4X Adressen fuer Siemens S7 ===
    // Format: %DB<nummer>.DB<typ><offset>:<datentyp>
    // Beispiel: %DB100.DBW0:INT = Datenbaustein 100, Wort an Offset 0, als Integer

    // Status-Adressen (Lesen von SPS)
    private static final String ADDR_STATUS_X_POSITION = "%DB100.DBD0:DINT";      // X-Position [mm]
    private static final String ADDR_STATUS_Y_POSITION = "%DB100.DBD4:DINT";      // Y-Position [mm]
    private static final String ADDR_STATUS_Z_POSITION = "%DB100.DBD8:DINT";      // Z-Position [mm]
    private static final String ADDR_STATUS_CRANE_MODE = "%DB100.DBB12:SINT";     // Kran-Modus (0=Auto, 1=Manual, 2=Semi)
    private static final String ADDR_STATUS_GRIPPER = "%DB100.DBB13:SINT";        // Greifer (0=Open, 1=Closed, 2=Loaded)
    private static final String ADDR_STATUS_JOB_STATE = "%DB100.DBB14:SINT";      // Job-Status
    private static final String ADDR_STATUS_JOB_NUMBER = "%DB100.DBW16:INT";      // Job-Nummer
    private static final String ADDR_STATUS_FLAGS = "%DB100.DBW18:WORD";          // Status-Flags (Fehler, Tueren, etc.)
    private static final String ADDR_STATUS_DOORS = "%DB100.DBW20:WORD";          // Tuer-Status Bits

    // Kommando-Adressen (Schreiben zur SPS)
    private static final String ADDR_CMD_PICKUP_X = "%DB101.DBD0:DINT";           // Aufnahme X [mm]
    private static final String ADDR_CMD_PICKUP_Y = "%DB101.DBD4:DINT";           // Aufnahme Y [mm]
    private static final String ADDR_CMD_PICKUP_Z = "%DB101.DBD8:DINT";           // Aufnahme Z [mm]
    private static final String ADDR_CMD_RELEASE_X = "%DB101.DBD12:DINT";         // Ablage X [mm]
    private static final String ADDR_CMD_RELEASE_Y = "%DB101.DBD16:DINT";         // Ablage Y [mm]
    private static final String ADDR_CMD_RELEASE_Z = "%DB101.DBD20:DINT";         // Ablage Z [mm]
    private static final String ADDR_CMD_LENGTH = "%DB101.DBW24:INT";             // Laenge [mm]
    private static final String ADDR_CMD_WIDTH = "%DB101.DBW26:INT";              // Breite [mm]
    private static final String ADDR_CMD_THICKNESS = "%DB101.DBW28:INT";          // Dicke [mm]
    private static final String ADDR_CMD_WEIGHT = "%DB101.DBW30:INT";             // Gewicht [kg]
    private static final String ADDR_CMD_FLAGS = "%DB101.DBW32:WORD";             // Flags (abort, longIngot, rotate)
    private static final String ADDR_CMD_TRIGGER = "%DB101.DBX34.0:BOOL";         // Kommando-Trigger

    private final SettingsService settingsService;
    private final CraneSimulatorService simulatorService;
    private final AtomicReference<PlcConnection> connectionRef = new AtomicReference<>();
    private final AtomicReference<PlcStatus> currentStatus = new AtomicReference<>(new PlcStatus());
    private final AtomicBoolean connecting = new AtomicBoolean(false);
    private final List<Consumer<PlcStatus>> statusListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<PlcAlert>> alertListeners = new CopyOnWriteArrayList<>();

    private int reconnectAttempts = 0;
    private long lastSuccessfulRead = 0;
    private boolean useSimulator = false;

    public PlcService(SettingsService settingsService, CraneSimulatorService simulatorService) {
        this.settingsService = settingsService;
        this.simulatorService = simulatorService;
    }

    @PostConstruct
    public void init() {
        if (settingsService.isSpsEnabled()) {
            log.info("PlcService initialisiert - versuche SPS-Verbindung");
            connect();
            // Falls Verbindung fehlgeschlagen, Simulator als Fallback verwenden
            if (!isConnected() && simulatorService.isRunning()) {
                useSimulator = true;
                log.info("SPS-Verbindung fehlgeschlagen - verwende Simulator als Fallback");
            }
        } else {
            // SPS deaktiviert - Simulator verwenden wenn er läuft
            if (simulatorService.isRunning()) {
                useSimulator = true;
                log.info("SPS deaktiviert - verwende Simulator");
            } else {
                log.info("PlcService initialisiert - SPS und Simulator deaktiviert");
            }
        }
    }

    /**
     * Wird aufgerufen nachdem alle Beans initialisiert sind.
     * Prüft nochmal ob Simulator verfügbar ist (startet via ApplicationReadyEvent)
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        // Prüfen ob Simulator jetzt läuft und wir ihn verwenden sollten
        if (!useSimulator && !isConnected() && simulatorService.isRunning()) {
            useSimulator = true;
            log.info("ApplicationReady: Simulator erkannt - wechsle zu Simulator-Modus");
        }
        log.info("PlcService ready - Connected: {}, SimulatorMode: {}", isConnected(), isSimulatorMode());
    }

    @PreDestroy
    public void shutdown() {
        disconnect();
    }

    // === Verbindungsmanagement ===

    /**
     * Stellt Verbindung zur SPS her
     */
    public synchronized void connect() {
        if (!settingsService.isSpsEnabled()) {
            log.debug("SPS ist deaktiviert - keine Verbindung");
            return;
        }

        if (isConnected()) {
            log.debug("Bereits verbunden");
            return;
        }

        if (!connecting.compareAndSet(false, true)) {
            log.debug("Verbindungsaufbau laeuft bereits");
            return;
        }

        try {
            String url = settingsService.getSpsUrl();
            log.info("Verbinde zur SPS: {}", url);

            PlcConnection connection = PlcDriverManager.getDefault()
                .getConnectionManager()
                .getConnection(url);

            if (!connection.getMetadata().isReadSupported()) {
                log.error("SPS unterstuetzt kein Lesen!");
                connection.close();
                return;
            }

            if (!connection.getMetadata().isWriteSupported()) {
                log.warn("SPS unterstuetzt kein Schreiben - nur Lesemodus");
            }

            connectionRef.set(connection);
            reconnectAttempts = 0;
            log.info("SPS-Verbindung hergestellt: {}", url);

            notifyAlert(PlcAlert.linkRestored());

        } catch (Exception e) {
            log.error("Fehler beim Verbinden zur SPS: {}", e.getMessage());
            reconnectAttempts++;
            notifyAlert(PlcAlert.linkDown());
        } finally {
            connecting.set(false);
        }
    }

    /**
     * Trennt die Verbindung zur SPS
     */
    public synchronized void disconnect() {
        PlcConnection connection = connectionRef.getAndSet(null);
        if (connection != null) {
            try {
                connection.close();
                log.info("SPS-Verbindung getrennt");
            } catch (Exception e) {
                log.error("Fehler beim Trennen der SPS-Verbindung: {}", e.getMessage());
            }
        }
    }

    /**
     * Prueft ob eine Verbindung besteht
     */
    public boolean isConnected() {
        PlcConnection connection = connectionRef.get();
        return connection != null && connection.isConnected();
    }

    /**
     * Prueft ob SPS aktiviert ist
     */
    public boolean isEnabled() {
        return settingsService.isSpsEnabled();
    }

    // === Status lesen ===

    /**
     * Zyklisches Lesen des SPS-Status (alle 500ms)
     */
    @Scheduled(fixedDelayString = "${plsbl.plc.poll-interval:500}")
    public void pollStatus() {
        // Simulator-Modus: Status vom Simulator holen
        if (useSimulator && simulatorService.isRunning()) {
            PlcStatus status = readStatusFromSimulator();
            currentStatus.set(status);
            lastSuccessfulRead = System.currentTimeMillis();
            notifyStatusListeners(status);
            return;
        }

        // Automatisch auf Simulator wechseln wenn verfügbar und keine SPS-Verbindung
        if (!useSimulator && !isConnected() && simulatorService.isRunning()) {
            useSimulator = true;
            log.info("Simulator erkannt - wechsle zu Simulator-Modus");
            return; // Nächster Poll wird den Simulator nutzen
        }

        if (!settingsService.isSpsEnabled()) {
            return;
        }

        if (!isConnected()) {
            // Reconnect-Versuch mit Backoff
            if (reconnectAttempts < settingsService.getSpsRetryCount()) {
                connect();
            }
            // Falls Verbindung fehlgeschlagen, auf Simulator wechseln
            if (!isConnected() && simulatorService.isRunning()) {
                useSimulator = true;
                log.info("SPS nicht erreichbar - wechsle zu Simulator");
            }
            return;
        }

        try {
            PlcStatus status = readStatus();
            if (status != null) {
                currentStatus.set(status);
                lastSuccessfulRead = System.currentTimeMillis();
                notifyStatusListeners(status);
            }
        } catch (PlcException e) {
            log.error("Fehler beim Lesen des SPS-Status: {}", e.getMessage());
            if (e.isReconnectNecessary()) {
                disconnect();
                notifyAlert(PlcAlert.linkDown());
                // Auf Simulator wechseln
                if (simulatorService.isRunning()) {
                    useSimulator = true;
                    log.info("SPS-Fehler - wechsle zu Simulator");
                }
            }
        }
    }

    /**
     * Liest den Status vom Simulator
     */
    private PlcStatus readStatusFromSimulator() {
        var simStatus = simulatorService.getSimulatorStatus();
        PlcStatus status = new PlcStatus();
        status.incrementReceiveCounter();

        // Positionen vom Simulator
        status.setXPosition(simStatus.xPosition());
        status.setYPosition(simStatus.yPosition());
        status.setZPosition(simStatus.zPosition());

        // Modus-Mapping
        status.setCraneMode(switch (simStatus.craneMode()) {
            case AUTOMATIC -> CraneMode.AUTOMATIC;
            case MANUAL -> CraneMode.MANUAL;
            case SEMI_AUTOMATIC -> CraneMode.SEMI_AUTOMATIC;
        });

        // Greifer-Mapping
        status.setGripperState(switch (simStatus.gripperState()) {
            case OPEN -> GripperState.OPEN;
            case CLOSED -> GripperState.CLOSED;
            case LOADED -> GripperState.LOADED;
        });

        // Job-Mapping
        status.setJobState(switch (simStatus.jobState()) {
            case IDLE -> JobState.IDLE;
            case STARTED -> JobState.STARTED;
            case LOADED -> JobState.LOADED;
            case DROPPED -> JobState.DROPPED;
        });

        // WorkPhase-Mapping
        status.setWorkPhase(switch (simStatus.workPhase()) {
            case IDLE -> WorkPhase.IDLE;
            case MOVE_TO_PICKUP -> WorkPhase.MOVE_TO_PICKUP;
            case LOWERING_TO_PICKUP -> WorkPhase.LOWERING_TO_PICKUP;
            case GRABBING -> WorkPhase.GRABBING;
            case LIFTING_INGOT -> WorkPhase.LIFTING_INGOT;
            case MOVE_TO_DESTINATION -> WorkPhase.MOVE_TO_DESTINATION;
            case LOWERING_TO_DROP -> WorkPhase.LOWERING_TO_DROP;
            case RELEASE_INGOT -> WorkPhase.RELEASE_INGOT;
            case LIFTING_EMPTY -> WorkPhase.LIFTING_EMPTY;
        });

        // Simulator hat keine Fehler
        status.setLinkDown(false);
        status.setChecksumError(false);
        status.setPlcError(false);
        status.setCraneOff(false);

        return status;
    }

    /**
     * Liest den aktuellen Status von der SPS
     */
    public PlcStatus readStatus() throws PlcException {
        PlcConnection connection = connectionRef.get();
        if (connection == null || !connection.isConnected()) {
            throw new PlcException("Keine SPS-Verbindung", null, true);
        }

        try {
            PlcReadRequest.Builder builder = connection.readRequestBuilder();

            // Alle Status-Adressen hinzufuegen
            builder.addTagAddress("xPos", ADDR_STATUS_X_POSITION);
            builder.addTagAddress("yPos", ADDR_STATUS_Y_POSITION);
            builder.addTagAddress("zPos", ADDR_STATUS_Z_POSITION);
            builder.addTagAddress("craneMode", ADDR_STATUS_CRANE_MODE);
            builder.addTagAddress("gripper", ADDR_STATUS_GRIPPER);
            builder.addTagAddress("jobState", ADDR_STATUS_JOB_STATE);
            builder.addTagAddress("jobNumber", ADDR_STATUS_JOB_NUMBER);
            builder.addTagAddress("flags", ADDR_STATUS_FLAGS);
            builder.addTagAddress("doors", ADDR_STATUS_DOORS);

            PlcReadRequest request = builder.build();
            PlcReadResponse response = request.execute().get(
                settingsService.getSpsTimeout(), java.util.concurrent.TimeUnit.SECONDS);

            return parseStatusResponse(response);

        } catch (Exception e) {
            throw new PlcException("Fehler beim Lesen", e.getMessage(), true, e);
        }
    }

    /**
     * Gibt den zuletzt gelesenen Status zurueck (cached)
     */
    public PlcStatus getCurrentStatus() {
        return currentStatus.get().clone();
    }

    /**
     * Parst die SPS-Antwort in ein PlcStatus-Objekt
     */
    private PlcStatus parseStatusResponse(PlcReadResponse response) {
        PlcStatus status = new PlcStatus();
        status.incrementReceiveCounter();

        // Positionen
        if (response.getResponseCode("xPos") == PlcResponseCode.OK) {
            status.setXPosition(response.getInteger("xPos"));
        }
        if (response.getResponseCode("yPos") == PlcResponseCode.OK) {
            status.setYPosition(response.getInteger("yPos"));
        }
        if (response.getResponseCode("zPos") == PlcResponseCode.OK) {
            status.setZPosition(response.getInteger("zPos"));
        }

        // Kran-Modus
        if (response.getResponseCode("craneMode") == PlcResponseCode.OK) {
            int mode = response.getByte("craneMode");
            status.setCraneMode(switch (mode) {
                case 0 -> CraneMode.AUTOMATIC;
                case 1 -> CraneMode.MANUAL;
                case 2 -> CraneMode.SEMI_AUTOMATIC;
                default -> CraneMode.MANUAL;
            });
        }

        // Greifer-Status
        if (response.getResponseCode("gripper") == PlcResponseCode.OK) {
            int gripper = response.getByte("gripper");
            status.setGripperState(switch (gripper) {
                case 0 -> GripperState.OPEN;
                case 1 -> GripperState.CLOSED;
                case 2 -> GripperState.LOADED;
                default -> GripperState.OPEN;
            });
        }

        // Job-Status
        if (response.getResponseCode("jobState") == PlcResponseCode.OK) {
            int job = response.getByte("jobState");
            status.setJobState(switch (job) {
                case 0 -> JobState.IDLE;
                case 1 -> JobState.STARTED;
                case 2 -> JobState.LOADED;
                case 3 -> JobState.DROPPED;
                default -> JobState.IDLE;
            });
        }

        // Job-Nummer
        if (response.getResponseCode("jobNumber") == PlcResponseCode.OK) {
            status.setJobNumber(response.getInteger("jobNumber"));
        }

        // Status-Flags (Bits)
        if (response.getResponseCode("flags") == PlcResponseCode.OK) {
            int flags = response.getInteger("flags");
            status.setLinkDown((flags & 0x0001) != 0);
            status.setChecksumError((flags & 0x0002) != 0);
            status.setPlcError((flags & 0x0004) != 0);
            status.setCraneOff((flags & 0x0008) != 0);
        }

        // Tuer-Status (Bits)
        if (response.getResponseCode("doors") == PlcResponseCode.OK) {
            int doors = response.getInteger("doors");
            status.setDoor1Open((doors & 0x0001) != 0);
            status.setDoor7Open((doors & 0x0040) != 0);
            status.setDoor10Open((doors & 0x0200) != 0);
            status.setGatesOpen((doors & 0x00A0) != 0);  // Bits 5 & 7
            status.setDoorsOpen((doors & 0x011E) != 0);  // Bits 1-4, 8
        }

        return status;
    }

    // === Kommandos senden ===

    /**
     * Sendet ein Kommando an die SPS oder den Simulator
     */
    public void sendCommand(PlcCommand command) throws PlcException {
        // Simulator-Modus: Kommando an Simulator senden
        if (useSimulator && simulatorService.isRunning()) {
            sendCommandToSimulator(command);
            return;
        }

        if (!settingsService.isSpsEnabled()) {
            log.debug("SPS deaktiviert - Kommando nicht gesendet: {}", command);
            return;
        }

        PlcConnection connection = connectionRef.get();
        if (connection == null || !connection.isConnected()) {
            throw new PlcException("Keine SPS-Verbindung", null, true);
        }

        if (!connection.getMetadata().isWriteSupported()) {
            throw new PlcException("SPS unterstuetzt kein Schreiben");
        }

        try {
            log.info("Sende Kommando an SPS: {}", command);

            PlcWriteRequest.Builder builder = connection.writeRequestBuilder();

            // Positionen
            builder.addTagAddress("pickupX", ADDR_CMD_PICKUP_X, command.getPickupPositionX());
            builder.addTagAddress("pickupY", ADDR_CMD_PICKUP_Y, command.getPickupPositionY());
            builder.addTagAddress("pickupZ", ADDR_CMD_PICKUP_Z, command.getPickupPositionZ());
            builder.addTagAddress("releaseX", ADDR_CMD_RELEASE_X, command.getReleasePositionX());
            builder.addTagAddress("releaseY", ADDR_CMD_RELEASE_Y, command.getReleasePositionY());
            builder.addTagAddress("releaseZ", ADDR_CMD_RELEASE_Z, command.getReleasePositionZ());

            // Dimensionen
            builder.addTagAddress("length", ADDR_CMD_LENGTH, command.getLength());
            builder.addTagAddress("width", ADDR_CMD_WIDTH, command.getWidth());
            builder.addTagAddress("thickness", ADDR_CMD_THICKNESS, command.getThickness());
            builder.addTagAddress("weight", ADDR_CMD_WEIGHT, command.getWeight());

            // Flags: Bit 0 = abort, Bit 1 = longIngot, Bit 2 = rotate
            int flags = 0;
            if (command.isAbort()) flags |= 0x0001;
            if (command.isLongIngot()) flags |= 0x0002;
            if (command.isRotate()) flags |= 0x0004;
            builder.addTagAddress("flags", ADDR_CMD_FLAGS, flags);

            // Trigger (setzt das Kommando aktiv)
            builder.addTagAddress("trigger", ADDR_CMD_TRIGGER, true);

            PlcWriteRequest request = builder.build();
            PlcWriteResponse response = request.execute().get(
                settingsService.getSpsTimeout(), java.util.concurrent.TimeUnit.SECONDS);

            // Antwort pruefen
            for (String tagName : response.getTagNames()) {
                if (response.getResponseCode(tagName) != PlcResponseCode.OK) {
                    throw new PlcException("Schreibfehler bei " + tagName + ": " +
                        response.getResponseCode(tagName));
                }
            }

            log.info("Kommando erfolgreich gesendet");

        } catch (PlcException e) {
            throw e;
        } catch (Exception e) {
            throw new PlcException("Fehler beim Senden", e.getMessage(), false, e);
        }
    }

    /**
     * Sendet ein Abbruch-Kommando
     */
    public void abort() throws PlcException {
        sendCommand(PlcCommand.abort());
    }

    /**
     * Sendet ein Kommando an den Simulator
     */
    private void sendCommandToSimulator(PlcCommand command) {
        log.info("Sende Kommando an Simulator: {}", command);

        CraneSimulatorCommand.Builder builder = CraneSimulatorCommand.builder();

        if (command.isAbort()) {
            builder.abort();
        } else {
            builder.pickup(
                    command.getPickupPositionX(),
                    command.getPickupPositionY(),
                    command.getPickupPositionZ()
                )
                .release(
                    command.getReleasePositionX(),
                    command.getReleasePositionY(),
                    command.getReleasePositionZ()
                )
                .ingot(
                    command.getLength(),
                    command.getWidth(),
                    command.getThickness(),
                    command.getWeight()
                )
                .longIngot(command.isLongIngot())
                .rotate(command.isRotate());
        }

        simulatorService.sendCommand(builder.build());
        log.info("Kommando an Simulator gesendet");
    }

    // === Listener-Management ===

    /**
     * Registriert einen Status-Listener
     */
    public void addStatusListener(Consumer<PlcStatus> listener) {
        statusListeners.add(listener);
    }

    /**
     * Entfernt einen Status-Listener
     */
    public void removeStatusListener(Consumer<PlcStatus> listener) {
        statusListeners.remove(listener);
    }

    /**
     * Registriert einen Alert-Listener
     */
    public void addAlertListener(Consumer<PlcAlert> listener) {
        alertListeners.add(listener);
    }

    /**
     * Entfernt einen Alert-Listener
     */
    public void removeAlertListener(Consumer<PlcAlert> listener) {
        alertListeners.remove(listener);
    }

    private void notifyStatusListeners(PlcStatus status) {
        for (Consumer<PlcStatus> listener : statusListeners) {
            try {
                listener.accept(status);
            } catch (Exception e) {
                log.error("Fehler im Status-Listener: {}", e.getMessage());
            }
        }
    }

    private void notifyAlert(PlcAlert alert) {
        log.info("PLC Alert: {}", alert);
        for (Consumer<PlcAlert> listener : alertListeners) {
            try {
                listener.accept(alert);
            } catch (Exception e) {
                log.error("Fehler im Alert-Listener: {}", e.getMessage());
            }
        }
    }

    // === Status-Informationen ===

    /**
     * Gibt Verbindungsinformationen zurueck
     */
    public String getConnectionInfo() {
        if (useSimulator && simulatorService.isRunning()) {
            return "SIMULATOR-MODUS aktiv";
        }
        if (!settingsService.isSpsEnabled()) {
            return "SPS deaktiviert";
        }
        if (isConnected()) {
            return "Verbunden mit " + settingsService.getSpsUrl();
        }
        return "Nicht verbunden (Versuche: " + reconnectAttempts + ")";
    }

    /**
     * Prueft ob der Simulator-Modus aktiv ist
     */
    public boolean isSimulatorMode() {
        return useSimulator && simulatorService.isRunning();
    }

    /**
     * Wechselt in den Simulator-Modus
     */
    public void enableSimulatorMode() {
        if (simulatorService.isRunning()) {
            useSimulator = true;
            log.info("Simulator-Modus aktiviert");
        } else {
            log.warn("Simulator laeuft nicht - kann nicht wechseln");
        }
    }

    /**
     * Wechselt in den SPS-Modus (versucht Verbindung)
     */
    public void disableSimulatorMode() {
        useSimulator = false;
        log.info("Simulator-Modus deaktiviert - versuche SPS-Verbindung");
        connect();
    }

    /**
     * Zeit seit letztem erfolgreichen Lesen [ms]
     */
    public long getTimeSinceLastRead() {
        if (lastSuccessfulRead == 0) return -1;
        return System.currentTimeMillis() - lastSuccessfulRead;
    }

    /**
     * Anzahl der Reconnect-Versuche
     */
    public int getReconnectAttempts() {
        return reconnectAttempts;
    }

    /**
     * Testet die SPS-Verbindung
     */
    public boolean testConnection() {
        if (!settingsService.isSpsEnabled()) {
            return false;
        }

        try {
            if (!isConnected()) {
                connect();
            }
            if (isConnected()) {
                readStatus();
                return true;
            }
        } catch (Exception e) {
            log.error("Verbindungstest fehlgeschlagen: {}", e.getMessage());
        }
        return false;
    }
}
