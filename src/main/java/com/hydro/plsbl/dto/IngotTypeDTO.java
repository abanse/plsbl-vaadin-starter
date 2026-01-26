package com.hydro.plsbl.dto;

import com.hydro.plsbl.entity.enums.LengthType;

/**
 * DTO für Barrentyp
 */
public class IngotTypeDTO {

    private Long id;
    private Long serial;
    private String name;
    private String description;
    private LengthType lengthType;
    private Boolean internalAllowed;
    private Boolean externalAllowed;
    private Boolean retrievalAllowed;
    private Boolean autoRetrieval;
    private Boolean sawToSwapout;
    private Integer minLength;
    private Integer maxLength;
    private Integer minWidth;
    private Integer maxWidth;
    private Integer minThickness;
    private Integer maxThickness;
    private Integer minWeight;
    private Integer maxWeight;
    private String productRegex;
    private Integer priority;

    public IngotTypeDTO() {
    }

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LengthType getLengthType() {
        return lengthType;
    }

    public void setLengthType(LengthType lengthType) {
        this.lengthType = lengthType;
    }

    public Boolean getInternalAllowed() {
        return internalAllowed;
    }

    public void setInternalAllowed(Boolean internalAllowed) {
        this.internalAllowed = internalAllowed;
    }

    public Boolean getExternalAllowed() {
        return externalAllowed;
    }

    public void setExternalAllowed(Boolean externalAllowed) {
        this.externalAllowed = externalAllowed;
    }

    public Boolean getRetrievalAllowed() {
        return retrievalAllowed;
    }

    public void setRetrievalAllowed(Boolean retrievalAllowed) {
        this.retrievalAllowed = retrievalAllowed;
    }

    public Boolean getAutoRetrieval() {
        return autoRetrieval;
    }

    public void setAutoRetrieval(Boolean autoRetrieval) {
        this.autoRetrieval = autoRetrieval;
    }

    public Boolean getSawToSwapout() {
        return sawToSwapout;
    }

    public void setSawToSwapout(Boolean sawToSwapout) {
        this.sawToSwapout = sawToSwapout;
    }

    public Integer getMinLength() {
        return minLength;
    }

    public void setMinLength(Integer minLength) {
        this.minLength = minLength;
    }

    public Integer getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(Integer maxLength) {
        this.maxLength = maxLength;
    }

    public Integer getMinWidth() {
        return minWidth;
    }

    public void setMinWidth(Integer minWidth) {
        this.minWidth = minWidth;
    }

    public Integer getMaxWidth() {
        return maxWidth;
    }

    public void setMaxWidth(Integer maxWidth) {
        this.maxWidth = maxWidth;
    }

    public Integer getMinThickness() {
        return minThickness;
    }

    public void setMinThickness(Integer minThickness) {
        this.minThickness = minThickness;
    }

    public Integer getMaxThickness() {
        return maxThickness;
    }

    public void setMaxThickness(Integer maxThickness) {
        this.maxThickness = maxThickness;
    }

    public Integer getMinWeight() {
        return minWeight;
    }

    public void setMinWeight(Integer minWeight) {
        this.minWeight = minWeight;
    }

    public Integer getMaxWeight() {
        return maxWeight;
    }

    public void setMaxWeight(Integer maxWeight) {
        this.maxWeight = maxWeight;
    }

    public String getProductRegex() {
        return productRegex;
    }

    public void setProductRegex(String productRegex) {
        this.productRegex = productRegex;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    /**
     * Gibt den Längenbereich als String zurück
     */
    public String getLengthRange() {
        if (minLength == null && maxLength == null) {
            return "-";
        }
        String min = minLength != null ? minLength.toString() : "?";
        String max = maxLength != null ? maxLength.toString() : "?";
        return min + " - " + max + " mm";
    }

    /**
     * Gibt den Gewichtsbereich als String zurück
     */
    public String getWeightRange() {
        if (minWeight == null && maxWeight == null) {
            return "-";
        }
        String min = minWeight != null ? minWeight.toString() : "?";
        String max = maxWeight != null ? maxWeight.toString() : "?";
        return min + " - " + max + " kg";
    }

    @Override
    public String toString() {
        return name + " (" + (lengthType != null ? lengthType.getDisplayName() : "?") + ")";
    }
}
