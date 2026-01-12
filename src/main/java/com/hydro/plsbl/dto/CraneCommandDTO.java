package com.hydro.plsbl.dto;

/**
 * DTO für Kran-Kommando-Daten
 */
public class CraneCommandDTO {

    private Long id;
    private Long tableSerial;
    private String cmdType;
    private Integer rotate;
    private String craneMode;
    private Long ingotId;
    private String ingotNo;
    private Long fromYardId;
    private String fromYardNo;
    private Long toYardId;
    private String toYardNo;

    // === Getters & Setters ===

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTableSerial() {
        return tableSerial;
    }

    public void setTableSerial(Long tableSerial) {
        this.tableSerial = tableSerial;
    }

    public String getCmdType() {
        return cmdType;
    }

    public void setCmdType(String cmdType) {
        this.cmdType = cmdType;
    }

    public Integer getRotate() {
        return rotate;
    }

    public void setRotate(Integer rotate) {
        this.rotate = rotate;
    }

    public String getCraneMode() {
        return craneMode;
    }

    public void setCraneMode(String craneMode) {
        this.craneMode = craneMode;
    }

    public Long getIngotId() {
        return ingotId;
    }

    public void setIngotId(Long ingotId) {
        this.ingotId = ingotId;
    }

    public String getIngotNo() {
        return ingotNo;
    }

    public void setIngotNo(String ingotNo) {
        this.ingotNo = ingotNo;
    }

    public Long getFromYardId() {
        return fromYardId;
    }

    public void setFromYardId(Long fromYardId) {
        this.fromYardId = fromYardId;
    }

    public String getFromYardNo() {
        return fromYardNo;
    }

    public void setFromYardNo(String fromYardNo) {
        this.fromYardNo = fromYardNo;
    }

    public Long getToYardId() {
        return toYardId;
    }

    public void setToYardId(Long toYardId) {
        this.toYardId = toYardId;
    }

    public String getToYardNo() {
        return toYardNo;
    }

    public void setToYardNo(String toYardNo) {
        this.toYardNo = toYardNo;
    }

    // === Business Methods ===

    public String getCmdTypeDisplay() {
        if (cmdType == null) return "-";
        return switch (cmdType) {
            case "P", "PICK" -> "Aufnehmen";
            case "D", "DROP" -> "Ablegen";
            case "M", "MOVE" -> "Bewegen";
            case "R", "ROTATE" -> "Drehen";
            case "K", "PARK" -> "Parken";
            case "RELOCATE" -> "Umlagern";
            default -> cmdType;
        };
    }

    public String getCraneModeDisplay() {
        if (craneMode == null) return "-";
        return switch (craneMode) {
            case "A", "AUTO" -> "Automatik";
            case "M", "MANUAL" -> "Manuell";
            case "S", "SEMI" -> "Halbautomatik";
            default -> craneMode;
        };
    }

    public String getRoute() {
        String from = fromYardNo != null ? fromYardNo : "-";
        String to = toYardNo != null ? toYardNo : "-";
        return from + " -> " + to;
    }
}
