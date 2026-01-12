package com.hydro.plsbl.kafka.dto;

import java.time.LocalDateTime;

/**
 * Kafka-Nachricht fuer Barren-Ereignisse (an SAP)
 *
 * Wird verwendet fuer:
 * - INGOT_PICKED_UP: Barren vom Kran aufgenommen
 * - INGOT_MOVED: Barren umgelagert
 * - INGOT_MODIFIED: Barren-Eigenschaften geaendert
 */
public class KafkaIngotEventMessage {

    public enum EventType {
        PICKED_UP,
        MOVED,
        MODIFIED
    }

    private EventType eventType;
    private String ingotNumber;
    private String productNumber;
    private String fromStockyardNumber;
    private String toStockyardNumber;
    private int pilePosition;
    private LocalDateTime timestamp;
    // Modifikationen
    private Boolean headSawn;
    private Boolean footSawn;
    private Boolean rotated;
    private Boolean revised;
    private Boolean scrapped;
    private String notes;

    // Constructors
    public KafkaIngotEventMessage() {
        this.timestamp = LocalDateTime.now();
    }

    public KafkaIngotEventMessage(EventType eventType, String ingotNumber) {
        this();
        this.eventType = eventType;
        this.ingotNumber = ingotNumber;
    }

    // Getters and Setters
    public EventType getEventType() {
        return eventType;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
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

    public String getFromStockyardNumber() {
        return fromStockyardNumber;
    }

    public void setFromStockyardNumber(String fromStockyardNumber) {
        this.fromStockyardNumber = fromStockyardNumber;
    }

    public String getToStockyardNumber() {
        return toStockyardNumber;
    }

    public void setToStockyardNumber(String toStockyardNumber) {
        this.toStockyardNumber = toStockyardNumber;
    }

    public int getPilePosition() {
        return pilePosition;
    }

    public void setPilePosition(int pilePosition) {
        this.pilePosition = pilePosition;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
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

    public Boolean getRotated() {
        return rotated;
    }

    public void setRotated(Boolean rotated) {
        this.rotated = rotated;
    }

    public Boolean getRevised() {
        return revised;
    }

    public void setRevised(Boolean revised) {
        this.revised = revised;
    }

    public Boolean getScrapped() {
        return scrapped;
    }

    public void setScrapped(Boolean scrapped) {
        this.scrapped = scrapped;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    @Override
    public String toString() {
        return "KafkaIngotEventMessage{" +
            "eventType=" + eventType +
            ", ingotNumber='" + ingotNumber + '\'' +
            ", timestamp=" + timestamp +
            '}';
    }
}
