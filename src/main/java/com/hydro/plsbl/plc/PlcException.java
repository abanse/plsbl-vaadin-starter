package com.hydro.plsbl.plc;

/**
 * Exception fuer PLC-Kommunikationsfehler
 *
 * Wird geworfen wenn:
 * - Verbindung zur SPS fehlschlaegt
 * - Lesen/Schreiben fehlschlaegt
 * - Timeout auftritt
 * - Protokollfehler erkannt wird
 */
public class PlcException extends Exception {

    private final String plcError;
    private final boolean reconnectNecessary;

    public PlcException(String message) {
        super(message);
        this.plcError = null;
        this.reconnectNecessary = false;
    }

    public PlcException(String message, Throwable cause) {
        super(message, cause);
        this.plcError = null;
        this.reconnectNecessary = true;
    }

    public PlcException(String message, String plcError, boolean reconnectNecessary) {
        super(message);
        this.plcError = plcError;
        this.reconnectNecessary = reconnectNecessary;
    }

    public PlcException(String message, String plcError, boolean reconnectNecessary, Throwable cause) {
        super(message, cause);
        this.plcError = plcError;
        this.reconnectNecessary = reconnectNecessary;
    }

    /**
     * Nativer Fehlertext von der SPS (falls vorhanden)
     */
    public String getPlcError() {
        return plcError;
    }

    /**
     * Gibt an, ob eine Neuverbindung notwendig ist
     */
    public boolean isReconnectNecessary() {
        return reconnectNecessary;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("PlcException: ");
        sb.append(getMessage());
        if (plcError != null) {
            sb.append(" [PLC: ").append(plcError).append("]");
        }
        if (reconnectNecessary) {
            sb.append(" (reconnect required)");
        }
        return sb.toString();
    }
}
