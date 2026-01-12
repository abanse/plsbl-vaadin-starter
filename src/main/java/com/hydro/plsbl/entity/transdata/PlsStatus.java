package com.hydro.plsbl.entity.transdata;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * PLS-Status (Säge-Einlagerung) - nur 1 Eintrag
 *
 * Entspricht der Tabelle TD_PLSSTATUS
 */
@Table("TD_PLSSTATUS")
public class PlsStatus {

    @Id
    private Long id;

    @Version
    @Column("SERIAL")
    private Long serial;

    @Column("PICKUP_MODE")
    private String pickupMode;

    @Column("PICKUP_NUMBER")
    private String pickupNumber;

    @Column("PICKUP_ORDER_ID")
    private Long pickupOrderId;

    @Column("PICKUP_IN_PROGRESS")
    private Boolean pickupInProgress;

    @Column("ROTATE")
    private Boolean rotate;

    @Column("RECEIVED_TIME")
    private LocalDateTime receivedTime;

    @Column("POSITION_X")
    private Integer positionX;

    @Column("POSITION_Y")
    private Integer positionY;

    @Column("POSITION_Z")
    private Integer positionZ;

    @Column("COMPUTED_X")
    private Integer computedX;

    @Column("COMPUTED_Y")
    private Integer computedY;

    @Column("COMPUTED_Z")
    private Integer computedZ;

    @Column("ERROR_TYPE")
    private String errorType;

    @Column("ERROR_MESSAGE")
    private String errorMessage;

    @Column("RETURN_CONFIRMED")
    private Boolean returnConfirmed;

    @Column("RECYCLE_CONFIRMED")
    private Boolean recycleConfirmed;

    @Column("SAWN_CONFIRMED")
    private Boolean sawnConfirmed;

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

    public String getPickupMode() {
        return pickupMode;
    }

    public void setPickupMode(String pickupMode) {
        this.pickupMode = pickupMode;
    }

    public String getPickupNumber() {
        return pickupNumber;
    }

    public void setPickupNumber(String pickupNumber) {
        this.pickupNumber = pickupNumber;
    }

    public Long getPickupOrderId() {
        return pickupOrderId;
    }

    public void setPickupOrderId(Long pickupOrderId) {
        this.pickupOrderId = pickupOrderId;
    }

    public Boolean getPickupInProgress() {
        return pickupInProgress;
    }

    public void setPickupInProgress(Boolean pickupInProgress) {
        this.pickupInProgress = pickupInProgress;
    }

    public Boolean getRotate() {
        return rotate;
    }

    public void setRotate(Boolean rotate) {
        this.rotate = rotate;
    }

    public LocalDateTime getReceivedTime() {
        return receivedTime;
    }

    public void setReceivedTime(LocalDateTime receivedTime) {
        this.receivedTime = receivedTime;
    }

    public Integer getPositionX() {
        return positionX;
    }

    public void setPositionX(Integer positionX) {
        this.positionX = positionX;
    }

    public Integer getPositionY() {
        return positionY;
    }

    public void setPositionY(Integer positionY) {
        this.positionY = positionY;
    }

    public Integer getPositionZ() {
        return positionZ;
    }

    public void setPositionZ(Integer positionZ) {
        this.positionZ = positionZ;
    }

    public Integer getComputedX() {
        return computedX;
    }

    public void setComputedX(Integer computedX) {
        this.computedX = computedX;
    }

    public Integer getComputedY() {
        return computedY;
    }

    public void setComputedY(Integer computedY) {
        this.computedY = computedY;
    }

    public Integer getComputedZ() {
        return computedZ;
    }

    public void setComputedZ(Integer computedZ) {
        this.computedZ = computedZ;
    }

    public String getErrorType() {
        return errorType;
    }

    public void setErrorType(String errorType) {
        this.errorType = errorType;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Boolean getReturnConfirmed() {
        return returnConfirmed;
    }

    public void setReturnConfirmed(Boolean returnConfirmed) {
        this.returnConfirmed = returnConfirmed;
    }

    public Boolean getRecycleConfirmed() {
        return recycleConfirmed;
    }

    public void setRecycleConfirmed(Boolean recycleConfirmed) {
        this.recycleConfirmed = recycleConfirmed;
    }

    public Boolean getSawnConfirmed() {
        return sawnConfirmed;
    }

    public void setSawnConfirmed(Boolean sawnConfirmed) {
        this.sawnConfirmed = sawnConfirmed;
    }

    // === Business Methods ===

    public boolean isPickupScheduled() {
        return pickupNumber != null && !pickupNumber.isEmpty();
    }

    public boolean isPickupRunning() {
        return Boolean.TRUE.equals(pickupInProgress);
    }

    public boolean isAutomatic() {
        return "AUTOMATIC".equals(pickupMode);
    }

    public boolean isManual() {
        return "MANUAL".equals(pickupMode);
    }

    public boolean isNoPickup() {
        return "NO_PICKUP".equals(pickupMode) || pickupMode == null;
    }

    public boolean hasError() {
        return errorType != null && !errorType.isEmpty();
    }

    @Override
    public String toString() {
        return "PlsStatus{" +
                "pickupMode='" + pickupMode + '\'' +
                ", pickupNumber='" + pickupNumber + '\'' +
                ", inProgress=" + pickupInProgress +
                '}';
    }
}
