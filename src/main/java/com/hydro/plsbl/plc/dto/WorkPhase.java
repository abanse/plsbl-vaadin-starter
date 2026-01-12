package com.hydro.plsbl.plc.dto;

/**
 * Arbeitsphase des Krans (fuer Simulation und Status-Anzeige)
 */
public enum WorkPhase {

    IDLE("Ruheposition"),
    MOVE_TO_PICKUP("Fahrt zur Aufnahme"),
    LOWERING_TO_PICKUP("Absenken zur Aufnahme"),
    GRABBING("Greifen"),
    LIFTING_INGOT("Anheben mit Barren"),
    MOVE_TO_DESTINATION("Fahrt zum Ziel"),
    LOWERING_TO_DROP("Absenken zum Ablegen"),
    RELEASE_INGOT("Ablegen"),
    LIFTING_EMPTY("Anheben leer");

    private final String displayName;

    WorkPhase(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isMoving() {
        return this == MOVE_TO_PICKUP || this == MOVE_TO_DESTINATION;
    }

    public boolean isLifting() {
        return this == LIFTING_INGOT || this == LIFTING_EMPTY;
    }

    public boolean isLowering() {
        return this == LOWERING_TO_PICKUP || this == LOWERING_TO_DROP;
    }

    public boolean isGripping() {
        return this == GRABBING || this == RELEASE_INGOT;
    }

    public boolean hasIngot() {
        return this == LIFTING_INGOT || this == MOVE_TO_DESTINATION ||
               this == LOWERING_TO_DROP || this == RELEASE_INGOT;
    }
}
