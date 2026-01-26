package com.hydro.plsbl.entity.transdata;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.annotation.Version;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * Lieferschein (Shipment/Delivery Note)
 *
 * Entspricht der Tabelle TD_SHIPMENT
 */
@Table("TD_SHIPMENT")
public class Shipment implements Persistable<Long> {

    @Transient
    private boolean isNew = true;

    @Id
    private Long id;

    @Version
    @Column("SERIAL")
    private Long serial;

    @Column("SHIPMENT_NO")
    private String shipmentNumber;

    @Column("ORDER_NO")
    private String orderNumber;

    @Column("DESTINATION")
    private String destination;

    @Column("CUSTOMER_NO")
    private String customerNumber;

    @Column("ADDRESS")
    private String address;

    @Column("PRINTED")
    private LocalDateTime printed;

    @Column("DELIVERED")
    private LocalDateTime delivered;

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

    public String getShipmentNumber() {
        return shipmentNumber;
    }

    public void setShipmentNumber(String shipmentNumber) {
        this.shipmentNumber = shipmentNumber;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public String getCustomerNumber() {
        return customerNumber;
    }

    public void setCustomerNumber(String customerNumber) {
        this.customerNumber = customerNumber;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public LocalDateTime getPrinted() {
        return printed;
    }

    public void setPrinted(LocalDateTime printed) {
        this.printed = printed;
    }

    public LocalDateTime getDelivered() {
        return delivered;
    }

    public void setDelivered(LocalDateTime delivered) {
        this.delivered = delivered;
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
        return "Shipment{" +
                "shipmentNumber='" + shipmentNumber + '\'' +
                ", destination='" + destination + '\'' +
                ", customerNumber='" + customerNumber + '\'' +
                '}';
    }
}
