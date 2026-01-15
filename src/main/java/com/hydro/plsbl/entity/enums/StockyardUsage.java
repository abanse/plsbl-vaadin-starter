package com.hydro.plsbl.entity.enums;

/**
 * Verwendung eines Lagerplatzes
 */
public enum StockyardUsage {
    SHORT('S', "Kurz", "-K-"),
    LONG('L', "Lang", "-L-"),
    AUTOMATIC('A', "Automatisch", "-"),
    RESERVED('R', "Schrott/Reserviert", "-R-");
    
    private final char code;
    private final String displayName;
    private final String emptyLabel;
    
    StockyardUsage(char code, String displayName, String emptyLabel) {
        this.code = code;
        this.displayName = displayName;
        this.emptyLabel = emptyLabel;
    }
    
    public char getCode() { 
        return code; 
    }
    
    public String getDisplayName() { 
        return displayName; 
    }
    
    /**
     * Label das angezeigt wird wenn der Platz leer ist
     */
    public String getEmptyLabel() {
        return emptyLabel;
    }
    
    public static StockyardUsage fromCode(char code) {
        for (StockyardUsage usage : values()) {
            if (usage.code == code) {
                return usage;
            }
        }
        throw new IllegalArgumentException("Unknown StockyardUsage code: " + code);
    }
    
    public static StockyardUsage fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        return fromCode(code.charAt(0));
    }
}
