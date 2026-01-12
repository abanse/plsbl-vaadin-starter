package com.hydro.plsbl.plc.dto;

import java.time.LocalDateTime;

/**
 * Status-DTO fuer die SPS-Kommunikation (Lesen von der SPS)
 *
 * Enthaelt alle Daten, die von der SPS gelesen werden:
 * - Kommunikationsstatus
 * - Kran-Position und Modus
 * - Greifer-Zustand
 * - Job-Status
 * - Tuer-Status
 * - Barren-Daten (falls geladen)
 *
 * Mutable - wird bei jedem Poll aktualisiert.
 */
public class PlcStatus implements Cloneable {

    // === Kommunikationsstatus ===
    private boolean linkDown;
    private boolean checksumError;
    private int jobNumber;
    private boolean plcError;

    // === Kran-Status ===
    private CraneMode craneMode = CraneMode.MANUAL;
    private boolean craneOff;

    // === Positionsdaten [mm] ===
    private int xPosition;
    private int yPosition;
    private int zPosition;

    // === Greifer-Status ===
    private GripperState gripperState = GripperState.OPEN;

    // === Job-Status ===
    private JobState jobState = JobState.IDLE;
    private WorkPhase workPhase = WorkPhase.IDLE;

    // === Tuer-Status ===
    private boolean door1Open;      // Tor 1
    private boolean door7Open;      // Tor 7
    private boolean door10Open;     // Tor 10
    private boolean gatesOpen;      // Tore 6 & 8
    private boolean doorsOpen;      // Tueren 2,3,4,5,9

    // === Barren-Daten (Echo vom Kommando) ===
    private int length;
    private int width;
    private int thickness;
    private int weight;
    private boolean longIngot;
    private boolean rotate;

    // === Echo der Positionen ===
    private int pickupPositionX;
    private int pickupPositionY;
    private int pickupPositionZ;
    private int releasePositionX;
    private int releasePositionY;
    private int releasePositionZ;

    // === Interne Zaehler ===
    private int receiveCounter;
    private LocalDateTime lastUpdate;

    public PlcStatus() {
        this.lastUpdate = LocalDateTime.now();
    }

