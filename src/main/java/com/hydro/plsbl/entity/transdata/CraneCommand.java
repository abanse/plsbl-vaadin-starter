package com.hydro.plsbl.entity.transdata;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.annotation.Version;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Kran-Kommando - Bewegungsdaten
 *
 * Entspricht der Tabelle TD_CRANECOMMAND
 */
@Table("TD_CRANECOMMAND")
public class CraneCommand implements Persistable<Long> {

    @Transient
    private boolean isNew = true;

    @Id
    private Long id;

    @Version
    @Column("SERIAL")
    private Long serial;

    @Column("TABLESERIAL")
    private Long tableSerial;

    @Column("CMD_TYPE")
    private String cmdType;

    @Column("ROTATE")
    private Integer rotate;

    @Column("CRANE_MODE")
    private String craneMode;

    @Column("INGOT_ID")
    private Long ingotId;

    @Column("FROM_YARD_ID")
    private Long fromYardId;

    @Column("TO_YARD_ID")
    private Long toYardId;

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

    public String getCmdType() {
        return cmdType;
    }

    public void setCmdType(String cmdType) {
        this.cmdType = cmdType;
    }

    public Integer getRotate() {
        return rotate;
    }

    public void setRotate(Integer rotate) {
        this.rotate = rotate;
    }

    public String getCraneMode() {
        return craneMode;
    }

    public void setCraneMode(String craneMode) {
        this.craneMode = craneMode;
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

    public Long getToYardId() {
        return toYardId;
    }

    public void setToYardId(Long toYardId) {
        this.toYardId = toYardId;
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
        return "CraneCommand{" +
                "id=" + id +
                ", cmdType='" + cmdType + '\'' +
                ", craneMode='" + craneMode + '\'' +
                '}';
    }
}
