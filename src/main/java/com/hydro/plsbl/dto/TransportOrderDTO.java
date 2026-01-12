package com.hydro.plsbl.dto;

/**
 * DTO für Transportauftrag-Daten
 */
public class TransportOrderDTO {

    private Long id;
    private Long tableSerial;
    private String transportNo;
    private String normText;
    private Long calloffId;
    private Long ingotId;
    private String ingotNo;
    private Long fromYardId;
    private String fromYardNo;
    private Integer fromPilePosition;
    private Long toYardId;
    private String toYardNo;
    private Integer toPilePosition;

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

    public String getTransportNo() {
        return transportNo;
    }

    public void setTransportNo(String transportNo) {
        this.transportNo = transportNo;
    }

    public String getNormText() {
        return normText;
    }

    public void setNormText(String normText) {
        this.normText = normText;
    }

    public Long getCalloffId() {
        return calloffId;
    }

    public void setCalloffId(Long calloffId) {
        this.calloffId = calloffId;
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

    public Integer getFromPilePosition() {
        return fromPilePosition;
    }

    public void setFromPilePosition(Integer fromPilePosition) {
        this.fromPilePosition = fromPilePosition;
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

    public Integer getToPilePosition() {
        return toPilePosition;
    }

    public void setToPilePosition(Integer toPilePosition) {
        this.toPilePosition = toPilePosition;
    }

    // === Business Methods ===

    public String getRoute() {
        String from = fromYardNo != null ? fromYardNo : "-";
        String to = toYardNo != null ? toYardNo : "-";
        return from + " -> " + to;
    }

    public String getRouteWithPositions() {
        String from = fromYardNo != null ? fromYardNo : "-";
        String to = toYardNo != null ? toYardNo : "-";
        String fromPos = fromPilePosition != null ? "[" + fromPilePosition + "]" : "";
        String toPos = toPilePosition != null ? "[" + toPilePosition + "]" : "";
        return from + fromPos + " -> " + to + toPos;
    }
}
