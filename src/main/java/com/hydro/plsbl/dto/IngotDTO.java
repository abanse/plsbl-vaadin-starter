package com.hydro.plsbl.dto;

import java.time.LocalDateTime;

/**
 * DTO für Barren-Daten
 */
public class IngotDTO {

    private Long id;
    private String ingotNo;
    private Long productId;
    private String productNo;
    private String productSuffix;
    private Long stockyardId;
    private String stockyardNo;
    private Integer pilePosition;
    private Integer weight;
    private Integer length;
    private Integer width;
    private Integer thickness;
    private Boolean headSawn;
    private Boolean footSawn;
    private Boolean scrap;
    private Boolean revised;
    private Boolean rotated;
    private LocalDateTime inStockSince;
    private LocalDateTime releasedSince;

    // === Getters & Setters ===

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getIngotNo() {
        return ingotNo;
    }

    public void setIngotNo(String ingotNo) {
        this.ingotNo = ingotNo;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
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

    public Long getStockyardId() {
        return stockyardId;
    }

    public void setStockyardId(Long stockyardId) {
        this.stockyardId = stockyardId;
    }

    public String getStockyardNo() {
        return stockyardNo;
    }

    public void setStockyardNo(String stockyardNo) {
        this.stockyardNo = stockyardNo;
    }

    public Integer getPilePosition() {
        return pilePosition;
    }

    public void setPilePosition(Integer pilePosition) {
        this.pilePosition = pilePosition;
    }

    public Integer getWeight() {
        return weight;
    }

    public void setWeight(Integer weight) {
        this.weight = weight;
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

    public Boolean getHeadSawn() {
        return headSawn;
    }

    public void setHeadSawn(Boolean headSawn) {
        this.headSawn = headSawn;
    }

    public Boolean getFootSawn() {
        return footSawn;
    }

    public void setFootSawn(Boolean footSawn) {
        this.footSawn = footSawn;
    }

    public Boolean getScrap() {
        return scrap;
    }

    public void setScrap(Boolean scrap) {
        this.scrap = scrap;
    }

    public Boolean getRevised() {
        return revised;
    }

    public void setRevised(Boolean revised) {
        this.revised = revised;
    }

    public Boolean getRotated() {
        return rotated;
    }

    public void setRotated(Boolean rotated) {
        this.rotated = rotated;
    }

    public LocalDateTime getInStockSince() {
        return inStockSince;
    }

    public void setInStockSince(LocalDateTime inStockSince) {
        this.inStockSince = inStockSince;
    }

    public LocalDateTime getReleasedSince() {
        return releasedSince;
    }

    public void setReleasedSince(LocalDateTime releasedSince) {
        this.releasedSince = releasedSince;
    }

    // === Business Methods ===

    public boolean isReleased() {
        return releasedSince != null;
    }

    public String getDisplayName() {
        return ingotNo + (productSuffix != null ? "-" + productSuffix : "");
    }
}
