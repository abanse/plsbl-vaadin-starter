package com.hydro.plsbl.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Suchkriterien für Abrufe (Calloff) - wie in der Original-Applikation
 */
public class CalloffSearchCriteria {

    private String calloffNumber;
    private String orderNumber;
    private String orderPosition;
    private String customerNumber;
    private String destination;
    private boolean noDestination;
    private String sapProductNumber;
    private String productNumber;  // Artikel
    private String searchPattern;  // Suchmuster
    private LocalDateTime deliveryDateFrom;
    private LocalDateTime deliveryDateTo;
    private boolean incompleteOnly = true;  // nur offene Abrufe
    private boolean completedOnly;          // nur erledigte Abrufe
    private boolean approvedOnly;           // nur genehmigte Abrufe (für BeladungView)
    private boolean notApprovedOnly;        // nur gesperrte Abrufe

    // === Getters & Setters ===

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

    public String getCustomerNumber() {
        return customerNumber;
    }

    public void setCustomerNumber(String customerNumber) {
        this.customerNumber = customerNumber;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public String getOrderPosition() {
        return orderPosition;
    }

    public void setOrderPosition(String orderPosition) {
        this.orderPosition = orderPosition;
    }

    public String getSapProductNumber() {
        return sapProductNumber;
    }

    public void setSapProductNumber(String sapProductNumber) {
        this.sapProductNumber = sapProductNumber;
    }

    public String getProductNumber() {
        return productNumber;
    }

    public void setProductNumber(String productNumber) {
        this.productNumber = productNumber;
    }

    public String getSearchPattern() {
        return searchPattern;
    }

    public void setSearchPattern(String searchPattern) {
        this.searchPattern = searchPattern;
    }

    public LocalDateTime getDeliveryDateFrom() {
        return deliveryDateFrom;
    }

    public void setDeliveryDateFrom(LocalDateTime deliveryDateFrom) {
        this.deliveryDateFrom = deliveryDateFrom;
    }

    public LocalDateTime getDeliveryDateTo() {
        return deliveryDateTo;
    }

    public void setDeliveryDateTo(LocalDateTime deliveryDateTo) {
        this.deliveryDateTo = deliveryDateTo;
    }

    public boolean isNoDestination() {
        return noDestination;
    }

    public void setNoDestination(boolean noDestination) {
        this.noDestination = noDestination;
    }

    public boolean isIncompleteOnly() {
        return incompleteOnly;
    }

    public void setIncompleteOnly(boolean incompleteOnly) {
        this.incompleteOnly = incompleteOnly;
    }

    public boolean isCompletedOnly() {
        return completedOnly;
    }

    public void setCompletedOnly(boolean completedOnly) {
        this.completedOnly = completedOnly;
    }

    public boolean isApprovedOnly() {
        return approvedOnly;
    }

    public void setApprovedOnly(boolean approvedOnly) {
        this.approvedOnly = approvedOnly;
    }

    public boolean isNotApprovedOnly() {
        return notApprovedOnly;
    }

    public void setNotApprovedOnly(boolean notApprovedOnly) {
        this.notApprovedOnly = notApprovedOnly;
    }

    /**
     * Prüft ob irgendwelche Suchkriterien gesetzt sind
     */
    public boolean hasAnyCriteria() {
        return (calloffNumber != null && !calloffNumber.isEmpty())
            || (orderNumber != null && !orderNumber.isEmpty())
            || (orderPosition != null && !orderPosition.isEmpty())
            || (customerNumber != null && !customerNumber.isEmpty())
            || (destination != null && !destination.isEmpty())
            || noDestination
            || (sapProductNumber != null && !sapProductNumber.isEmpty())
            || (productNumber != null && !productNumber.isEmpty())
            || (searchPattern != null && !searchPattern.isEmpty())
            || deliveryDateFrom != null
            || deliveryDateTo != null
            || incompleteOnly
            || completedOnly
            || approvedOnly
            || notApprovedOnly;
    }

    /**
     * Setzt alle Kriterien zurück
     */
    public void reset() {
        calloffNumber = null;
        orderNumber = null;
        orderPosition = null;
        customerNumber = null;
        destination = null;
        noDestination = false;
        sapProductNumber = null;
        productNumber = null;
        searchPattern = null;
        deliveryDateFrom = null;
        deliveryDateTo = null;
        incompleteOnly = true;
        completedOnly = false;
        approvedOnly = false;
        notApprovedOnly = false;
    }

    @Override
    public String toString() {
        return "CalloffSearchCriteria{" +
            "calloffNumber='" + calloffNumber + '\'' +
            ", orderNumber='" + orderNumber + '\'' +
            ", orderPosition='" + orderPosition + '\'' +
            ", customerNumber='" + customerNumber + '\'' +
            ", destination='" + destination + '\'' +
            ", sapProductNumber='" + sapProductNumber + '\'' +
            ", productNumber='" + productNumber + '\'' +
            ", incompleteOnly=" + incompleteOnly +
            ", completedOnly=" + completedOnly +
            ", notApprovedOnly=" + notApprovedOnly +
            '}';
    }
}
