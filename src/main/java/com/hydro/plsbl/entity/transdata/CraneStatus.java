package com.hydro.plsbl.entity.transdata;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Kran-Status - Bewegungsdaten (nur 1 Eintrag)
 *
 * Entspricht der Tabelle TD_CRANESTATUS
 */
@Table("TD_CRANESTATUS")
public class CraneStatus {

    @Id
    private Long id;

    @Version
    @Column("SERIAL")
    private Long serial;

    @Column("X_POSITION")
    private Integer xPosition;

    @Column("Y_POSITION")
    private Integer yPosition;

    @Column("Z_POSITION")
    private Integer zPosition;

    @Column("CRANE_MODE")
    private String craneMode;

    @Column("GRIPPER_STATE")
    private String gripperState;

    @Column("JOB_STATE")
    private String jobState;

    @Column("DAEMON_STATE")
    private String daemonState;

    @Column("WORK_PHASE")
    private String workPhase;

    @Column("FROM_STOCKYARD_ID")
    private Long fromStockyardId;

    @Column("TO_STOCKYARD_ID")
    private Long toStockyardId;

    @Column("INCIDENT")
    private String incident;

    @Column("INCIDENT_TEXT")
    private String incidentText;

    @Column("DOORS_OPEN")
    private Boolean doorsOpen;

    @Column("GATES_OPEN")
    private Boolean gatesOpen;

    // === Getters & Setters ===

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSerial() {
        return serial;
    }

    public void setSerial(Long serial) {
        this.serial = serial;
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

    public Long getToStockyardId() {
        return toStockyardId;
    }

    public void setToStockyardId(Long toStockyardId) {
        this.toStockyardId = toStockyardId;
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

    @Override
    public String toString() {
        return "CraneStatus{" +
                "craneMode='" + craneMode + '\'' +
                ", jobState='" + jobState + '\'' +
                ", position=(" + xPosition + "," + yPosition + "," + zPosition + ")" +
                '}';
    }
}
