package com.hydro.plsbl.entity.masterdata;

import com.hydro.plsbl.entity.enums.StockyardType;
import com.hydro.plsbl.entity.enums.StockyardUsage;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.annotation.Version;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Lagerplatz (Stockyard) - Stammdaten
 *
 * Entspricht der Tabelle MD_STOCKYARD
 */
@Table("MD_STOCKYARD")
public class Stockyard implements Persistable<Long> {

    @Transient
    private boolean isNew = true;

    @Id
    private Long id;
    
    @Version
    @Column("SERIAL")
    private Long serial;
    
    @Column("YARD_NO")
    private String yardNumber;
    
    @Column("X_COORDINATE")
    private int xCoordinate;
    
    @Column("Y_COORDINATE")
    private int yCoordinate;
    
    @Column("DESCRIPTION")
    private String description;
    
    @Column("YARD_TYPE")
    private String typeCode;
    
    @Column("YARD_USAGE")
    private String usageCode;
    
    // Diese Spalten existieren nicht in der Oracle-Tabelle
    @Transient
    private int xPosition;

    @Transient
    private int yPosition;

    @Transient
    private int zPosition;
    
    @Column("LENGTH")
    private int length;
    
    @Column("WIDTH")
    private int width;
    
    @Column("HEIGHT")
    private int height;
    
    @Column("MAX_INGOTS")
    private int maxIngots;
    
    @Column("TO_STOCK_ALLOWED")
    private boolean toStockAllowed;
    
    @Column("FROM_STOCK_ALLOWED")
    private boolean fromStockAllowed;
    
    // Diese Spalte existiert möglicherweise nicht in der Oracle-Tabelle
    @Transient
    private int movementCoefficient;
    
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
        return typeCode != null ? StockyardType.fromCode(typeCode) : null;
    }
    
    public void setType(StockyardType type) {
        this.typeCode = type != null ? String.valueOf(type.getCode()) : null;
    }
    
    public StockyardUsage getUsage() {
        return usageCode != null ? StockyardUsage.fromCode(usageCode) : null;
    }
    
    public void setUsage(StockyardUsage usage) {
        this.usageCode = usage != null ? String.valueOf(usage.getCode()) : null;
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
    
    public int getMovementCoefficient() {
        return movementCoefficient;
    }
    
    public void setMovementCoefficient(int movementCoefficient) {
        this.movementCoefficient = movementCoefficient;
    }
    
    // === Business Methods ===
    
    /**
     * Wird dieser Lagerplatz in der StockView angezeigt?
     */
    public boolean isShownInStockView() {
        StockyardType type = getType();
        return type != null && type.isShownInStockView();
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
        return "Stockyard{" +
                "yardNumber='" + yardNumber + '\'' +
                ", coordinates=(" + xCoordinate + "," + yCoordinate + ")" +
                ", type=" + getType() +
                '}';
    }
}
