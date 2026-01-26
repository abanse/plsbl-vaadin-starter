package com.hydro.plsbl.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO für Lieferschein-Daten
 */
public class DeliveryNoteDTO {

    private Long id;
    private String deliveryNoteNumber;
    private LocalDateTime createdAt;

    // Abruf-Daten
    private String calloffNumber;
    private String orderNumber;
    private String orderPosition;

    // Kunden-Daten
    private String customerNumber;
    private String customerName;
    private String customerAddress;
    private String destination;

    // Produkt
    private String sapProductNumber;
    private String productName;

    // Gelieferte Barren
    private List<IngotDTO> deliveredIngots = new ArrayList<>();

    // Summen
    private int totalCount;
    private int totalWeight;

    // === Getters & Setters ===

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDeliveryNoteNumber() {
        return deliveryNoteNumber;
    }

    public void setDeliveryNoteNumber(String deliveryNoteNumber) {
        this.deliveryNoteNumber = deliveryNoteNumber;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
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

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public List<IngotDTO> getDeliveredIngots() {
        return deliveredIngots;
    }

    public void setDeliveredIngots(List<IngotDTO> deliveredIngots) {
        this.deliveredIngots = deliveredIngots;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public int getTotalWeight() {
        return totalWeight;
    }

    public void setTotalWeight(int totalWeight) {
        this.totalWeight = totalWeight;
    }

    // === Helper Methods ===

    public void calculateTotals() {
        this.totalCount = deliveredIngots.size();
        this.totalWeight = deliveredIngots.stream()
            .mapToInt(i -> i.getWeight() != null ? i.getWeight() : 0)
            .sum();
    }

    public String getOrderDisplay() {
        if (orderNumber == null) return "-";
        if (orderPosition != null && !orderPosition.isEmpty()) {
            return orderNumber + "/" + orderPosition;
        }
        return orderNumber;
    }
}
