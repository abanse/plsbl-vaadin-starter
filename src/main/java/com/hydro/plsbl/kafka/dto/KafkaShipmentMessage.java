package com.hydro.plsbl.kafka.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Kafka-Nachricht fuer Lieferungs-Ereignisse (an SAP)
 *
 * Meldet abgeschlossene Lieferungen mit allen enthaltenen Barren.
 */
public class KafkaShipmentMessage {

    private String shipmentNumber;
    private String calloffNumber;
    private String customerNumber;
    private String transportNumber;
    private LocalDateTime completedAt;
    private int totalIngots;
    private int totalWeight;  // kg
    private List<ShipmentIngot> ingots = new ArrayList<>();

    // Constructors
    public KafkaShipmentMessage() {
        this.completedAt = LocalDateTime.now();
    }

    // Inner Class for Ingot Details
    public static class ShipmentIngot {
        private String ingotNumber;
        private String productNumber;
        private int weight;

        public ShipmentIngot() {
        }

        public ShipmentIngot(String ingotNumber, String productNumber, int weight) {
            this.ingotNumber = ingotNumber;
            this.productNumber = productNumber;
            this.weight = weight;
        }

        public String getIngotNumber() {
            return ingotNumber;
        }

        public void setIngotNumber(String ingotNumber) {
            this.ingotNumber = ingotNumber;
        }

        public String getProductNumber() {
            return productNumber;
        }

        public void setProductNumber(String productNumber) {
            this.productNumber = productNumber;
        }

        public int getWeight() {
            return weight;
        }

        public void setWeight(int weight) {
            this.weight = weight;
        }
    }

    // Helper Methods
    public void addIngot(String ingotNumber, String productNumber, int weight) {
        ingots.add(new ShipmentIngot(ingotNumber, productNumber, weight));
        totalIngots = ingots.size();
        totalWeight = ingots.stream().mapToInt(ShipmentIngot::getWeight).sum();
    }

    // Getters and Setters
    public String getShipmentNumber() {
        return shipmentNumber;
    }

    public void setShipmentNumber(String shipmentNumber) {
        this.shipmentNumber = shipmentNumber;
    }

    public String getCalloffNumber() {
        return calloffNumber;
    }

    public void setCalloffNumber(String calloffNumber) {
        this.calloffNumber = calloffNumber;
    }

    public String getCustomerNumber() {
        return customerNumber;
    }

    public void setCustomerNumber(String customerNumber) {
        this.customerNumber = customerNumber;
    }

    public String getTransportNumber() {
        return transportNumber;
    }

    public void setTransportNumber(String transportNumber) {
        this.transportNumber = transportNumber;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public int getTotalIngots() {
        return totalIngots;
    }

    public void setTotalIngots(int totalIngots) {
        this.totalIngots = totalIngots;
    }

    public int getTotalWeight() {
        return totalWeight;
    }

    public void setTotalWeight(int totalWeight) {
        this.totalWeight = totalWeight;
    }

    public List<ShipmentIngot> getIngots() {
        return ingots;
    }

    public void setIngots(List<ShipmentIngot> ingots) {
        this.ingots = ingots;
    }

    @Override
    public String toString() {
        return "KafkaShipmentMessage{" +
            "shipmentNumber='" + shipmentNumber + '\'' +
            ", totalIngots=" + totalIngots +
            ", totalWeight=" + totalWeight +
            '}';
    }
}
