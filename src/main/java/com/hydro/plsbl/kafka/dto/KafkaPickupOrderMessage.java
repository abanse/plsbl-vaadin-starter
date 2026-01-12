package com.hydro.plsbl.kafka.dto;

/**
 * Kafka-Nachricht fuer Abholauftraege (von der Saege)
 *
 * Entspricht dem bisherigen PICKUP_ORDER UMP-Message-Typ.
 * Die Saege meldet, dass ein Barren zum Einlagern bereit ist.
 */
public class KafkaPickupOrderMessage {

    private String ingotNumber;
    private String productNumber;
    private int length;      // mm
    private int width;       // mm
    private int height;      // mm
    private int weight;      // kg
    private boolean headSawn;
    private boolean footSawn;
    private boolean rotated;
    private String quality;
    private String targetStockyardNumber;  // Optional: gewuenschter Ziel-Lagerplatz

    // Constructors
    public KafkaPickupOrderMessage() {
    }

    // Getters and Setters
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

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public boolean isHeadSawn() {
        return headSawn;
    }

    public void setHeadSawn(boolean headSawn) {
        this.headSawn = headSawn;
    }

    public boolean isFootSawn() {
        return footSawn;
    }

    public void setFootSawn(boolean footSawn) {
        this.footSawn = footSawn;
    }

    public boolean isRotated() {
        return rotated;
    }

    public void setRotated(boolean rotated) {
        this.rotated = rotated;
    }

    public String getQuality() {
        return quality;
    }

    public void setQuality(String quality) {
        this.quality = quality;
    }

    public String getTargetStockyardNumber() {
        return targetStockyardNumber;
    }

    public void setTargetStockyardNumber(String targetStockyardNumber) {
        this.targetStockyardNumber = targetStockyardNumber;
    }

    @Override
    public String toString() {
        return "KafkaPickupOrderMessage{" +
            "ingotNumber='" + ingotNumber + '\'' +
            ", productNumber='" + productNumber + '\'' +
            ", weight=" + weight +
            '}';
    }
}
