package com.hydro.plsbl.security;

/**
 * Zugriffsmodus für Arbeitsplätze
 */
public enum AccessMode {
    
    /**
     * Vollzugriff - kann Kran steuern, Aufträge anlegen, etc.
     */
    CONTROL("Steuerung", true),
    
    /**
     * Nur Ansicht - kann nur beobachten, keine Aktionen
     */
    VIEW("Ansicht", false),
    
    /**
     * Eingeschränkt - kann einige Aktionen, aber nicht Kran steuern
     */
    LIMITED("Eingeschränkt", false);
    
    private final String displayName;
    private final boolean canControlCrane;
    
    AccessMode(String displayName, boolean canControlCrane) {
        this.displayName = displayName;
        this.canControlCrane = canControlCrane;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public boolean canControlCrane() {
        return canControlCrane;
    }
}
