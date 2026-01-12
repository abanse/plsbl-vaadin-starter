package com.hydro.plsbl.entity.masterdata;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Anwendungs-Einstellung (Key-Value Paar)
 *
 * Entspricht der Tabelle MD_APPSETTING
 */
@Table("MD_APPSETTING")
public class AppSetting implements Persistable<String> {

    @Transient
    private boolean isNew = true;

    @Id
    @Column("SETTING_KEY")
    private String key;

    @Column("SETTING_VALUE")
    private String value;

    @Column("DESCRIPTION")
    private String description;

    @Column("CATEGORY")
    private String category;

    // === Constructors ===

    public AppSetting() {
    }

    public AppSetting(String key, String value, String category, String description) {
        this.key = key;
        this.value = value;
        this.category = category;
        this.description = description;
        this.isNew = true;
    }

    // === Getters & Setters ===

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    // === Helper Methods ===

    public int getIntValue() {
        return getIntValue(0);
    }

    public int getIntValue(int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public void setIntValue(int intValue) {
        this.value = String.valueOf(intValue);
    }

    public boolean getBooleanValue() {
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    public void setBooleanValue(boolean boolValue) {
        this.value = String.valueOf(boolValue);
    }

    // === Persistable Implementation ===

    @Override
    public String getId() {
        return key;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public void markNotNew() {
        this.isNew = false;
    }

    @Override
    public String toString() {
        return "AppSetting{" +
                "key='" + key + '\'' +
                ", value='" + value + '\'' +
                ", category='" + category + '\'' +
                '}';
    }
}
