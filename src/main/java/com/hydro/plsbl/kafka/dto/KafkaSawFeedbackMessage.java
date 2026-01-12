package com.hydro.plsbl.kafka.dto;

import java.time.LocalDateTime;

/**
 * Kafka-Nachricht fuer Rueckmeldungen an die Saege
 *
 * Meldet der Saege, dass ein Barren erfolgreich eingelagert wurde
 * oder eine Aktion ausgefuehrt wurde.
 */
public class KafkaSawFeedbackMessage {

    public enum FeedbackType {
        INGOT_STORED,       // Barren erfolgreich eingelagert
        PICKUP_CONFIRMED,   // Abholung bestaetigt
        PICKUP_FAILED,      // Abholung fehlgeschlagen
        READY_FOR_NEXT      // Bereit fuer naechsten Barren
    }

    private FeedbackType feedbackType;
    private String ingotNumber;
    private String stockyardNumber;
    private int pilePosition;
    private boolean success;
    private String errorMessage;
    private LocalDateTime timestamp;

    // Constructors
    public KafkaSawFeedbackMessage() {
        this.timestamp = LocalDateTime.now();
        this.success = true;
    }

    public KafkaSawFeedbackMessage(FeedbackType feedbackType, String ingotNumber, boolean success) {
        this();
        this.feedbackType = feedbackType;
        this.ingotNumber = ingotNumber;
        this.success = success;
    }

    // Factory Methods
    public static KafkaSawFeedbackMessage ingotStored(String ingotNumber, String stockyardNumber, int pilePosition) {
        KafkaSawFeedbackMessage msg = new KafkaSawFeedbackMessage(FeedbackType.INGOT_STORED, ingotNumber, true);
        msg.setStockyardNumber(stockyardNumber);
        msg.setPilePosition(pilePosition);
        return msg;
    }

    public static KafkaSawFeedbackMessage pickupConfirmed(String ingotNumber) {
        return new KafkaSawFeedbackMessage(FeedbackType.PICKUP_CONFIRMED, ingotNumber, true);
    }

    public static KafkaSawFeedbackMessage pickupFailed(String ingotNumber, String errorMessage) {
        KafkaSawFeedbackMessage msg = new KafkaSawFeedbackMessage(FeedbackType.PICKUP_FAILED, ingotNumber, false);
        msg.setErrorMessage(errorMessage);
        return msg;
    }

    public static KafkaSawFeedbackMessage readyForNext() {
        return new KafkaSawFeedbackMessage(FeedbackType.READY_FOR_NEXT, null, true);
    }

    // Getters and Setters
    public FeedbackType getFeedbackType() {
        return feedbackType;
    }

    public void setFeedbackType(FeedbackType feedbackType) {
        this.feedbackType = feedbackType;
    }

    public String getIngotNumber() {
        return ingotNumber;
    }

    public void setIngotNumber(String ingotNumber) {
        this.ingotNumber = ingotNumber;
    }

    public String getStockyardNumber() {
        return stockyardNumber;
    }

    public void setStockyardNumber(String stockyardNumber) {
        this.stockyardNumber = stockyardNumber;
    }

    public int getPilePosition() {
        return pilePosition;
    }

    public void setPilePosition(int pilePosition) {
        this.pilePosition = pilePosition;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "KafkaSawFeedbackMessage{" +
            "feedbackType=" + feedbackType +
            ", ingotNumber='" + ingotNumber + '\'' +
            ", success=" + success +
            '}';
    }
}
