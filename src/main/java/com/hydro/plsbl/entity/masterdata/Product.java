package com.hydro.plsbl.entity.masterdata;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Produkt/Artikel - Stammdaten
 *
 * Entspricht der Tabelle MD_PRODUCT
 */
@Table("MD_PRODUCT")
public class Product {

    @Id
    private Long id;

    @Version
    @Column("SERIAL")
    private Long serial;

    @Column("PRODUCT_NO")
    private String productNo;

    @Column("DESCRIPTION")
    private String description;

    @Column("MAX_PER_LOCATION")
    private Integer maxPerLocation;

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
        return "Product{" +
                "productNo='" + productNo + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}
