package com.hydro.plsbl.simulator;

/**
 * Arbeitsphasen des Kran-Simulators
 */
public enum WorkPhase {
    IDLE("Leerlauf"),
    MOVE_TO_PICKUP("Fahrt zur Abholposition"),
    LOWERING_TO_PICKUP("Absenken zur Abholung"),
    GRABBING("Greifer schließen"),
    LIFTING_INGOT("Barren anheben"),
    MOVE_TO_DESTINATION("Fahrt zur Ablageposition"),
    LOWERING_TO_DROP("Absenken zur Ablage"),
    RELEASE_INGOT("Barren ablegen"),
    LIFTING_EMPTY("Greifer anheben");

    private final String description;

    WorkPhase(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
