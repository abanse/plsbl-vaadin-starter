package com.hydro.plsbl.simulator;

/**
 * Auftragsstatus des Krans
 */
public enum JobState {
    IDLE("Leerlauf"),
    STARTED("Gestartet"),
    LOADED("Barren geladen"),
    DROPPED("Barren abgelegt");

    private final String description;

    JobState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
