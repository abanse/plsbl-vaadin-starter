package com.hydro.plsbl.entity.transdata;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Status eines Lagerplatzes - Bewegungsdaten
 *
 * Entspricht der Tabelle TD_STOCKYARDSTATUS
 */
@Table("TD_STOCKYARDSTATUS")
public class StockyardStatus {
    
    @Id
    private Long id;
    
    @Version
    @Column("SERIAL")
    private Long serial;
    
    @Column("STOCKYARD_ID")
    private Long stockyardId;
    
    @Column("PRODUCT_ID")
    private Long productId;
    
    @Column("INGOTS_COUNT")
    private int ingotsCount;
    
    @Column("NEIGHBOR_ID")
    private Long neighborId;

    @Column("YARD_USAGE")
    private String yardUsage;

    @Column("PILE_HEIGHT")
    private int pileHeight;

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
    
    public Long getStockyardId() {
        return stockyardId;
    }
    
    public void setStockyardId(Long stockyardId) {
        this.stockyardId = stockyardId;
    }
    
    public Long getProductId() {
        return productId;
    }
    
    public void setProductId(Long productId) {
        this.productId = productId;
    }
    
    public int getIngotsCount() {
        return ingotsCount;
    }
    
    public void setIngotsCount(int ingotsCount) {
        this.ingotsCount = ingotsCount;
    }
    
    public Long getNeighborId() {
        return neighborId;
    }
    
    public void setNeighborId(Long neighborId) {
        this.neighborId = neighborId;
    }

    public String getYardUsage() {
        return yardUsage;
    }

    public void setYardUsage(String yardUsage) {
        this.yardUsage = yardUsage;
    }

    public int getPileHeight() {
        return pileHeight;
    }

    public void setPileHeight(int pileHeight) {
        this.pileHeight = pileHeight;
    }

    // === Business Methods ===
    
    public boolean isEmpty() {
        return ingotsCount == 0;
    }
    
    @Override
    public String toString() {
        return "StockyardStatus{" +
                "stockyardId=" + stockyardId +
                ", ingotsCount=" + ingotsCount +
                '}';
    }
}
