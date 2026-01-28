package com.hydro.plsbl.entity.transdata;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.annotation.Version;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Abruf - Kundenbestellung/Lieferabruf
 *
 * Entspricht der Tabelle TD_CALLOFF
 */
@Table("TD_CALLOFF")
public class Calloff implements Persistable<Long> {

    @Transient
    private boolean isNew = true;

    @Id
    private Long id;

    @Version
    @Column("SERIAL")
    private Long serial;

    // TABLESERIAL nicht in Oracle-Schema vorhanden
    @Transient
    private Long tableSerial;

    @Column("CALLOFF_NO")
    private String calloffNumber;

    @Column("ORDER_NO")
    private String orderNumber;

    @Column("ORDER_POS")
    private String orderPosition;

    @Column("CUSTOMER_NO")
    private String customerNumber;

    // CUSTOMER_NAME nicht in Oracle-Schema vorhanden
    @Transient
    private String customerName;

    @Column("CUSTOMER_ADDRESS")
    private String customerAddress;

    @Column("DESTINATION")
    private String destination;

    @Column("SAP_PRODUCT_NO")
    private String sapProductNumber;

    @Column("PRODUCT_ID")
    private Long productId;

    @Column("AMOUNT_REQUESTED")
    private Integer amountRequested;

    @Column("AMOUNT_DELIVERED")
    private Integer amountDelivered;

    @Column("DELIVERY")
    private LocalDate deliveryDate;

    @Column("APPROVED")
    private Boolean approved;

    @Column("COMPLETED")
    private Boolean completed;

    @Column("RECEIVED")
    private LocalDateTime received;

    @Column("NORMTEXT")
    private String notes;

    // === Berechnete Felder ===

    @Transient
    public int getRemainingAmount() {
        int requested = amountRequested != null ? amountRequested : 0;
        int delivered = amountDelivered != null ? amountDelivered : 0;
        return Math.max(0, requested - delivered);
    }

    @Transient
    public boolean isDeliverable() {
        return !Boolean.TRUE.equals(completed)
            && Boolean.TRUE.equals(approved)
            && getRemainingAmount() > 0;
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

    public Long getTableSerial() {
        return tableSerial;
    }

    public void setTableSerial(Long tableSerial) {
        this.tableSerial = tableSerial;
    }

    public String getCalloffNumber() {
        return calloffNumber;
    }

    public void setCalloffNumber(String calloffNumber) {
        this.calloffNumber = calloffNumber;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public String getOrderPosition() {
        return orderPosition;
    }

    public void setOrderPosition(String orderPosition) {
        this.orderPosition = orderPosition;
    }

    public String getCustomerNumber() {
        return customerNumber;
    }

    public void setCustomerNumber(String customerNumber) {
        this.customerNumber = customerNumber;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerAddress() {
        return customerAddress;
    }

    public void setCustomerAddress(String customerAddress) {
        this.customerAddress = customerAddress;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public String getSapProductNumber() {
        return sapProductNumber;
    }

    public void setSapProductNumber(String sapProductNumber) {
        this.sapProductNumber = sapProductNumber;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Integer getAmountRequested() {
        return amountRequested;
    }

    public void setAmountRequested(Integer amountRequested) {
        this.amountRequested = amountRequested;
    }

    public Integer getAmountDelivered() {
        return amountDelivered;
    }

    public void setAmountDelivered(Integer amountDelivered) {
        this.amountDelivered = amountDelivered;
    }

    public LocalDate getDeliveryDate() {
        return deliveryDate;
    }

    public void setDeliveryDate(LocalDate deliveryDate) {
        this.deliveryDate = deliveryDate;
    }

    public Boolean getApproved() {
        return approved;
    }

    public void setApproved(Boolean approved) {
        this.approved = approved;
    }

    public boolean isApproved() {
        return Boolean.TRUE.equals(approved);
    }

    public Boolean getCompleted() {
        return completed;
    }

    public void setCompleted(Boolean completed) {
        this.completed = completed;
    }

    public boolean isCompleted() {
        return Boolean.TRUE.equals(completed);
    }

    public LocalDateTime getReceived() {
        return received;
    }

    public void setReceived(LocalDateTime received) {
        this.received = received;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
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
        return "Calloff{" +
                "calloffNumber='" + calloffNumber + '\'' +
                ", orderNumber='" + orderNumber + '\'' +
                ", destination='" + destination + '\'' +
                ", remaining=" + getRemainingAmount() +
                '}';
    }
}
