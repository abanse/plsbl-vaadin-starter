package com.hydro.plsbl.kafka.dto;

import java.time.LocalDateTime;

/**
 * Kafka-Nachricht fuer Abruf-Telegramme (von SAP)
 *
 * Entspricht dem bisherigen CALLOFF UMP-Message-Typ.
 */
public class KafkaCalloffMessage {

    private String calloffNumber;
    private String productNumber;
    private String customerNumber;
    private String customerName;
    private int quantity;
    private LocalDateTime deliveryDate;
    private String priority;
    private String notes;

    // Constructors
    public KafkaCalloffMessage() {
    }

    // Getters and Setters
    public String getCalloffNumber() {
        return calloffNumber;
    }

    public void setCalloffNumber(String calloffNumber) {
        this.calloffNumber = calloffNumber;
    }

    public String getProductNumber() {
        return productNumber;
    }

    public void setProductNumber(String productNumber) {
        this.productNumber = productNumber;
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

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public LocalDateTime getDeliveryDate() {
        return deliveryDate;
    }

    public void setDeliveryDate(LocalDateTime deliveryDate) {
        this.deliveryDate = deliveryDate;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    @Override
    public String toString() {
        return "KafkaCalloffMessage{" +
            "calloffNumber='" + calloffNumber + '\'' +
            ", productNumber='" + productNumber + '\'' +
            ", quantity=" + quantity +
            '}';
    }
}