    @Override
    public PlcStatus clone() {
        try {
            PlcStatus clone = (PlcStatus) super.clone();
            clone.lastUpdate = LocalDateTime.now();
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    // === Hilfsmethoden ===

    public void incrementReceiveCounter() {
        receiveCounter++;
        lastUpdate = LocalDateTime.now();
    }

    public void incrementJobNumber() {
        jobNumber = (jobNumber + 1) % 10000;
    }

    public boolean hasError() {
        return linkDown || checksumError || plcError;
    }

    public boolean isReady() {
        return !hasError() && !craneOff && craneMode == CraneMode.AUTOMATIC && jobState == JobState.IDLE;
    }

    public boolean isWorking() {
        return jobState == JobState.STARTED || jobState == JobState.LOADED;
    }

    public boolean anyDoorOpen() {
        return door1Open || door7Open || door10Open || gatesOpen || doorsOpen;
    }

    // === Getters & Setters ===

    public boolean isLinkDown() {
        return linkDown;
    }

    public void setLinkDown(boolean linkDown) {
        this.linkDown = linkDown;
    }

    public boolean isChecksumError() {
        return checksumError;
    }

    public void setChecksumError(boolean checksumError) {
        this.checksumError = checksumError;
    }

    public int getJobNumber() {
        return jobNumber;
    }

    public void setJobNumber(int jobNumber) {
        this.jobNumber = jobNumber;
    }

    public boolean isPlcError() {
        return plcError;
    }

    public void setPlcError(boolean plcError) {
        this.plcError = plcError;
    }

    public CraneMode getCraneMode() {
        return craneMode;
    }

    public void setCraneMode(CraneMode craneMode) {
        this.craneMode = craneMode;
    }

    public boolean isCraneOff() {
        return craneOff;
    }

    public void setCraneOff(boolean craneOff) {
        this.craneOff = craneOff;
    }

    public int getXPosition() {
        return xPosition;
    }

    public void setXPosition(int xPosition) {
        this.xPosition = xPosition;
    }

    public int getYPosition() {
        return yPosition;
    }

    public void setYPosition(int yPosition) {
        this.yPosition = yPosition;
    }

    public int getZPosition() {
        return zPosition;
    }

    public void setZPosition(int zPosition) {
        this.zPosition = zPosition;
    }

    public GripperState getGripperState() {
        return gripperState;
    }

    public void setGripperState(GripperState gripperState) {
        this.gripperState = gripperState;
    }

    public JobState getJobState() {
        return jobState;
    }

    public void setJobState(JobState jobState) {
        this.jobState = jobState;
    }

    public WorkPhase getWorkPhase() {
        return workPhase;
    }

    public void setWorkPhase(WorkPhase workPhase) {
        this.workPhase = workPhase;
    }

    public boolean isDoor1Open() {
        return door1Open;
    }

    public void setDoor1Open(boolean door1Open) {
        this.door1Open = door1Open;
    }

    public boolean isDoor7Open() {
        return door7Open;
    }

    public void setDoor7Open(boolean door7Open) {
        this.door7Open = door7Open;
    }

    public boolean isDoor10Open() {
        return door10Open;
    }

    public void setDoor10Open(boolean door10Open) {
        this.door10Open = door10Open;
    }

    public boolean isGatesOpen() {
        return gatesOpen;
    }

    public void setGatesOpen(boolean gatesOpen) {
        this.gatesOpen = gatesOpen;
    }

    public boolean isDoorsOpen() {
        return doorsOpen;
    }

    public void setDoorsOpen(boolean doorsOpen) {
        this.doorsOpen = doorsOpen;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getThickness() {
        return thickness;
    }

    public void setThickness(int thickness) {
        this.thickness = thickness;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public boolean isLongIngot() {
        return longIngot;
    }

    public void setLongIngot(boolean longIngot) {
        this.longIngot = longIngot;
    }

    public boolean isRotate() {
        return rotate;
    }

    public void setRotate(boolean rotate) {
        this.rotate = rotate;
    }

    public int getPickupPositionX() {
        return pickupPositionX;
    }

    public void setPickupPositionX(int pickupPositionX) {
        this.pickupPositionX = pickupPositionX;
    }

    public int getPickupPositionY() {
        return pickupPositionY;
    }

    public void setPickupPositionY(int pickupPositionY) {
        this.pickupPositionY = pickupPositionY;
    }

    public int getPickupPositionZ() {
        return pickupPositionZ;
    }

    public void setPickupPositionZ(int pickupPositionZ) {
        this.pickupPositionZ = pickupPositionZ;
    }

    public int getReleasePositionX() {
        return releasePositionX;
    }

    public void setReleasePositionX(int releasePositionX) {
        this.releasePositionX = releasePositionX;
    }

    public int getReleasePositionY() {
        return releasePositionY;
    }

    public void setReleasePositionY(int releasePositionY) {
        this.releasePositionY = releasePositionY;
    }

    public int getReleasePositionZ() {
        return releasePositionZ;
    }

    public void setReleasePositionZ(int releasePositionZ) {
        this.releasePositionZ = releasePositionZ;
    }

    public int getReceiveCounter() {
        return receiveCounter;
    }

    public void setReceiveCounter(int receiveCounter) {
        this.receiveCounter = receiveCounter;
    }

    public LocalDateTime getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(LocalDateTime lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    @Override
    public String toString() {
        return "PlcStatus{" +
            "pos=(" + xPosition + "," + yPosition + "," + zPosition + ")" +
            ", mode=" + craneMode +
            ", gripper=" + gripperState +
            ", job=" + jobState +
            ", phase=" + workPhase +
            (hasError() ? ", ERROR" : "") +
            (craneOff ? ", OFF" : "") +
            '}';
    }
}
