package com.hydro.plsbl.dto;

import com.hydro.plsbl.entity.enums.StockyardType;
import com.hydro.plsbl.entity.enums.StockyardUsage;

/**
 * Data Transfer Object für Stockyard (Lagerplatz)
 * 
 * Enthält alle Daten die für die UI benötigt werden,
 * inklusive Status-Informationen.
 */
public class StockyardDTO {
    
    private Long id;
    private String yardNumber;
    private int xCoordinate;
    private int yCoordinate;
    private String description;
    private StockyardType type;
    private StockyardUsage usage;
    private int xPosition;
    private int yPosition;
    private int zPosition;
    private int length;
    private int width;
    private int height;
    private int maxIngots;
    private boolean toStockAllowed;
    private boolean fromStockAllowed;
    
    // Status-Daten (aus StockyardStatus)
    private StockyardStatusDTO status;
    
    // === Getters & Setters ===
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getYardNumber() {
        return yardNumber;
    }
    
    public void setYardNumber(String yardNumber) {
        this.yardNumber = yardNumber;
    }
    
    public int getXCoordinate() {
        return xCoordinate;
    }
    
    public void setXCoordinate(int xCoordinate) {
        this.xCoordinate = xCoordinate;
    }
    
    public int getYCoordinate() {
        return yCoordinate;
    }
    
    public void setYCoordinate(int yCoordinate) {
        this.yCoordinate = yCoordinate;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public StockyardType getType() {
        return type;
    }
    
    public void setType(StockyardType type) {
        this.type = type;
    }
    
    public StockyardUsage getUsage() {
        return usage;
    }
    
    public void setUsage(StockyardUsage usage) {
        this.usage = usage;
    }
    
    public int getXPosition() {
        return xPosition;
    }
    
    public void setXPosition(int xPosition) {
        this.xPosition = xPosition;
    }
    
    public int getYPosition() {
        return yPosition;
    }
    
    public void setYPosition(int yPosition) {
        this.yPosition = yPosition;
    }
    
    public int getZPosition() {
        return zPosition;
    }
    
    public void setZPosition(int zPosition) {
        this.zPosition = zPosition;
    }
    
    public int getLength() {
        return length;
    }
    
    public void setLength(int length) {
        this.length = length;
    }
    
    public int getWidth() {
        return width;
    }
    
    public void setWidth(int width) {
        this.width = width;
    }
    
    public int getHeight() {
        return height;
    }
    
    public void setHeight(int height) {
        this.height = height;
    }
    
    public int getMaxIngots() {
        return maxIngots;
    }
    
    public void setMaxIngots(int maxIngots) {
        this.maxIngots = maxIngots;
    }
    
    public boolean isToStockAllowed() {
        return toStockAllowed;
    }
    
    public void setToStockAllowed(boolean toStockAllowed) {
        this.toStockAllowed = toStockAllowed;
    }
    
    public boolean isFromStockAllowed() {
        return fromStockAllowed;
    }
    
    public void setFromStockAllowed(boolean fromStockAllowed) {
        this.fromStockAllowed = fromStockAllowed;
    }
    
    public StockyardStatusDTO getStatus() {
        return status;
    }
    
    public void setStatus(StockyardStatusDTO status) {
        this.status = status;
    }
    
    // === Convenience Methods ===
    
    /**
     * Liefert das Label für die Button-Anzeige
     */
    public String getDisplayLabel() {
        if (status == null || status.isEmpty()) {
            // Leer - zeige Verwendungstyp
            return usage != null ? usage.getEmptyLabel() : "-";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append(status.getIngotsCount());
        
        // Zusätzliche Markierungen
        if (status.isRevisedOnTop()) {
            sb.append("k");  // korrigiert
        }
        if (status.isScrapOnTop()) {
            sb.append("s");  // Schrott
        }
        
        return sb.toString();
    }
    
    /**
     * Ist der Platz voll?
     */
    public boolean isFull() {
        return status != null && status.getIngotsCount() >= maxIngots;
    }
    
    /**
     * Ist der Platz leer?
     */
    public boolean isEmpty() {
        return status == null || status.isEmpty();
    }
    
    /**
     * Ist der Platz gesperrt (weder Ein- noch Auslagern erlaubt)?
     */
    public boolean isLocked() {
        return !toStockAllowed && !fromStockAllowed;
    }
    
    @Override
    public String toString() {
        return "StockyardDTO{" +
                "yardNumber='" + yardNumber + '\'' +
                ", coords=(" + xCoordinate + "," + yCoordinate + ")" +
                ", type=" + type +
                ", ingots=" + (status != null ? status.getIngotsCount() : 0) +
                '}';
    }
}
