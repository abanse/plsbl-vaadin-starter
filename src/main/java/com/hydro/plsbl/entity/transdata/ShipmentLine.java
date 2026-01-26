package com.hydro.plsbl.entity.transdata;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.annotation.Version;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Lieferschein-Position (Shipment Line)
 *
 * Entspricht der Tabelle TD_SHIPMENTLINE
 */
@Table("TD_SHIPMENTLINE")
public class ShipmentLine implements Persistable<Long> {

    @Transient
    private boolean isNew = true;

    @Id
    private Long id;

    @Version
    @Column("SERIAL")
    private Long serial;

    @Column("SHIPMENT_ID")
    private Long shipmentId;

    @Column("SHIPMENT_POS")
    private Integer position;

    @Column("INGOT_NO")
    private String ingotNumber;

    @Column("INGOT_ID")
    private Long ingotId;

    @Column("INGOT_COMMENT")
    private String comment;

    @Column("WEIGHT")
    private Integer weight;

    @Column("LENGTH")
    private Integer length;

    @Column("THICKNESS")
    private Integer thickness;

    @Column("WIDTH")
    private Integer width;

    @Column("HEAD_SAWN")
    private Boolean headSawn;

    @Column("FOOT_SAWN")
    private Boolean footSawn;

    @Column("SCRAP")
    private Boolean scrap;

    @Column("REVISED")
    private Boolean revised;

    @Column("PRODUCT_NO")
    private String productNumber;

    @Column("SAP_PRODUCT_NO")
    private String sapProductNumber;

    @Column("CALLOFF_NO")
    private String calloffNumber;

    @Column("ORDER_POS")
    private String orderPosition;

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

    public Long getShipmentId() {
        return shipmentId;
    }

    public void setShipmentId(Long shipmentId) {
        this.shipmentId = shipmentId;
    }

    public Integer getPosition() {
        return position;
    }

    public void setPosition(Integer position) {
        this.position = position;
    }

    public String getIngotNumber() {
        return ingotNumber;
    }

    public void setIngotNumber(String ingotNumber) {
        this.ingotNumber = ingotNumber;
    }

    public Long getIngotId() {
        return ingotId;
    }

    public void setIngotId(Long ingotId) {
        this.ingotId = ingotId;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
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

    public Integer getThickness() {
        return thickness;
    }

    public void setThickness(Integer thickness) {
        this.thickness = thickness;
    }

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
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

    public String getProductNumber() {
        return productNumber;
    }

    public void setProductNumber(String productNumber) {
        this.productNumber = productNumber;
    }

    public String getSapProductNumber() {
        return sapProductNumber;
    }

    public void setSapProductNumber(String sapProductNumber) {
        this.sapProductNumber = sapProductNumber;
    }

    public String getCalloffNumber() {
        return calloffNumber;
    }

    public void setCalloffNumber(String calloffNumber) {
        this.calloffNumber = calloffNumber;
    }

    public String getOrderPosition() {
        return orderPosition;
    }

    public void setOrderPosition(String orderPosition) {
        this.orderPosition = orderPosition;
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
        return "ShipmentLine{" +
                "position=" + position +
                ", ingotNumber='" + ingotNumber + '\'' +
                ", weight=" + weight +
                '}';
    }
}
