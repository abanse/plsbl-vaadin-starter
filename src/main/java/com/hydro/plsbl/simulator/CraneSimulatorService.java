package com.hydro.plsbl.simulator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Kran-Simulator Service
 *
 * Simuliert die Kran-Bewegungen. Der Status wird intern gehalten
 * und kann über getSimulatorStatus() abgefragt werden.
 */
@Service
public class CraneSimulatorService {

    private static final Logger log = LoggerFactory.getLogger(CraneSimulatorService.class);

    private final CraneSimulatorConfig config;

    // Simulator State
    private volatile boolean running = false;
    private ScheduledExecutorService executor;
    private ScheduledFuture<?> simulatorTask;

    // Aktuelle Position
    private int xPosition;
    private int yPosition;
    private int zPosition;

    // Aktueller Auftrag
    private CraneSimulatorCommand currentCommand;
    private WorkPhase workPhase = WorkPhase.IDLE;
    private JobState jobState = JobState.IDLE;
    private GripperState gripperState = GripperState.OPEN;
    private CraneMode craneMode = CraneMode.AUTOMATIC;

    private int simCount = 0;
    private int jobNumber = 0;

    public CraneSimulatorService(CraneSimulatorConfig config) {
        this.config = config;
        reset();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Application ready - Simulator enabled={}, autoStart={}", config.isEnabled(), config.isAutoStart());
        if (config.isEnabled() && config.isAutoStart()) {
            start();
            log.info("Simulator auto-started, running={}", running);
        }
    }

    @PreDestroy
    public void onShutdown() {
        stop();
    }

    /**
     * Setzt den Simulator zurück
     */
    public void reset() {
        xPosition = config.getParkX();
        yPosition = config.getParkY();
        zPosition = config.getDefaultZ();
        workPhase = WorkPhase.IDLE;
        jobState = JobState.IDLE;
        gripperState = GripperState.OPEN;
        currentCommand = null;
        simCount = 0;
        updateDatabase();
        log.info("Simulator reset to park position ({}, {}, {})", xPosition, yPosition, zPosition);
    }

