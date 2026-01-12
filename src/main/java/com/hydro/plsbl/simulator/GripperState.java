package com.hydro.plsbl.simulator;

/**
 * Greiferzustand
 */
public enum GripperState {
    OPEN("Offen"),
    CLOSED("Geschlossen"),
    LOADED("Beladen");

    private final String description;

    GripperState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
