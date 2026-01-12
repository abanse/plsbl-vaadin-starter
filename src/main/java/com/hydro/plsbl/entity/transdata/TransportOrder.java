package com.hydro.plsbl.entity.transdata;

import com.hydro.plsbl.entity.enums.OrderStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.annotation.Version;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * Transportauftrag - Bewegungsdaten
 *
 * Entspricht der Tabelle TD_TRANSPORTORDER
 */
@Table("TD_TRANSPORTORDER")
public class TransportOrder implements Persistable<Long> {

    @Transient
    private boolean isNew = true;

    @Id
    private Long id;

    @Version
    @Column("SERIAL")
    private Long serial;

    @Column("TABLESERIAL")
    private Long tableSerial;

    @Column("TRANSPORT_NO")
    private String transportNo;

    @Column("NORMTEXT")
    private String normText;

    @Column("CALLOFF_ID")
    private Long calloffId;

    @Column("INGOT_ID")
    private Long ingotId;

    @Column("FROM_YARD_ID")
    private Long fromYardId;

    @Column("FROM_PILE_POSITION")
    private Integer fromPilePosition;

    @Column("TO_YARD_ID")
    private Long toYardId;

    @Column("TO_PILE_POSITION")
    private Integer toPilePosition;

    @Column("PRINTED")
    private LocalDateTime printed;

    @Column("DELIVERED")
    private LocalDateTime delivered;

    // === Neue Felder für automatische Verarbeitung ===

    @Column("STATUS")
    private String status;

    @Column("PRIORITY")
    private Integer priority;

    @Column("STARTED_AT")
    private LocalDateTime startedAt;

    @Column("COMPLETED_AT")
    private LocalDateTime completedAt;

    @Column("ERROR_MESSAGE")
    private String errorMessage;

    @Column("RETRY_COUNT")
    private Integer retryCount;

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

    public String getTransportNo() {
        return transportNo;
    }

    public void setTransportNo(String transportNo) {
        this.transportNo = transportNo;
    }

    public String getNormText() {
        return normText;
    }

    public void setNormText(String normText) {
        this.normText = normText;
    }

    public Long getCalloffId() {
        return calloffId;
    }

    public void setCalloffId(Long calloffId) {
        this.calloffId = calloffId;
    }

    public Long getIngotId() {
        return ingotId;
    }

    public void setIngotId(Long ingotId) {
        this.ingotId = ingotId;
    }

    public Long getFromYardId() {
        return fromYardId;
    }

    public void setFromYardId(Long fromYardId) {
        this.fromYardId = fromYardId;
    }

    public Integer getFromPilePosition() {
        return fromPilePosition;
    }

    public void setFromPilePosition(Integer fromPilePosition) {
        this.fromPilePosition = fromPilePosition;
    }

    public Long getToYardId() {
        return toYardId;
    }

    public void setToYardId(Long toYardId) {
        this.toYardId = toYardId;
    }

    public Integer getToPilePosition() {
        return toPilePosition;
    }

    public void setToPilePosition(Integer toPilePosition) {
        this.toPilePosition = toPilePosition;
    }

    public LocalDateTime getPrinted() {
        return printed;
    }

    public void setPrinted(LocalDateTime printed) {
        this.printed = printed;
    }

    public LocalDateTime getDelivered() {
        return delivered;
    }

    public void setDelivered(LocalDateTime delivered) {
        this.delivered = delivered;
    }

    // === Neue Getter/Setter für automatische Verarbeitung ===

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OrderStatus getOrderStatus() {
        return OrderStatus.fromCode(status);
    }

    public void setOrderStatus(OrderStatus orderStatus) {
        this.status = orderStatus != null ? orderStatus.getCode() : null;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
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
        return "TransportOrder{" +
                "transportNo='" + transportNo + '\'' +
                ", normText='" + normText + '\'' +
                '}';
    }
}
