package com.hydro.plsbl.dto;

import com.hydro.plsbl.entity.transdata.Calloff;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO für Abruf-Daten (Calloff)
 */
public class CalloffDTO {

    private Long id;
    private Long tableSerial;
    private String calloffNumber;
    private String orderNumber;
    private String orderPosition;
    private String customerNumber;
    private String customerName;
    private String customerAddress;
    private String destination;
    private String sapProductNumber;
    private Long productId;
    private Integer amountRequested;
    private Integer amountDelivered;
    private LocalDate deliveryDate;
    private Boolean approved;
    private Boolean completed;
    private LocalDateTime received;
    private String notes;

    // === Constructors ===

    public CalloffDTO() {
    }

    public CalloffDTO(Calloff entity) {
        if (entity != null) {
            this.id = entity.getId();
            this.tableSerial = entity.getTableSerial();
            this.calloffNumber = entity.getCalloffNumber();
            this.orderNumber = entity.getOrderNumber();
            this.orderPosition = entity.getOrderPosition();
            this.customerNumber = entity.getCustomerNumber();
            this.customerName = entity.getCustomerName();
            this.customerAddress = entity.getCustomerAddress();
            this.destination = entity.getDestination();
            this.sapProductNumber = entity.getSapProductNumber();
            this.productId = entity.getProductId();
            this.amountRequested = entity.getAmountRequested();
            this.amountDelivered = entity.getAmountDelivered();
            this.deliveryDate = entity.getDeliveryDate();
            this.approved = entity.getApproved();
            this.completed = entity.getCompleted();
            this.received = entity.getReceived();
            this.notes = entity.getNotes();
        }
    }

    // === Berechnete Felder ===

    public int getRemainingAmount() {
        int requested = amountRequested != null ? amountRequested : 0;
        int delivered = amountDelivered != null ? amountDelivered : 0;
        return Math.max(0, requested - delivered);
    }

    public boolean isDeliverable() {
        return !Boolean.TRUE.equals(completed)
            && Boolean.TRUE.equals(approved)
            && getRemainingAmount() > 0;
    }

    public String getOrderDisplay() {
        StringBuilder sb = new StringBuilder();
        if (orderNumber != null) {
            sb.append(orderNumber);
        }
        if (orderPosition != null && !orderPosition.isEmpty()) {
            sb.append("/").append(orderPosition);
        }
        return sb.toString();
    }

    public String getStatusDisplay() {
        if (Boolean.TRUE.equals(completed)) {
            return "Abgeschlossen";
        } else if (Boolean.TRUE.equals(approved)) {
            return "Genehmigt";
        } else {
            return "Offen";
        }
    }

    // === Getters & Setters ===

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    @Override
    public String toString() {
        return "CalloffDTO{" +
                "calloffNumber='" + calloffNumber + '\'' +
                ", destination='" + destination + '\'' +
                ", remaining=" + getRemainingAmount() +
                '}';
    }
}
