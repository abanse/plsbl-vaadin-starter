package com.hydro.plsbl.dto;

/**
 * Suchkriterien für Abrufe (Calloff) - wie in der Original-Applikation
 */
public class CalloffSearchCriteria {

    private String calloffNumber;
    private String orderNumber;
    private String customerNumber;
    private String destination;
    private String sapProductNumber;
    private boolean incompleteOnly = true;  // Standard: Nur offene
    private boolean approvedOnly;
    private boolean notApprovedOnly;

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

    public String getSapProductNumber() {
        return sapProductNumber;
    }

    public void setSapProductNumber(String sapProductNumber) {
        this.sapProductNumber = sapProductNumber;
    }

    public boolean isIncompleteOnly() {
        return incompleteOnly;
    }

    public void setIncompleteOnly(boolean incompleteOnly) {
        this.incompleteOnly = incompleteOnly;
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
            || (customerNumber != null && !customerNumber.isEmpty())
            || (destination != null && !destination.isEmpty())
            || (sapProductNumber != null && !sapProductNumber.isEmpty())
            || incompleteOnly
            || approvedOnly
            || notApprovedOnly;
    }

    /**
     * Setzt alle Kriterien zurück
     */
    public void reset() {
        calloffNumber = null;
        orderNumber = null;
        customerNumber = null;
        destination = null;
        sapProductNumber = null;
        incompleteOnly = true;
        approvedOnly = false;
        notApprovedOnly = false;
    }

    @Override
    public String toString() {
        return "CalloffSearchCriteria{" +
            "calloffNumber='" + calloffNumber + '\'' +
            ", orderNumber='" + orderNumber + '\'' +
            ", customerNumber='" + customerNumber + '\'' +
            ", destination='" + destination + '\'' +
            ", sapProductNumber='" + sapProductNumber + '\'' +
            ", incompleteOnly=" + incompleteOnly +
            ", approvedOnly=" + approvedOnly +
            ", notApprovedOnly=" + notApprovedOnly +
            '}';
    }
}
