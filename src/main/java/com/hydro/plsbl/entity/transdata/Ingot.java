package com.hydro.plsbl.entity.transdata;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.annotation.Version;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * Barren - Bewegungsdaten
 *
 * Entspricht der Tabelle TD_INGOT
 */
@Table("TD_INGOT")
public class Ingot implements Persistable<Long> {

    @Transient
    private boolean isNew = true;

    @Id
    private Long id;

    @Version
    @Column("SERIAL")
    private Long serial;

    @Column("INGOT_NO")
    private String ingotNo;

    @Column("PRODUCT_ID")
    private Long productId;

    @Column("PRODUCT_SUFFIX")
    private String productSuffix;

    @Column("STOCKYARD_ID")
    private Long stockyardId;

    @Column("PILE_POSITION")
    private Integer pilePosition;

    @Column("WEIGHT")
    private Integer weight;

    @Column("LENGTH")
    private Integer length;

    @Column("WIDTH")
    private Integer width;

    @Column("THICKNESS")
    private Integer thickness;

    @Column("HEAD_SAWN")
    private Boolean headSawn;

    @Column("FOOT_SAWN")
    private Boolean footSawn;

    @Column("SCRAP")
    private Boolean scrap;

    @Column("REVISED")
    private Boolean revised;

    @Column("ROTATED")
    private Boolean rotated;

    @Column("IN_STOCK_SINCE")
    private LocalDateTime inStockSince;

    @Column("RELEASED_SINCE")
    private LocalDateTime releasedSince;

    // Diese Spalten existieren nicht in der Oracle-Tabelle - als Transient markiert
    @Transient
    private Integer xPosition;

    @Transient
    private Integer yPosition;

    @Transient
    private Integer zPosition;

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

    // === Business Methods ===

    public boolean isOnStock() {
        return stockyardId != null;
    }

    public boolean isReleased() {
        return releasedSince != null;
    }

    // === Persistable Implementation ===

    @Override
    public boolean isNew() {
        return isNew;
    }

    public void markNotNew() {
        this.isNew = false;
    }

    @Override
    public String toString() {
        return "Ingot{" +
                "ingotNo='" + ingotNo + '\'' +
                ", stockyardId=" + stockyardId +
                ", pilePosition=" + pilePosition +
                '}';
    }
}