    /**
     * Startet den Simulator
     */
    public synchronized void start() {
        if (running) {
            log.warn("Simulator already running");
            return;
        }
        if (!config.isEnabled()) {
            log.warn("Simulator is disabled");
            return;
        }

        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "CraneSimulator");
            t.setDaemon(true);
            return t;
        });

        simulatorTask = executor.scheduleAtFixedRate(
                this::tick,
                config.getIntervalMs(),
                config.getIntervalMs(),
                TimeUnit.MILLISECONDS
        );

        running = true;
        log.info("Crane simulator started with {}ms interval", config.getIntervalMs());
    }

    /**
     * Stoppt den Simulator
     */
    public synchronized void stop() {
        if (!running) {
            return;
        }

        running = false;

        if (simulatorTask != null) {
            simulatorTask.cancel(false);
            simulatorTask = null;
        }

        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }

        log.info("Crane simulator stopped");
    }

    /**
     * Prüft ob der Simulator läuft
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Sendet einen Befehl an den Simulator
     */
    public synchronized void sendCommand(CraneSimulatorCommand command) {
        log.info("sendCommand called, running={}, jobState={}", running, jobState);

        if (!running) {
            log.warn("Cannot send command - simulator is not running!");
            return;
        }

        if (command.isAbort()) {
            log.info("Aborting current command");
            currentCommand = null;
            workPhase = WorkPhase.IDLE;
            jobState = JobState.IDLE;
            return;
        }

        if (jobState != JobState.IDLE) {
            log.warn("Cannot send command - crane is busy (jobState={})", jobState);
            return;
        }

        currentCommand = command;
        jobState = JobState.STARTED;
        workPhase = WorkPhase.IDLE;
        jobNumber++;
        simCount = 0;

        // Stockyards in DB speichern
        updateStockyardIds(command.getFromStockyardId(), command.getToStockyardId());

        log.info("Command accepted: {} - starting from ({},{},{})", command, xPosition, yPosition, zPosition);
    }

    /**
     * Gibt die aktuelle Arbeitsphase zurück
     */
    public WorkPhase getWorkPhase() {
        return workPhase;
    }

    /**
     * Gibt den aktuellen Auftragsstatus zurück
     */
    public JobState getJobState() {
        return jobState;
    }

    /**
     * Gibt den Greiferzustand zurück
     */
    public GripperState getGripperState() {
        return gripperState;
    }

    /**
     * Gibt die aktuelle Position zurück
     */
    public int[] getPosition() {
        return new int[]{xPosition, yPosition, zPosition};
    }

    // === Private Methods ===

    /**
     * Ein Simulationsschritt
     */
    private void tick() {
        try {
            nextState();
            updateDatabase();
            // Debug: Logge Position alle 10 Ticks wenn Auftrag läuft
            if (jobState != JobState.IDLE && simCount % 5 == 0) {
                log.debug("Tick: pos=({},{},{}), phase={}, job={}, gripper={}",
                    xPosition, yPosition, zPosition, workPhase, jobState, gripperState);
            }
        } catch (Exception e) {
            log.error("Simulator tick error", e);
        }
    }

    /**
     * Berechnet den nächsten Zustand
     */
    private synchronized void nextState() {
        if (!running) return;

        if (jobState == JobState.IDLE) {
            // Kein Auftrag - nichts zu tun
            return;
        }

        switch (workPhase) {
            case IDLE:
                // Zum Abholen fahren (erst auf Höhe gehen)
                if (moveZ(config.getDefaultZ())) {
                    workPhase = WorkPhase.MOVE_TO_PICKUP;
                    simCount = 0;
                }
                break;

            case MOVE_TO_PICKUP:
                simCount++;
                if (moveXY(currentCommand.getPickupX(), currentCommand.getPickupY()) && simCount > 1) {
                    workPhase = WorkPhase.LOWERING_TO_PICKUP;
                    simCount = 0;
                }
                break;

            case LOWERING_TO_PICKUP:
                simCount++;
                if (moveZ(currentCommand.getPickupZ()) && simCount > 1) {
                    workPhase = WorkPhase.GRABBING;
                    simCount = 0;
                }
                break;

            case GRABBING:
                gripperState = GripperState.LOADED;
                simCount++;
                if (simCount > 2) {
                    workPhase = WorkPhase.LIFTING_INGOT;
                    simCount = 0;
                }
                break;

            case LIFTING_INGOT:
                jobState = JobState.LOADED;
                simCount++;
                if (moveZ(config.getDefaultZ()) && simCount > 1) {
                    workPhase = WorkPhase.MOVE_TO_DESTINATION;
                    simCount = 0;
                }
                break;

            case MOVE_TO_DESTINATION:
                simCount++;
                if (moveXY(currentCommand.getReleaseX(), currentCommand.getReleaseY()) && simCount > 1) {
                    workPhase = WorkPhase.LOWERING_TO_DROP;
                    simCount = 0;
                }
                break;

            case LOWERING_TO_DROP:
                simCount++;
                if (moveZ(currentCommand.getReleaseZ()) && simCount > 1) {
                    workPhase = WorkPhase.RELEASE_INGOT;
                    simCount = 0;
                }
                break;

            case RELEASE_INGOT:
                gripperState = GripperState.CLOSED;
                jobState = JobState.DROPPED;
                simCount++;
                if (simCount > 2) {
                    workPhase = WorkPhase.LIFTING_EMPTY;
                    simCount = 0;
                }
                break;

            case LIFTING_EMPTY:
                gripperState = GripperState.OPEN;
                if (moveZ(config.getDefaultZ())) {
                    simCount++;
                }
                // Zur Parkposition fahren und fertig
                if (simCount > 1 && moveXY(
                        Math.max(xPosition, config.getParkX()),
                        Math.max(yPosition, config.getParkY()))) {
                    workPhase = WorkPhase.IDLE;
                    jobState = JobState.IDLE;
                    currentCommand = null;
                    simCount = 0;
                    log.info("Job completed, crane at ({}, {}, {})", xPosition, yPosition, zPosition);
                }
                break;
        }
    }

    /**
     * Bewegt den Kran horizontal
     */
    private boolean moveXY(int goalX, int goalY) {
        boolean reachedX = moveAxis('X', goalX);
        boolean reachedY = moveAxis('Y', goalY);
        return reachedX && reachedY;
    }

    private boolean moveAxis(char axis, int goal) {
        int current = axis == 'X' ? xPosition : yPosition;
        int delta = axis == 'X' ? config.getDeltaX() : config.getDeltaY();

        if (current < goal) {
            current = Math.min(current + delta, goal);
        } else if (current > goal) {
            current = Math.max(current - delta, goal);
        }

        if (axis == 'X') {
            xPosition = current;
        } else {
            yPosition = current;
        }

        return current == goal;
    }

    /**
     * Bewegt den Kran vertikal
     */
    private boolean moveZ(int goalZ) {
        if (zPosition < goalZ) {
            zPosition = Math.min(zPosition + config.getDeltaZ(), goalZ);
        } else if (zPosition > goalZ) {
            zPosition = Math.max(zPosition - config.getDeltaZ(), goalZ);
        }
        return zPosition == goalZ;
    }

    /**
     * Der Simulator aktualisiert keine Datenbank mehr.
     * Der Status wird nur intern gehalten und kann über getStatus() abgefragt werden.
     */
    private void updateDatabase() {
        // Keine Datenbank-Aktualisierung - Status wird intern gehalten
        // und kann über getSimulatorStatus() abgefragt werden
    }

    /**
     * Speichert die Stockyard-IDs (nur intern)
     */
    private void updateStockyardIds(Long fromId, Long toId) {
        // Nur intern gespeichert - keine DB-Aktualisierung
        this.fromStockyardId = fromId;
        this.toStockyardId = toId;
    }

    // === Simulator Status für UI ===

    private Long fromStockyardId;
    private Long toStockyardId;

    /**
     * Gibt den aktuellen Simulator-Status zurück (für UI-Anzeige)
     */
    public synchronized SimulatorStatus getSimulatorStatus() {
        return new SimulatorStatus(
            xPosition, yPosition, zPosition,
            craneMode, gripperState, jobState, workPhase,
            fromStockyardId, toStockyardId,
            running
        );
    }

    /**
     * Status-DTO für den Simulator
     */
    public record SimulatorStatus(
        int xPosition,
        int yPosition,
        int zPosition,
        CraneMode craneMode,
        GripperState gripperState,
        JobState jobState,
        WorkPhase workPhase,
        Long fromStockyardId,
        Long toStockyardId,
        boolean running
    ) {}
}
