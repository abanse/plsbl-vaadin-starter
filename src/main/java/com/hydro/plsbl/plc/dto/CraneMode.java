package com.hydro.plsbl.plc.dto;

/**
 * Betriebsmodus des Krans
 */
public enum CraneMode {

    AUTOMATIC("A", "Automatik"),
    MANUAL("M", "Handbetrieb"),
    SEMI_AUTOMATIC("S", "Halbautomatik");

    private final String code;
    private final String displayName;

    CraneMode(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static CraneMode fromCode(String code) {
        if (code == null) return null;
        for (CraneMode mode : values()) {
            if (mode.code.equals(code)) {
                return mode;
            }
        }
        return null;
    }
}
