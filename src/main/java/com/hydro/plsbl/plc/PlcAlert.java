package com.hydro.plsbl.plc;

import java.time.LocalDateTime;

/**
 * Alarm/Warnung von der SPS
 *
 * Repraesentiert Ereignisse wie:
 * - Tuer offen
 * - Notaus aktiviert
 * - Uebertemperatur
 * - Kommunikationsfehler
 */
public class PlcAlert {

    /**
     * Kategorie des Alarms
     */
    public enum AlertCategory {
        COMMUNICATION("Kommunikation"),
        SAFETY("Sicherheit"),
        DOOR("Tuer/Tor"),
        GRIPPER("Greifer"),
        POSITION("Position"),
        SYSTEM("System");

        private final String displayName;

        AlertCategory(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Schweregrad des Alarms
     */
    public enum Severity {
        INFO("Info"),
        WARNING("Warnung"),
        ERROR("Fehler"),
        CRITICAL("Kritisch");

        private final String displayName;

        Severity(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private final AlertCategory category;
    private final Severity severity;
    private final String code;
    private final String text;
    private final boolean raised;
    private final LocalDateTime timestamp;

    public PlcAlert(AlertCategory category, Severity severity, String code, String text, boolean raised) {
        this.category = category;
        this.severity = severity;
        this.code = code;
        this.text = text;
        this.raised = raised;
        this.timestamp = LocalDateTime.now();
    }

    // === Factory Methods ===

    public static PlcAlert info(AlertCategory category, String code, String text) {
        return new PlcAlert(category, Severity.INFO, code, text, true);
    }

    public static PlcAlert warning(AlertCategory category, String code, String text) {
        return new PlcAlert(category, Severity.WARNING, code, text, true);
    }

    public static PlcAlert error(AlertCategory category, String code, String text) {
        return new PlcAlert(category, Severity.ERROR, code, text, true);
    }

    public static PlcAlert critical(AlertCategory category, String code, String text) {
        return new PlcAlert(category, Severity.CRITICAL, code, text, true);
    }

    public static PlcAlert cleared(AlertCategory category, String code, String text) {
        return new PlcAlert(category, Severity.INFO, code, text, false);
    }

    // === Common Alerts ===

    public static PlcAlert linkDown() {
        return critical(AlertCategory.COMMUNICATION, "LINK_DOWN", "Verbindung zur SPS unterbrochen");
    }

    public static PlcAlert linkRestored() {
        return cleared(AlertCategory.COMMUNICATION, "LINK_DOWN", "Verbindung zur SPS wiederhergestellt");
    }

    public static PlcAlert doorOpen(int doorNumber) {
        return warning(AlertCategory.DOOR, "DOOR_" + doorNumber, "Tor " + doorNumber + " ist offen");
    }

    public static PlcAlert emergencyStop() {
        return critical(AlertCategory.SAFETY, "EMERGENCY_STOP", "Not-Aus aktiviert");
    }

    public static PlcAlert craneOff() {
        return error(AlertCategory.SYSTEM, "CRANE_OFF", "Kran ist ausgeschaltet");
    }

    // === Getters ===

    public AlertCategory getCategory() {
        return category;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getCode() {
        return code;
    }

    public String getText() {
        return text;
    }

    public boolean isRaised() {
        return raised;
    }

    public boolean isCleared() {
        return !raised;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public boolean isCritical() {
        return severity == Severity.CRITICAL;
    }

    public boolean isError() {
        return severity == Severity.ERROR || severity == Severity.CRITICAL;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s: %s - %s (%s)",
            severity.getDisplayName(),
            category.getDisplayName(),
            code,
            text,
            raised ? "AKTIV" : "BEHOBEN");
    }
}
