package com.hydro.plsbl.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Konfiguration der Arbeitsplätze und deren Berechtigungen.
 * 
 * Wird aus application.properties geladen:
 * 
 * plsbl.workstations.crane-controllers=KRAN-PC-01,KRAN-PC-02
 * plsbl.workstations.view-only=BUERO-PC-01,BUERO-PC-02
 * plsbl.workstations.default-mode=VIEW
 */
@Configuration
@ConfigurationProperties(prefix = "plsbl.workstations")
public class WorkstationConfig {
    
    /**
     * Liste der Arbeitsplätze die den Kran steuern DÜRFEN (Whitelist)
     * Diese können den Steuerungs-Token anfordern.
     */
    private List<String> craneControllers = new ArrayList<>();
    
    /**
     * Liste der Arbeitsplätze die NUR Ansichtsmodus haben
     */
    private List<String> viewOnly = new ArrayList<>();
    
    /**
     * Standard-Modus für unbekannte Arbeitsplätze
     */
    private AccessMode defaultMode = AccessMode.VIEW;
    
    /**
     * Ob Token-basierte Steuerung aktiviert ist
     * (nur einer kann gleichzeitig steuern)
     */
    private boolean tokenBasedControl = true;
    
    /**
     * Timeout in Sekunden nach dem ein inaktiver Token automatisch freigegeben wird
     */
    private int tokenTimeoutSeconds = 300; // 5 Minuten
    
    /**
     * Individuelle Konfiguration pro Arbeitsplatz (optional)
     * Key = Hostname/IP, Value = Konfiguration
     */
    private Map<String, WorkstationSettings> stations = new HashMap<>();
    
    // === Getters & Setters ===
    
    public List<String> getCraneControllers() {
        return craneControllers;
    }
    
    public void setCraneControllers(List<String> craneControllers) {
        this.craneControllers = craneControllers;
    }
    
    public List<String> getViewOnly() {
        return viewOnly;
    }
    
    public void setViewOnly(List<String> viewOnly) {
        this.viewOnly = viewOnly;
    }
    
    public AccessMode getDefaultMode() {
        return defaultMode;
    }
    
    public void setDefaultMode(AccessMode defaultMode) {
        this.defaultMode = defaultMode;
    }
    
    public boolean isTokenBasedControl() {
        return tokenBasedControl;
    }
    
    public void setTokenBasedControl(boolean tokenBasedControl) {
        this.tokenBasedControl = tokenBasedControl;
    }
    
    public int getTokenTimeoutSeconds() {
        return tokenTimeoutSeconds;
    }
    
    public void setTokenTimeoutSeconds(int tokenTimeoutSeconds) {
        this.tokenTimeoutSeconds = tokenTimeoutSeconds;
    }
    
    public Map<String, WorkstationSettings> getStations() {
        return stations;
    }
    
    public void setStations(Map<String, WorkstationSettings> stations) {
        this.stations = stations;
    }
    
    // === Helper Methods ===
    
    /**
     * Prüft ob ein Arbeitsplatz den Kran steuern DARF (grundsätzlich)
     */
    public boolean isAllowedToControlCrane(String workstationId) {
        // Explizit in Whitelist?
        if (craneControllers.contains(workstationId)) {
            return true;
        }
        
        // Explizit nur View?
        if (viewOnly.contains(workstationId)) {
            return false;
        }
        
        // Individuelle Konfiguration?
        WorkstationSettings settings = stations.get(workstationId);
        if (settings != null) {
            return settings.getMode() == AccessMode.CONTROL;
        }
        
        // Standard-Modus
        return defaultMode == AccessMode.CONTROL;
    }
    
    /**
     * Ermittelt den Zugriffsmodus für einen Arbeitsplatz
     */
    public AccessMode getModeFor(String workstationId) {
        // Explizit Controller?
        if (craneControllers.contains(workstationId)) {
            return AccessMode.CONTROL;
        }
        
        // Explizit nur View?
        if (viewOnly.contains(workstationId)) {
            return AccessMode.VIEW;
        }
        
        // Individuelle Konfiguration?
        WorkstationSettings settings = stations.get(workstationId);
        if (settings != null) {
            return settings.getMode();
        }
        
        return defaultMode;
    }
    
    // === Inner class für individuelle Einstellungen ===
    
    public static class WorkstationSettings {
        private AccessMode mode = AccessMode.VIEW;
        private String description;
        private boolean enabled = true;
        
        public AccessMode getMode() {
            return mode;
        }
        
        public void setMode(AccessMode mode) {
            this.mode = mode;
        }
        
        public String getDescription() {
            return description;
        }
        
        public void setDescription(String description) {
            this.description = description;
        }
        
        public boolean isEnabled() {
            return enabled;
        }
        
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
