package com.hydro.plsbl.simulator;

/**
 * Betriebsart des Krans
 */
public enum CraneMode {
    AUTOMATIC("Automatik"),
    MANUAL("Manuell"),
    SEMI_AUTOMATIC("Halbautomatik");

    private final String description;

    CraneMode(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
