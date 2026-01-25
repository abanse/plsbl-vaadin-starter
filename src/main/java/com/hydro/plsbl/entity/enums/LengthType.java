package com.hydro.plsbl.entity.enums;

/**
 * Längentyp für Barren
 */
public enum LengthType {
    SHORT('S', "Kurz"),
    MEDIUM('M', "Mittel"),
    LONG('L', "Lang");

    private final char code;
    private final String displayName;

    LengthType(char code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public char getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static LengthType fromCode(char code) {
        for (LengthType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown LengthType code: " + code);
    }

    public static LengthType fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        return fromCode(code.charAt(0));
    }
}
