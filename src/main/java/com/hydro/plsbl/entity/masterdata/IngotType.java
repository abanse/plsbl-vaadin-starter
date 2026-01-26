package com.hydro.plsbl.entity.masterdata;

import com.hydro.plsbl.entity.enums.LengthType;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Barrentyp - Stammdaten
 *
 * Definiert Kategorien von Barren basierend auf Länge, Gewicht und anderen Eigenschaften.
 * Entspricht der Tabelle MD_INGOTTYPE
 */
@Table("MD_INGOTTYPE")
public class IngotType {

    @Id
    private Long id;

    @Version
    @Column("SERIAL")
    private Long serial;

    @Column("NAME")
    private String name;

    @Column("DESCRIPTION")
    private String description;

    @Column("LENGTH_TYPE")
    private String lengthTypeCode;

    @Column("INTERNAL_ALLOWED")
    private Boolean internalAllowed;

    @Column("EXTERNAL_ALLOWED")
    private Boolean externalAllowed;

    @Column("RETRIEVAL_ALLOWED")
    private Boolean retrievalAllowed;

    @Column("AUTO_RETRIEVAL")
    private Boolean autoRetrieval;

    @Column("SAW_TO_SWAPOUT")
    private Boolean sawToSwapout;

    @Column("MIN_LENGTH")
    private Integer minLength;

    @Column("MAX_LENGTH")
    private Integer maxLength;

    @Column("MIN_WIDTH")
    private Integer minWidth;

    @Column("MAX_WIDTH")
    private Integer maxWidth;

    @Column("MIN_THICKNESS")
    private Integer minThickness;

    @Column("MAX_THICKNESS")
    private Integer maxThickness;

    @Column("MIN_WEIGHT")
    private Integer minWeight;

    @Column("MAX_WEIGHT")
    private Integer maxWeight;

    @Column("PRODUCT_REGEX")
    private String productRegex;

    @Column("PRIORITY")
    private Integer priority;

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

    public String getLengthTypeCode() {
        return lengthTypeCode;
    }

    public void setLengthTypeCode(String lengthTypeCode) {
        this.lengthTypeCode = lengthTypeCode;
    }

    public LengthType getLengthType() {
        return lengthTypeCode != null ? LengthType.fromCode(lengthTypeCode) : null;
    }

    public void setLengthType(LengthType lengthType) {
        this.lengthTypeCode = lengthType != null ? String.valueOf(lengthType.getCode()) : null;
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

    @Override
    public String toString() {
        return "IngotType{" +
                "name='" + name + '\'' +
                ", lengthType=" + getLengthType() +
                ", minLength=" + minLength +
                ", maxLength=" + maxLength +
                '}';
    }
}
