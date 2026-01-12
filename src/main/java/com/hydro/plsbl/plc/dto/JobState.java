package com.hydro.plsbl.plc.dto;

/**
 * Status des aktuellen Auftrags
 */
public enum JobState {

    IDLE("I", "Bereit"),
    STARTED("S", "Gestartet"),
    LOADED("L", "Beladen"),
    DROPPED("D", "Abgelegt");

    private final String code;
    private final String displayName;

    JobState(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static JobState fromCode(String code) {
        if (code == null) return null;
        for (JobState state : values()) {
            if (state.code.equals(code)) {
                return state;
            }
        }
        return null;
    }

    public boolean isWorking() {
        return this == STARTED || this == LOADED;
    }

    public boolean isComplete() {
        return this == DROPPED || this == IDLE;
    }
}
