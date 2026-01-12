package com.hydro.plsbl.dto;

import java.time.LocalDateTime;

/**
 * DTO für Säge-Status und Einlagerungsauftrag
 */
public class SawStatusDTO {

    // Pickup Mode
    public enum PickupMode {
        NO_PICKUP("Keine Einlagerung"),
        AUTOMATIC("Automatisch"),
        MANUAL("Manuell");

        private final String displayName;

        PickupMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // Status-Felder
    private PickupMode pickupMode = PickupMode.NO_PICKUP;
    private boolean rotate;
    private boolean pickupInProgress;

    // Einlagerungsauftrag
    private String pickupNumber;
    private LocalDateTime received;

    // Position von Säge
    private Integer positionX;
    private Integer positionY;
    private Integer positionZ;

    // Errechnete Position
    private Integer computedX;
    private Integer computedY;
    private Integer computedZ;

    // Barren-Daten
    private String ingotNo;
    private String productNo;
    private String productSuffix;
    private Integer length;
    private Integer width;
    private Integer thickness;
    private Integer weight;

    // Säge-Status
    private boolean headSawn;
    private boolean footSawn;
    private boolean scrap;
    private boolean revised;

    // Fehler
    private String errorType;
    private String errorMessage;

    // Bestätigungen
    private boolean returnConfirmed;
    private boolean recycleConfirmed;
    private boolean sawnConfirmed;

    // === Getters & Setters ===

    public PickupMode getPickupMode() {
        return pickupMode;
    }

    public void setPickupMode(PickupMode pickupMode) {
        this.pickupMode = pickupMode;
    }

    public boolean isRotate() {
        return rotate;
    }

    public void setRotate(boolean rotate) {
        this.rotate = rotate;
    }

    public boolean isPickupInProgress() {
        return pickupInProgress;
    }

    public void setPickupInProgress(boolean pickupInProgress) {
        this.pickupInProgress = pickupInProgress;
    }

    public String getPickupNumber() {
        return pickupNumber;
    }

    public void setPickupNumber(String pickupNumber) {
        this.pickupNumber = pickupNumber;
    }

    public LocalDateTime getReceived() {
        return received;
    }

    public void setReceived(LocalDateTime received) {
        this.received = received;
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

    public String getIngotNo() {
        return ingotNo;
    }

    public void setIngotNo(String ingotNo) {
        this.ingotNo = ingotNo;
    }

    public String getProductNo() {
        return productNo;
    }

    public void setProductNo(String productNo) {
        this.productNo = productNo;
    }

    public String getProductSuffix() {
        return productSuffix;
    }

    public void setProductSuffix(String productSuffix) {
        this.productSuffix = productSuffix;
    }

    public Integer getLength() {
        return length;
    }

    public void setLength(Integer length) {
        this.length = length;
    }

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public Integer getThickness() {
        return thickness;
    }

    public void setThickness(Integer thickness) {
        this.thickness = thickness;
    }

    public Integer getWeight() {
        return weight;
    }

    public void setWeight(Integer weight) {
        this.weight = weight;
    }

    public boolean isHeadSawn() {
        return headSawn;
    }

    public void setHeadSawn(boolean headSawn) {
        this.headSawn = headSawn;
    }

    public boolean isFootSawn() {
        return footSawn;
    }

    public void setFootSawn(boolean footSawn) {
        this.footSawn = footSawn;
    }

    public boolean isScrap() {
        return scrap;
    }

    public void setScrap(boolean scrap) {
        this.scrap = scrap;
    }

    public boolean isRevised() {
        return revised;
    }

    public void setRevised(boolean revised) {
        this.revised = revised;
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

    public boolean isReturnConfirmed() {
        return returnConfirmed;
    }

    public void setReturnConfirmed(boolean returnConfirmed) {
        this.returnConfirmed = returnConfirmed;
    }

    public boolean isRecycleConfirmed() {
        return recycleConfirmed;
    }

    public void setRecycleConfirmed(boolean recycleConfirmed) {
        this.recycleConfirmed = recycleConfirmed;
    }

    public boolean isSawnConfirmed() {
        return sawnConfirmed;
    }

    public void setSawnConfirmed(boolean sawnConfirmed) {
        this.sawnConfirmed = sawnConfirmed;
    }

    // === Business Methods ===

    public boolean hasPickup() {
        return pickupNumber != null && !pickupNumber.isEmpty();
    }

    public boolean hasError() {
        return errorType != null && !errorType.isEmpty();
    }

    public boolean hasIngot() {
        return ingotNo != null && !ingotNo.isEmpty();
    }

    public String getPickupModeDisplay() {
        return pickupMode != null ? pickupMode.getDisplayName() : "-";
    }
}
