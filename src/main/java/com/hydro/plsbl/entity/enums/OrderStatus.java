package com.hydro.plsbl.entity.enums;

/**
 * Status eines Transportauftrags
 */
public enum OrderStatus {

    /** Auftrag erstellt, wartet auf Verarbeitung */
    PENDING("P", "Wartend"),

    /** Auftrag wird gerade vom Kran ausgeführt */
    IN_PROGRESS("I", "In Bearbeitung"),

    /** Kran hat Barren aufgenommen */
    PICKED_UP("U", "Aufgenommen"),

    /** Auftrag erfolgreich abgeschlossen */
    COMPLETED("C", "Abgeschlossen"),

    /** Auftrag fehlgeschlagen */
    FAILED("F", "Fehlgeschlagen"),

    /** Auftrag abgebrochen */
    CANCELLED("X", "Abgebrochen"),

    /** Auftrag pausiert (z.B. Türen offen) */
    PAUSED("H", "Pausiert");

    private final String code;
    private final String displayName;

    OrderStatus(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Findet OrderStatus anhand des Codes
     */
    public static OrderStatus fromCode(String code) {
        if (code == null) return PENDING;
        for (OrderStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return PENDING;
    }

    /**
     * Prüft ob der Status aktiv ist (noch nicht abgeschlossen)
     */
    public boolean isActive() {
        return this == PENDING || this == IN_PROGRESS || this == PICKED_UP || this == PAUSED;
    }

    /**
     * Prüft ob der Status ein End-Status ist
     */
    public boolean isFinal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
