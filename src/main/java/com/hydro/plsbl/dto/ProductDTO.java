package com.hydro.plsbl.dto;

/**
 * DTO für Produkt/Artikel
 */
public class ProductDTO {

    private Long id;
    private Long serial;
    private String productNo;
    private String description;
    private Integer maxPerLocation;

    public ProductDTO() {
    }

    public ProductDTO(Long id, String productNo, String description, Integer maxPerLocation) {
        this.id = id;
        this.productNo = productNo;
        this.description = description;
        this.maxPerLocation = maxPerLocation;
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

    public String getProductNo() {
        return productNo;
    }

    public void setProductNo(String productNo) {
        this.productNo = productNo;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getMaxPerLocation() {
        return maxPerLocation;
    }

    public void setMaxPerLocation(Integer maxPerLocation) {
        this.maxPerLocation = maxPerLocation;
    }

    @Override
    public String toString() {
        return productNo + (description != null ? " - " + description : "");
    }
}
