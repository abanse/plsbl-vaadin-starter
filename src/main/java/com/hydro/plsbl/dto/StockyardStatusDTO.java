package com.hydro.plsbl.dto;

/**
 * Data Transfer Object für StockyardStatus
 */
public class StockyardStatusDTO {
    
    private Long id;
    private Long stockyardId;
    private Long productId;
    private String productNumber;
    private int ingotsCount;
    private Long neighborId;
    
    // Berechnete Felder
    private boolean empty;
    private boolean full;
    private boolean revisedOnTop;
    private boolean scrapOnTop;
    
    // === Getters & Setters ===
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
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
    
    public String getProductNumber() {
        return productNumber;
    }
    
    public void setProductNumber(String productNumber) {
        this.productNumber = productNumber;
    }
    
    public int getIngotsCount() {
        return ingotsCount;
    }
    
    public void setIngotsCount(int ingotsCount) {
        this.ingotsCount = ingotsCount;
        this.empty = (ingotsCount == 0);
    }
    
    public Long getNeighborId() {
        return neighborId;
    }
    
    public void setNeighborId(Long neighborId) {
        this.neighborId = neighborId;
    }
    
    public boolean isEmpty() {
        return empty;
    }
    
    public void setEmpty(boolean empty) {
        this.empty = empty;
    }
    
    public boolean isFull() {
        return full;
    }
    
    public void setFull(boolean full) {
        this.full = full;
    }
    
    public boolean isRevisedOnTop() {
        return revisedOnTop;
    }
    
    public void setRevisedOnTop(boolean revisedOnTop) {
        this.revisedOnTop = revisedOnTop;
    }
    
    public boolean isScrapOnTop() {
        return scrapOnTop;
    }
    
    public void setScrapOnTop(boolean scrapOnTop) {
        this.scrapOnTop = scrapOnTop;
    }
    
    @Override
    public String toString() {
        return "StockyardStatusDTO{" +
                "stockyardId=" + stockyardId +
                ", ingotsCount=" + ingotsCount +
                ", empty=" + empty +
                ", full=" + full +
                '}';
    }
}
