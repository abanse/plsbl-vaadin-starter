package com.hydro.plsbl.simulator;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Konfiguration für den Kran-Simulator
 */
@Configuration
@ConfigurationProperties(prefix = "plsbl.simulator")
public class CraneSimulatorConfig {

    /**
     * Simulator aktivieren
     */
    private boolean enabled = true;

    /**
     * Simulationsintervall in Millisekunden (kleiner = flüssiger)
     */
    private int intervalMs = 100;

    /**
     * Bewegungsschrittweite X in mm pro Intervall
     */
    private int deltaX = 200;

    /**
     * Bewegungsschrittweite Y in mm pro Intervall
     */
    private int deltaY = 200;

    /**
     * Bewegungsschrittweite Z in mm pro Intervall
     */
    private int deltaZ = 80;

    /**
     * Standard-Höhe (Z-Position) in mm
     */
    private int defaultZ = 5000;

    /**
     * Parkposition X in mm
     */
    private int parkX = 3000;

    /**
     * Parkposition Y in mm
     */
    private int parkY = 3000;

    /**
     * Automatisch starten
     */
    private boolean autoStart = true;

    // === Getters & Setters ===

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getIntervalMs() {
        return intervalMs;
    }

    public void setIntervalMs(int intervalMs) {
        this.intervalMs = intervalMs;
    }

    public int getDeltaX() {
        return deltaX;
    }

    public void setDeltaX(int deltaX) {
        this.deltaX = deltaX;
    }

    public int getDeltaY() {
        return deltaY;
    }

    public void setDeltaY(int deltaY) {
        this.deltaY = deltaY;
    }

    public int getDeltaZ() {
        return deltaZ;
    }

    public void setDeltaZ(int deltaZ) {
        this.deltaZ = deltaZ;
    }

    public int getDefaultZ() {
        return defaultZ;
    }

    public void setDefaultZ(int defaultZ) {
        this.defaultZ = defaultZ;
    }

    public int getParkX() {
        return parkX;
    }

    public void setParkX(int parkX) {
        this.parkX = parkX;
    }

    public int getParkY() {
        return parkY;
    }

    public void setParkY(int parkY) {
        this.parkY = parkY;
    }

    public boolean isAutoStart() {
        return autoStart;
    }

    public void setAutoStart(boolean autoStart) {
        this.autoStart = autoStart;
    }
}
