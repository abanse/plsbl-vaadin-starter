package com.hydro.plsbl.dto;

/**
 * DTO für Kran-Status-Daten
 */
public class CraneStatusDTO {

    private Long id;
    private Integer xPosition;
    private Integer yPosition;
    private Integer zPosition;
    private String craneMode;
    private String gripperState;
    private String jobState;
    private String daemonState;
    private String workPhase;
    private Long fromStockyardId;
    private String fromStockyardNo;
    private Long toStockyardId;
    private String toStockyardNo;
    private String incident;
    private String incidentText;
    private Boolean doorsOpen;
    private Boolean gatesOpen;

    // Barren im Greifer (falls vorhanden)
    private String ingotNo;
    private String ingotProductNo;
    private Integer ingotLength;
    private Integer ingotWidth;

    // === Getters & Setters ===

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getXPosition() {
        return xPosition;
    }

    public void setXPosition(Integer xPosition) {
        this.xPosition = xPosition;
    }

    public Integer getYPosition() {
        return yPosition;
    }

    public void setYPosition(Integer yPosition) {
        this.yPosition = yPosition;
    }

    public Integer getZPosition() {
        return zPosition;
    }

    public void setZPosition(Integer zPosition) {
        this.zPosition = zPosition;
    }

    public String getCraneMode() {
        return craneMode;
    }

    public void setCraneMode(String craneMode) {
        this.craneMode = craneMode;
    }

    public String getGripperState() {
        return gripperState;
    }

    public void setGripperState(String gripperState) {
        this.gripperState = gripperState;
    }

    public String getJobState() {
        return jobState;
    }

    public void setJobState(String jobState) {
        this.jobState = jobState;
    }

    public String getDaemonState() {
        return daemonState;
    }

    public void setDaemonState(String daemonState) {
        this.daemonState = daemonState;
    }

    public String getWorkPhase() {
        return workPhase;
    }

    public void setWorkPhase(String workPhase) {
        this.workPhase = workPhase;
    }

    public Long getFromStockyardId() {
        return fromStockyardId;
    }

    public void setFromStockyardId(Long fromStockyardId) {
        this.fromStockyardId = fromStockyardId;
    }

    public String getFromStockyardNo() {
        return fromStockyardNo;
    }

    public void setFromStockyardNo(String fromStockyardNo) {
        this.fromStockyardNo = fromStockyardNo;
    }

    public Long getToStockyardId() {
        return toStockyardId;
    }

    public void setToStockyardId(Long toStockyardId) {
        this.toStockyardId = toStockyardId;
    }

    public String getToStockyardNo() {
        return toStockyardNo;
    }

    public void setToStockyardNo(String toStockyardNo) {
        this.toStockyardNo = toStockyardNo;
    }

    public String getIncident() {
        return incident;
    }

    public void setIncident(String incident) {
        this.incident = incident;
    }

    public String getIncidentText() {
        return incidentText;
    }

    public void setIncidentText(String incidentText) {
        this.incidentText = incidentText;
    }

    public Boolean getDoorsOpen() {
        return doorsOpen;
    }

    public void setDoorsOpen(Boolean doorsOpen) {
        this.doorsOpen = doorsOpen;
    }

    public Boolean getGatesOpen() {
        return gatesOpen;
    }

    public void setGatesOpen(Boolean gatesOpen) {
        this.gatesOpen = gatesOpen;
    }

    public String getIngotNo() {
        return ingotNo;
    }

    public void setIngotNo(String ingotNo) {
        this.ingotNo = ingotNo;
    }

    public String getIngotProductNo() {
        return ingotProductNo;
    }

    public void setIngotProductNo(String ingotProductNo) {
        this.ingotProductNo = ingotProductNo;
    }

    public Integer getIngotLength() {
        return ingotLength;
    }

    public void setIngotLength(Integer ingotLength) {
        this.ingotLength = ingotLength;
    }

    public Integer getIngotWidth() {
        return ingotWidth;
    }

    public void setIngotWidth(Integer ingotWidth) {
        this.ingotWidth = ingotWidth;
    }

    // === Business Methods ===

    public boolean isAutomatic() {
        return "AUTOMATIC".equals(craneMode);
    }

    public boolean isManual() {
        return "MANUAL".equals(craneMode);
    }

    public boolean isIdle() {
        return "IDLE".equals(jobState);
    }

    public boolean hasIncident() {
        return incident != null && !"OK".equals(incident);
    }

    public boolean hasIngot() {
        return ingotNo != null && !ingotNo.isEmpty();
    }

    public boolean isGripperHolding() {
        return "CLOSED".equals(gripperState) || "GRIPPING".equals(gripperState);
    }

    public String getModeDisplay() {
        if (craneMode == null) return "Unbekannt";
        return switch (craneMode) {
            case "AUTOMATIC" -> "Automatik";
            case "MANUAL" -> "Handbetrieb";
            case "SEMI_AUTOMATIC" -> "Halbautomatik";
            default -> craneMode;
        };
    }

    public String getJobStateDisplay() {
        if (jobState == null) return "Unbekannt";
        return switch (jobState) {
            case "IDLE" -> "Bereit";
            case "WORKING" -> "Arbeitet";
            case "WAITING" -> "Wartet";
            case "ERROR" -> "Fehler";
            default -> jobState;
        };
    }

    public String getGripperStateDisplay() {
        if (gripperState == null) return "Unbekannt";
        return switch (gripperState) {
            case "OPEN" -> "Offen";
            case "CLOSED" -> "Geschlossen";
            case "GRIPPING" -> "Greift";
            default -> gripperState;
        };
    }
}
