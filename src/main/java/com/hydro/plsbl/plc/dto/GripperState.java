package com.hydro.plsbl.plc.dto;

/**
 * Zustand des Greifers
 */
public enum GripperState {

    OPEN("O", "Offen"),
    CLOSED("C", "Geschlossen"),
    LOADED("L", "Beladen");

    private final String code;
    private final String displayName;

    GripperState(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static GripperState fromCode(String code) {
        if (code == null) return null;
        for (GripperState state : values()) {
            if (state.code.equals(code)) {
                return state;
            }
        }
        return null;
    }

    public boolean isHolding() {
        return this == LOADED;
    }
}
