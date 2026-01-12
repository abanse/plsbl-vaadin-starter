package com.hydro.plsbl.entity.enums;

/**
 * Typ eines Lagerplatzes
 */
public enum StockyardType {
    INTERNAL('I', "Intern"),
    EXTERNAL('E', "Extern"),
    SAW('S', "Säge"),
    SWAPOUT('O', "Auslagerung"),
    LOADING('L', "Verladung"),
    AUSGANG('A', "Ausgang");
    
    private final char code;
    private final String displayName;
    
    StockyardType(char code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }
    
    public char getCode() { 
        return code; 
    }
    
    public String getDisplayName() { 
        return displayName; 
    }
    
    /**
     * Wird dieser Typ in der Lager-Ansicht angezeigt?
     */
    public boolean isShownInStockView() {
        return this == INTERNAL || this == EXTERNAL || this == LOADING;
    }
    
    public static StockyardType fromCode(char code) {
        for (StockyardType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown StockyardType code: " + code);
    }
    
    public static StockyardType fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        return fromCode(code.charAt(0));
    }
}
