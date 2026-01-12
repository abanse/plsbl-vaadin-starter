package com.hydro.plsbl.service;

import com.hydro.plsbl.entity.masterdata.AppSetting;
import com.hydro.plsbl.repository.AppSettingRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * Service für Anwendungs-Einstellungen
 *
 * Hält die konfigurierbaren Werte für Kran, Säge und Lager.
 * Die Werte werden in der Datenbank persistiert (Tabelle MD_APPSETTING).
 */
@Service
public class SettingsService {

    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);

    // Kategorien
    public static final String CAT_SAW = "SAW";
    public static final String CAT_CRANE = "CRANE";
    public static final String CAT_WAREHOUSE = "WAREHOUSE";
    public static final String CAT_SPS = "SPS";
    public static final String CAT_LOADING = "LOADING";
    public static final String CAT_GENERAL = "GENERAL";

    // Setting Keys
    public static final String KEY_SAW_X = "SAW_X";
    public static final String KEY_SAW_Y = "SAW_Y";
    public static final String KEY_SAW_Z = "SAW_Z";
    public static final String KEY_SAW_TOLERANZ_X = "SAW_TOLERANZ_X";
    public static final String KEY_SAW_TOLERANZ_Y = "SAW_TOLERANZ_Y";
    public static final String KEY_SAW_TOLERANZ_Z = "SAW_TOLERANZ_Z";

    public static final String KEY_LAGER_MIN_X = "LAGER_MIN_X";
    public static final String KEY_LAGER_MIN_Y = "LAGER_MIN_Y";
    public static final String KEY_LAGER_MAX_X = "LAGER_MAX_X";
    public static final String KEY_LAGER_MAX_Y = "LAGER_MAX_Y";
    public static final String KEY_GRID_COLS = "GRID_COLS";
    public static final String KEY_GRID_ROWS = "GRID_ROWS";

    public static final String KEY_KRAN_ZIELPOS_MAX_DX = "KRAN_ZIELPOS_MAX_DX";
    public static final String KEY_KRAN_ZIELPOS_MAX_DY = "KRAN_ZIELPOS_MAX_DY";
    public static final String KEY_KRAN_ZIELPOS_MAX_DZ = "KRAN_ZIELPOS_MAX_DZ";
    public static final String KEY_KRAN_TOLERANZ_DX = "KRAN_TOLERANZ_DX";
    public static final String KEY_KRAN_TOLERANZ_DY = "KRAN_TOLERANZ_DY";
    public static final String KEY_KRAN_TOLERANZ_DZ = "KRAN_TOLERANZ_DZ";
    public static final String KEY_PHASENERKENNUNG_DX = "PHASENERKENNUNG_DX";
    public static final String KEY_PHASENERKENNUNG_DY = "PHASENERKENNUNG_DY";
    public static final String KEY_PHASENERKENNUNG_DZ = "PHASENERKENNUNG_DZ";

    // SPS Settings
    public static final String KEY_SPS_URL = "SPS_URL";
    public static final String KEY_SPS_TIMEOUT = "SPS_TIMEOUT";
    public static final String KEY_SPS_RETRY_COUNT = "SPS_RETRY_COUNT";
    public static final String KEY_SPS_RETRY_DELAY = "SPS_RETRY_DELAY";
    public static final String KEY_SPS_ENABLED = "SPS_ENABLED";

    // Verladung Settings
    public static final String KEY_LOADING_ZONE_FRONT_X = "LOADING_ZONE_FRONT_X";
    public static final String KEY_LOADING_ZONE_REAR_X = "LOADING_ZONE_REAR_X";
    public static final String KEY_LOADING_ZONE_MIDDLE_X = "LOADING_ZONE_MIDDLE_X";
    public static final String KEY_LOADING_ZONE_Y = "LOADING_ZONE_Y";
    public static final String KEY_LOADING_ZONE_Z = "LOADING_ZONE_Z";
    public static final String KEY_LOADING_MAX_INGOTS = "LOADING_MAX_INGOTS";
    public static final String KEY_LOADING_MAX_WEIGHT = "LOADING_MAX_WEIGHT";

    // General Settings
    public static final String KEY_TEMPORARY_PRODUCT_TIMEOUT = "TEMPORARY_PRODUCT_TIMEOUT";
    public static final String KEY_RETIRE_DAYS = "RETIRE_DAYS";
    public static final String KEY_HELP_URL = "HELP_URL";

    // Color Settings (Yard colors)
    public static final String CAT_COLORS = "COLORS";
    public static final String KEY_COLOR_YARD_EMPTY = "COLOR_YARD_EMPTY";
    public static final String KEY_COLOR_YARD_IN_USE = "COLOR_YARD_IN_USE";
    public static final String KEY_COLOR_YARD_FULL = "COLOR_YARD_FULL";
    public static final String KEY_COLOR_YARD_LOCKED = "COLOR_YARD_LOCKED";
    public static final String KEY_COLOR_YARD_NO_SWAP_IN = "COLOR_YARD_NO_SWAP_IN";
    public static final String KEY_COLOR_YARD_NO_SWAP_OUT = "COLOR_YARD_NO_SWAP_OUT";
    public static final String KEY_COLOR_YARD_NO_SWAP_IN_OUT = "COLOR_YARD_NO_SWAP_IN_OUT";

    // Kafka Settings
    public static final String CAT_KAFKA = "KAFKA";
    public static final String KEY_KAFKA_ENABLED = "KAFKA_ENABLED";
    public static final String KEY_KAFKA_BOOTSTRAP_SERVERS = "KAFKA_BOOTSTRAP_SERVERS";
    public static final String KEY_KAFKA_GROUP_ID = "KAFKA_GROUP_ID";
    public static final String KEY_KAFKA_CLIENT_ID = "KAFKA_CLIENT_ID";
    // Topics - Incoming (von SAP/Saege)
    public static final String KEY_KAFKA_TOPIC_CALLOFF = "KAFKA_TOPIC_CALLOFF";
    public static final String KEY_KAFKA_TOPIC_PICKUP_ORDER = "KAFKA_TOPIC_PICKUP_ORDER";
    public static final String KEY_KAFKA_TOPIC_PRODUCT_RESTRICTION = "KAFKA_TOPIC_PRODUCT_RESTRICTION";
    // Topics - Outgoing (an SAP/Saege)
    public static final String KEY_KAFKA_TOPIC_INGOT_PICKED_UP = "KAFKA_TOPIC_INGOT_PICKED_UP";
    public static final String KEY_KAFKA_TOPIC_INGOT_MOVED = "KAFKA_TOPIC_INGOT_MOVED";
    public static final String KEY_KAFKA_TOPIC_INGOT_MODIFIED = "KAFKA_TOPIC_INGOT_MODIFIED";
    public static final String KEY_KAFKA_TOPIC_SHIPMENT_COMPLETED = "KAFKA_TOPIC_SHIPMENT_COMPLETED";
    public static final String KEY_KAFKA_TOPIC_SAW_FEEDBACK = "KAFKA_TOPIC_SAW_FEEDBACK";

    private final JdbcTemplate jdbcTemplate;

    // Cache für Einstellungen (Key -> Value)
    private final Map<String, Integer> settingsCache = new HashMap<>();
    private final Map<String, String> stringSettingsCache = new HashMap<>();

    // Default-Werte (Integer)
    private static final Map<String, SettingDefinition> DEFAULTS = new HashMap<>();
    // Default-Werte (String)
    private static final Map<String, StringSettingDefinition> STRING_DEFAULTS = new HashMap<>();

    static {
        // Säge
        DEFAULTS.put(KEY_SAW_X, new SettingDefinition(9300, CAT_SAW, "Abholposition Mitte X [mm]"));
        DEFAULTS.put(KEY_SAW_Y, new SettingDefinition(36020, CAT_SAW, "Abholposition Vorderkante Y [mm]"));
        DEFAULTS.put(KEY_SAW_Z, new SettingDefinition(1138, CAT_SAW, "Abholposition Auflagefläche Z [mm]"));
        DEFAULTS.put(KEY_SAW_TOLERANZ_X, new SettingDefinition(600, CAT_SAW, "Toleranz X [mm]"));
        DEFAULTS.put(KEY_SAW_TOLERANZ_Y, new SettingDefinition(330, CAT_SAW, "Toleranz Y [mm]"));
        DEFAULTS.put(KEY_SAW_TOLERANZ_Z, new SettingDefinition(145, CAT_SAW, "Toleranz Z [mm]"));

        // Lager
        DEFAULTS.put(KEY_LAGER_MIN_X, new SettingDefinition(5000, CAT_WAREHOUSE, "Internes Lager min. X [mm]"));
        DEFAULTS.put(KEY_LAGER_MIN_Y, new SettingDefinition(9000, CAT_WAREHOUSE, "Internes Lager min. Y [mm]"));
        DEFAULTS.put(KEY_LAGER_MAX_X, new SettingDefinition(98000, CAT_WAREHOUSE, "Internes Lager max. X [mm]"));
        DEFAULTS.put(KEY_LAGER_MAX_Y, new SettingDefinition(37000, CAT_WAREHOUSE, "Internes Lager max. Y [mm]"));
        DEFAULTS.put(KEY_GRID_COLS, new SettingDefinition(17, CAT_WAREHOUSE, "Grid Spalten"));
        DEFAULTS.put(KEY_GRID_ROWS, new SettingDefinition(10, CAT_WAREHOUSE, "Grid Zeilen"));

        // Kran
        DEFAULTS.put(KEY_KRAN_ZIELPOS_MAX_DX, new SettingDefinition(150, CAT_CRANE, "Zielposition max. dX [mm]"));
        DEFAULTS.put(KEY_KRAN_ZIELPOS_MAX_DY, new SettingDefinition(50, CAT_CRANE, "Zielposition max. dY [mm]"));
        DEFAULTS.put(KEY_KRAN_ZIELPOS_MAX_DZ, new SettingDefinition(1200, CAT_CRANE, "Zielposition max. dZ [mm]"));
        DEFAULTS.put(KEY_KRAN_TOLERANZ_DX, new SettingDefinition(50, CAT_CRANE, "Toleranz dX [mm]"));
        DEFAULTS.put(KEY_KRAN_TOLERANZ_DY, new SettingDefinition(25, CAT_CRANE, "Toleranz dY [mm]"));
        DEFAULTS.put(KEY_KRAN_TOLERANZ_DZ, new SettingDefinition(300, CAT_CRANE, "Toleranz dZ [mm]"));
        DEFAULTS.put(KEY_PHASENERKENNUNG_DX, new SettingDefinition(500, CAT_CRANE, "Phasenerkennung dX [mm]"));
        DEFAULTS.put(KEY_PHASENERKENNUNG_DY, new SettingDefinition(500, CAT_CRANE, "Phasenerkennung dY [mm]"));
        DEFAULTS.put(KEY_PHASENERKENNUNG_DZ, new SettingDefinition(50, CAT_CRANE, "Phasenerkennung dZ [mm]"));

        // SPS (Integer-Werte)
        DEFAULTS.put(KEY_SPS_TIMEOUT, new SettingDefinition(10, CAT_SPS, "SPS Timeout [s]"));
        DEFAULTS.put(KEY_SPS_RETRY_COUNT, new SettingDefinition(3, CAT_SPS, "Anzahl Wiederholungsversuche"));
        DEFAULTS.put(KEY_SPS_RETRY_DELAY, new SettingDefinition(1000, CAT_SPS, "Wartezeit zwischen Versuchen [ms]"));
        DEFAULTS.put(KEY_SPS_ENABLED, new SettingDefinition(1, CAT_SPS, "SPS-Verbindung aktiviert (1=ja, 0=nein)"));

        // SPS (String-Werte)
        STRING_DEFAULTS.put(KEY_SPS_URL, new StringSettingDefinition(
            "s7://10.72.242.190:102?remote-slot=1", CAT_SPS, "PLC4X Verbindungs-URL"));

        // Verladung
        DEFAULTS.put(KEY_LOADING_ZONE_FRONT_X, new SettingDefinition(0, CAT_LOADING, "Ladezone vorne X [mm]"));
        DEFAULTS.put(KEY_LOADING_ZONE_REAR_X, new SettingDefinition(0, CAT_LOADING, "Ladezone hinten X [mm]"));
        DEFAULTS.put(KEY_LOADING_ZONE_MIDDLE_X, new SettingDefinition(0, CAT_LOADING, "Ladezone mitte X [mm]"));
        DEFAULTS.put(KEY_LOADING_ZONE_Y, new SettingDefinition(0, CAT_LOADING, "Ladezone Y [mm]"));
        DEFAULTS.put(KEY_LOADING_ZONE_Z, new SettingDefinition(0, CAT_LOADING, "Ladezone Z [mm]"));
        DEFAULTS.put(KEY_LOADING_MAX_INGOTS, new SettingDefinition(20, CAT_LOADING, "Max. Anzahl Ingots pro Verladung"));
        DEFAULTS.put(KEY_LOADING_MAX_WEIGHT, new SettingDefinition(25000, CAT_LOADING, "Max. Gewicht pro Verladung [kg]"));

        // General
        DEFAULTS.put(KEY_TEMPORARY_PRODUCT_TIMEOUT, new SettingDefinition(30, CAT_GENERAL, "Temp. Produkt Timeout [min]"));
        DEFAULTS.put(KEY_RETIRE_DAYS, new SettingDefinition(365, CAT_GENERAL, "Ruhestandstage"));
        STRING_DEFAULTS.put(KEY_HELP_URL, new StringSettingDefinition(
            "https://help.example.com", CAT_GENERAL, "Hilfe-URL"));

        // Colors (Hex-Werte)
        STRING_DEFAULTS.put(KEY_COLOR_YARD_EMPTY, new StringSettingDefinition(
            "#4CAF50", CAT_COLORS, "Lagerplatz leer (gruen)"));
        STRING_DEFAULTS.put(KEY_COLOR_YARD_IN_USE, new StringSettingDefinition(
            "#FFEB3B", CAT_COLORS, "Lagerplatz belegt (gelb)"));
        STRING_DEFAULTS.put(KEY_COLOR_YARD_FULL, new StringSettingDefinition(
            "#F44336", CAT_COLORS, "Lagerplatz voll (rot)"));
        STRING_DEFAULTS.put(KEY_COLOR_YARD_LOCKED, new StringSettingDefinition(
            "#9E9E9E", CAT_COLORS, "Lagerplatz gesperrt (grau)"));
        STRING_DEFAULTS.put(KEY_COLOR_YARD_NO_SWAP_IN, new StringSettingDefinition(
            "#FF9800", CAT_COLORS, "Keine Einlagerung (orange)"));
        STRING_DEFAULTS.put(KEY_COLOR_YARD_NO_SWAP_OUT, new StringSettingDefinition(
            "#2196F3", CAT_COLORS, "Keine Auslagerung (blau)"));
        STRING_DEFAULTS.put(KEY_COLOR_YARD_NO_SWAP_IN_OUT, new StringSettingDefinition(
            "#9C27B0", CAT_COLORS, "Keine Ein-/Auslagerung (lila)"));

        // Kafka
        DEFAULTS.put(KEY_KAFKA_ENABLED, new SettingDefinition(0, CAT_KAFKA, "Kafka aktiviert (1=ja, 0=nein)"));
        STRING_DEFAULTS.put(KEY_KAFKA_BOOTSTRAP_SERVERS, new StringSettingDefinition(
            "localhost:9092", CAT_KAFKA, "Kafka Bootstrap Server(s)"));
        STRING_DEFAULTS.put(KEY_KAFKA_GROUP_ID, new StringSettingDefinition(
            "plsbl-group", CAT_KAFKA, "Kafka Consumer Group ID"));
        STRING_DEFAULTS.put(KEY_KAFKA_CLIENT_ID, new StringSettingDefinition(
            "plsbl-client", CAT_KAFKA, "Kafka Client ID"));
        // Topics - Incoming
        STRING_DEFAULTS.put(KEY_KAFKA_TOPIC_CALLOFF, new StringSettingDefinition(
            "plsbl.incoming.calloff", CAT_KAFKA, "Topic fuer Abruf-Telegramme"));
        STRING_DEFAULTS.put(KEY_KAFKA_TOPIC_PICKUP_ORDER, new StringSettingDefinition(
            "plsbl.incoming.pickup-order", CAT_KAFKA, "Topic fuer Abholauftraege von der Saege"));
        STRING_DEFAULTS.put(KEY_KAFKA_TOPIC_PRODUCT_RESTRICTION, new StringSettingDefinition(
            "plsbl.incoming.product-restriction", CAT_KAFKA, "Topic fuer Produkt-Einschraenkungen"));
        // Topics - Outgoing
        STRING_DEFAULTS.put(KEY_KAFKA_TOPIC_INGOT_PICKED_UP, new StringSettingDefinition(
            "plsbl.outgoing.ingot-picked-up", CAT_KAFKA, "Topic fuer Barren-Abholung Meldung"));
        STRING_DEFAULTS.put(KEY_KAFKA_TOPIC_INGOT_MOVED, new StringSettingDefinition(
            "plsbl.outgoing.ingot-moved", CAT_KAFKA, "Topic fuer Barren-Umlagerung Meldung"));
        STRING_DEFAULTS.put(KEY_KAFKA_TOPIC_INGOT_MODIFIED, new StringSettingDefinition(
            "plsbl.outgoing.ingot-modified", CAT_KAFKA, "Topic fuer Barren-Aenderung Meldung"));
        STRING_DEFAULTS.put(KEY_KAFKA_TOPIC_SHIPMENT_COMPLETED, new StringSettingDefinition(
            "plsbl.outgoing.shipment-completed", CAT_KAFKA, "Topic fuer Lieferung-Abgeschlossen Meldung"));
        STRING_DEFAULTS.put(KEY_KAFKA_TOPIC_SAW_FEEDBACK, new StringSettingDefinition(
            "plsbl.outgoing.saw-feedback", CAT_KAFKA, "Topic fuer Rueckmeldung an die Saege"));
    }

    public SettingsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        log.info("Initializing SettingsService...");
        ensureTableExists();
        loadAllSettings();
        log.info("SettingsService initialized with {} settings", settingsCache.size());
    }

    /**
     * Stellt sicher, dass die Tabelle MD_APPSETTING existiert
     */
    private void ensureTableExists() {
        try {
            // Prüfen ob Tabelle existiert
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM MD_APPSETTING WHERE ROWNUM = 1", Integer.class);
            log.debug("Table MD_APPSETTING exists");
        } catch (Exception e) {
            // Tabelle existiert nicht - erstellen
            log.info("Creating table MD_APPSETTING...");
            try {
                jdbcTemplate.execute(
                    "CREATE TABLE MD_APPSETTING (" +
                    "SETTING_KEY VARCHAR2(100) PRIMARY KEY, " +
                    "SETTING_VALUE VARCHAR2(500), " +
                    "CATEGORY VARCHAR2(50), " +
                    "DESCRIPTION VARCHAR2(500)" +
                    ")"
                );
                log.info("Table MD_APPSETTING created successfully");
            } catch (Exception createEx) {
                log.warn("Could not create table MD_APPSETTING: {}", createEx.getMessage());
            }
        }
    }

    /**
     * Lädt alle Einstellungen aus der Datenbank
     */
    private void loadAllSettings() {
        settingsCache.clear();
        stringSettingsCache.clear();

        try {
            jdbcTemplate.query(
                "SELECT SETTING_KEY, SETTING_VALUE FROM MD_APPSETTING",
                (rs, rowNum) -> {
                    String key = rs.getString("SETTING_KEY");
                    String value = rs.getString("SETTING_VALUE");

                    // Prüfen ob es ein String-Setting ist
                    if (STRING_DEFAULTS.containsKey(key)) {
                        stringSettingsCache.put(key, value);
                    } else {
                        try {
                            settingsCache.put(key, Integer.parseInt(value));
                        } catch (NumberFormatException e) {
                            // Könnte ein String-Wert sein
                            stringSettingsCache.put(key, value);
                        }
                    }
                    return null;
                }
            );
            log.info("Loaded {} int settings and {} string settings from database",
                settingsCache.size(), stringSettingsCache.size());
        } catch (Exception e) {
            log.warn("Could not load settings from database: {}", e.getMessage());
        }

        // Fehlende Integer-Einstellungen mit Defaults auffüllen und speichern
        for (Map.Entry<String, SettingDefinition> entry : DEFAULTS.entrySet()) {
            if (!settingsCache.containsKey(entry.getKey())) {
                settingsCache.put(entry.getKey(), entry.getValue().defaultValue);
                saveSettingToDb(entry.getKey(), String.valueOf(entry.getValue().defaultValue),
                    entry.getValue().category, entry.getValue().description);
            }
        }

        // Fehlende String-Einstellungen mit Defaults auffüllen und speichern
        for (Map.Entry<String, StringSettingDefinition> entry : STRING_DEFAULTS.entrySet()) {
            if (!stringSettingsCache.containsKey(entry.getKey())) {
                stringSettingsCache.put(entry.getKey(), entry.getValue().defaultValue);
                saveSettingToDb(entry.getKey(), entry.getValue().defaultValue,
                    entry.getValue().category, entry.getValue().description);
            }
        }
    }

    /**
     * Speichert eine Einstellung in der Datenbank
     */
    @Transactional
    private void saveSettingToDb(String key, String value, String category, String description) {
        try {
            // Prüfen ob Eintrag existiert
            int count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM MD_APPSETTING WHERE SETTING_KEY = ?",
                Integer.class, key);

            if (count > 0) {
                // Update
                jdbcTemplate.update(
                    "UPDATE MD_APPSETTING SET SETTING_VALUE = ? WHERE SETTING_KEY = ?",
                    value, key);
            } else {
                // Insert
                jdbcTemplate.update(
                    "INSERT INTO MD_APPSETTING (SETTING_KEY, SETTING_VALUE, CATEGORY, DESCRIPTION) VALUES (?, ?, ?, ?)",
                    key, value, category, description);
            }
            log.debug("Setting saved: {} = {}", key, value);
        } catch (Exception e) {
            log.error("Error saving setting {} = {}: {}", key, value, e.getMessage());
        }
    }

    /**
     * Holt einen Wert aus dem Cache (mit Default-Fallback)
     */
    private int getValue(String key) {
        Integer value = settingsCache.get(key);
        if (value != null) {
            return value;
        }
        SettingDefinition def = DEFAULTS.get(key);
        return def != null ? def.defaultValue : 0;
    }

    /**
     * Setzt einen Integer-Wert und speichert ihn in der Datenbank
     */
    private void setValue(String key, int value) {
        settingsCache.put(key, value);
        SettingDefinition def = DEFAULTS.get(key);
        String category = def != null ? def.category : "UNKNOWN";
        String description = def != null ? def.description : "";
        saveSettingToDb(key, String.valueOf(value), category, description);
        log.info("Setting updated: {} = {}", key, value);
    }

    /**
     * Holt einen String-Wert aus dem Cache (mit Default-Fallback)
     */
    private String getStringValue(String key) {
        String value = stringSettingsCache.get(key);
        if (value != null) {
            return value;
        }
        StringSettingDefinition def = STRING_DEFAULTS.get(key);
        return def != null ? def.defaultValue : "";
    }

    /**
     * Setzt einen String-Wert und speichert ihn in der Datenbank
     */
    private void setStringValue(String key, String value) {
        stringSettingsCache.put(key, value);
        StringSettingDefinition def = STRING_DEFAULTS.get(key);
        String category = def != null ? def.category : "UNKNOWN";
        String description = def != null ? def.description : "";
        saveSettingToDb(key, value, category, description);
        log.info("Setting updated: {} = {}", key, value);
    }

    // === Getters & Setters ===

    // Säge
    public int getSawX() { return getValue(KEY_SAW_X); }
    public void setSawX(int value) { setValue(KEY_SAW_X, value); }

    public int getSawY() { return getValue(KEY_SAW_Y); }
    public void setSawY(int value) { setValue(KEY_SAW_Y, value); }

    public int getSawZ() { return getValue(KEY_SAW_Z); }
    public void setSawZ(int value) { setValue(KEY_SAW_Z, value); }

    public int getSawToleranzX() { return getValue(KEY_SAW_TOLERANZ_X); }
    public void setSawToleranzX(int value) { setValue(KEY_SAW_TOLERANZ_X, value); }

    public int getSawToleranzY() { return getValue(KEY_SAW_TOLERANZ_Y); }
    public void setSawToleranzY(int value) { setValue(KEY_SAW_TOLERANZ_Y, value); }

    public int getSawToleranzZ() { return getValue(KEY_SAW_TOLERANZ_Z); }
    public void setSawToleranzZ(int value) { setValue(KEY_SAW_TOLERANZ_Z, value); }

    // Lager
    public int getLagerMinX() { return getValue(KEY_LAGER_MIN_X); }
    public void setLagerMinX(int value) { setValue(KEY_LAGER_MIN_X, value); }

    public int getLagerMinY() { return getValue(KEY_LAGER_MIN_Y); }
    public void setLagerMinY(int value) { setValue(KEY_LAGER_MIN_Y, value); }

    public int getLagerMaxX() { return getValue(KEY_LAGER_MAX_X); }
    public void setLagerMaxX(int value) { setValue(KEY_LAGER_MAX_X, value); }

    public int getLagerMaxY() { return getValue(KEY_LAGER_MAX_Y); }
    public void setLagerMaxY(int value) { setValue(KEY_LAGER_MAX_Y, value); }

    public int getGridCols() { return getValue(KEY_GRID_COLS); }
    public void setGridCols(int value) { setValue(KEY_GRID_COLS, value); }

    public int getGridRows() { return getValue(KEY_GRID_ROWS); }
    public void setGridRows(int value) { setValue(KEY_GRID_ROWS, value); }

    // Kran
    public int getKranZielposMaxDX() { return getValue(KEY_KRAN_ZIELPOS_MAX_DX); }
    public void setKranZielposMaxDX(int value) { setValue(KEY_KRAN_ZIELPOS_MAX_DX, value); }

    public int getKranZielposMaxDY() { return getValue(KEY_KRAN_ZIELPOS_MAX_DY); }
    public void setKranZielposMaxDY(int value) { setValue(KEY_KRAN_ZIELPOS_MAX_DY, value); }

    public int getKranZielposMaxDZ() { return getValue(KEY_KRAN_ZIELPOS_MAX_DZ); }
    public void setKranZielposMaxDZ(int value) { setValue(KEY_KRAN_ZIELPOS_MAX_DZ, value); }

    public int getKranToleranzDX() { return getValue(KEY_KRAN_TOLERANZ_DX); }
    public void setKranToleranzDX(int value) { setValue(KEY_KRAN_TOLERANZ_DX, value); }

    public int getKranToleranzDY() { return getValue(KEY_KRAN_TOLERANZ_DY); }
    public void setKranToleranzDY(int value) { setValue(KEY_KRAN_TOLERANZ_DY, value); }

    public int getKranToleranzDZ() { return getValue(KEY_KRAN_TOLERANZ_DZ); }
    public void setKranToleranzDZ(int value) { setValue(KEY_KRAN_TOLERANZ_DZ, value); }

    public int getPhasenerkennungDX() { return getValue(KEY_PHASENERKENNUNG_DX); }
    public void setPhasenerkennungDX(int value) { setValue(KEY_PHASENERKENNUNG_DX, value); }

    public int getPhasenerkennungDY() { return getValue(KEY_PHASENERKENNUNG_DY); }
    public void setPhasenerkennungDY(int value) { setValue(KEY_PHASENERKENNUNG_DY, value); }

    public int getPhasenerkennungDZ() { return getValue(KEY_PHASENERKENNUNG_DZ); }
    public void setPhasenerkennungDZ(int value) { setValue(KEY_PHASENERKENNUNG_DZ, value); }

    // SPS
    public String getSpsUrl() { return getStringValue(KEY_SPS_URL); }
    public void setSpsUrl(String value) { setStringValue(KEY_SPS_URL, value); }

    public int getSpsTimeout() { return getValue(KEY_SPS_TIMEOUT); }
    public void setSpsTimeout(int value) { setValue(KEY_SPS_TIMEOUT, value); }

    public int getSpsRetryCount() { return getValue(KEY_SPS_RETRY_COUNT); }
    public void setSpsRetryCount(int value) { setValue(KEY_SPS_RETRY_COUNT, value); }

    public int getSpsRetryDelay() { return getValue(KEY_SPS_RETRY_DELAY); }
    public void setSpsRetryDelay(int value) { setValue(KEY_SPS_RETRY_DELAY, value); }

    public boolean isSpsEnabled() { return getValue(KEY_SPS_ENABLED) == 1; }
    public void setSpsEnabled(boolean enabled) { setValue(KEY_SPS_ENABLED, enabled ? 1 : 0); }

    // Verladung
    public int getLoadingZoneFrontX() { return getValue(KEY_LOADING_ZONE_FRONT_X); }
    public void setLoadingZoneFrontX(int value) { setValue(KEY_LOADING_ZONE_FRONT_X, value); }

    public int getLoadingZoneRearX() { return getValue(KEY_LOADING_ZONE_REAR_X); }
    public void setLoadingZoneRearX(int value) { setValue(KEY_LOADING_ZONE_REAR_X, value); }

    public int getLoadingZoneMiddleX() { return getValue(KEY_LOADING_ZONE_MIDDLE_X); }
    public void setLoadingZoneMiddleX(int value) { setValue(KEY_LOADING_ZONE_MIDDLE_X, value); }

    public int getLoadingZoneY() { return getValue(KEY_LOADING_ZONE_Y); }
    public void setLoadingZoneY(int value) { setValue(KEY_LOADING_ZONE_Y, value); }

    public int getLoadingZoneZ() { return getValue(KEY_LOADING_ZONE_Z); }
    public void setLoadingZoneZ(int value) { setValue(KEY_LOADING_ZONE_Z, value); }

    public int getLoadingMaxIngots() { return getValue(KEY_LOADING_MAX_INGOTS); }
    public void setLoadingMaxIngots(int value) { setValue(KEY_LOADING_MAX_INGOTS, value); }

    public int getLoadingMaxWeight() { return getValue(KEY_LOADING_MAX_WEIGHT); }
    public void setLoadingMaxWeight(int value) { setValue(KEY_LOADING_MAX_WEIGHT, value); }

    // General
    public int getTemporaryProductTimeout() { return getValue(KEY_TEMPORARY_PRODUCT_TIMEOUT); }
    public void setTemporaryProductTimeout(int value) { setValue(KEY_TEMPORARY_PRODUCT_TIMEOUT, value); }

    public int getRetireDays() { return getValue(KEY_RETIRE_DAYS); }
    public void setRetireDays(int value) { setValue(KEY_RETIRE_DAYS, value); }

    public String getHelpUrl() { return getStringValue(KEY_HELP_URL); }
    public void setHelpUrl(String value) { setStringValue(KEY_HELP_URL, value); }

    // Colors
    public String getColorYardEmpty() { return getStringValue(KEY_COLOR_YARD_EMPTY); }
    public void setColorYardEmpty(String value) { setStringValue(KEY_COLOR_YARD_EMPTY, value); }

    public String getColorYardInUse() { return getStringValue(KEY_COLOR_YARD_IN_USE); }
    public void setColorYardInUse(String value) { setStringValue(KEY_COLOR_YARD_IN_USE, value); }

    public String getColorYardFull() { return getStringValue(KEY_COLOR_YARD_FULL); }
    public void setColorYardFull(String value) { setStringValue(KEY_COLOR_YARD_FULL, value); }

    public String getColorYardLocked() { return getStringValue(KEY_COLOR_YARD_LOCKED); }
    public void setColorYardLocked(String value) { setStringValue(KEY_COLOR_YARD_LOCKED, value); }

    public String getColorYardNoSwapIn() { return getStringValue(KEY_COLOR_YARD_NO_SWAP_IN); }
    public void setColorYardNoSwapIn(String value) { setStringValue(KEY_COLOR_YARD_NO_SWAP_IN, value); }

    public String getColorYardNoSwapOut() { return getStringValue(KEY_COLOR_YARD_NO_SWAP_OUT); }
    public void setColorYardNoSwapOut(String value) { setStringValue(KEY_COLOR_YARD_NO_SWAP_OUT, value); }

    public String getColorYardNoSwapInOut() { return getStringValue(KEY_COLOR_YARD_NO_SWAP_IN_OUT); }
    public void setColorYardNoSwapInOut(String value) { setStringValue(KEY_COLOR_YARD_NO_SWAP_IN_OUT, value); }

    // Kafka
    public boolean isKafkaEnabled() { return getValue(KEY_KAFKA_ENABLED) == 1; }
    public void setKafkaEnabled(boolean enabled) { setValue(KEY_KAFKA_ENABLED, enabled ? 1 : 0); }

    public String getKafkaBootstrapServers() { return getStringValue(KEY_KAFKA_BOOTSTRAP_SERVERS); }
    public void setKafkaBootstrapServers(String value) { setStringValue(KEY_KAFKA_BOOTSTRAP_SERVERS, value); }

    public String getKafkaGroupId() { return getStringValue(KEY_KAFKA_GROUP_ID); }
    public void setKafkaGroupId(String value) { setStringValue(KEY_KAFKA_GROUP_ID, value); }

    public String getKafkaClientId() { return getStringValue(KEY_KAFKA_CLIENT_ID); }
    public void setKafkaClientId(String value) { setStringValue(KEY_KAFKA_CLIENT_ID, value); }

    // Kafka Topics - Incoming
    public String getKafkaTopicCalloff() { return getStringValue(KEY_KAFKA_TOPIC_CALLOFF); }
    public void setKafkaTopicCalloff(String value) { setStringValue(KEY_KAFKA_TOPIC_CALLOFF, value); }

    public String getKafkaTopicPickupOrder() { return getStringValue(KEY_KAFKA_TOPIC_PICKUP_ORDER); }
    public void setKafkaTopicPickupOrder(String value) { setStringValue(KEY_KAFKA_TOPIC_PICKUP_ORDER, value); }

    public String getKafkaTopicProductRestriction() { return getStringValue(KEY_KAFKA_TOPIC_PRODUCT_RESTRICTION); }
    public void setKafkaTopicProductRestriction(String value) { setStringValue(KEY_KAFKA_TOPIC_PRODUCT_RESTRICTION, value); }

    // Kafka Topics - Outgoing
    public String getKafkaTopicIngotPickedUp() { return getStringValue(KEY_KAFKA_TOPIC_INGOT_PICKED_UP); }
    public void setKafkaTopicIngotPickedUp(String value) { setStringValue(KEY_KAFKA_TOPIC_INGOT_PICKED_UP, value); }

    public String getKafkaTopicIngotMoved() { return getStringValue(KEY_KAFKA_TOPIC_INGOT_MOVED); }
    public void setKafkaTopicIngotMoved(String value) { setStringValue(KEY_KAFKA_TOPIC_INGOT_MOVED, value); }

    public String getKafkaTopicIngotModified() { return getStringValue(KEY_KAFKA_TOPIC_INGOT_MODIFIED); }
    public void setKafkaTopicIngotModified(String value) { setStringValue(KEY_KAFKA_TOPIC_INGOT_MODIFIED, value); }

    public String getKafkaTopicShipmentCompleted() { return getStringValue(KEY_KAFKA_TOPIC_SHIPMENT_COMPLETED); }
    public void setKafkaTopicShipmentCompleted(String value) { setStringValue(KEY_KAFKA_TOPIC_SHIPMENT_COMPLETED, value); }

    public String getKafkaTopicSawFeedback() { return getStringValue(KEY_KAFKA_TOPIC_SAW_FEEDBACK); }
    public void setKafkaTopicSawFeedback(String value) { setStringValue(KEY_KAFKA_TOPIC_SAW_FEEDBACK, value); }

    // === Hilfsmethoden für Koordinaten-Konvertierung ===

    /**
     * Konvertiert Grid-X-Koordinate zu mm-Position
     */
    public int gridToMmX(int gridX) {
        int lagerMaxX = getLagerMaxX();
        int lagerMinX = getLagerMinX();
        int gridCols = getGridCols();
        int range = lagerMaxX - lagerMinX;
        if (gridCols <= 1) return lagerMaxX;
        return lagerMaxX - ((gridX - 1) * range / (gridCols - 1));
    }

    /**
     * Konvertiert Grid-Y-Koordinate zu mm-Position
     */
    public int gridToMmY(int gridY) {
        int lagerMaxY = getLagerMaxY();
        int lagerMinY = getLagerMinY();
        int gridRows = getGridRows();
        int range = lagerMaxY - lagerMinY;
        if (gridRows <= 1) return lagerMinY;
        return lagerMinY + ((gridY - 1) * range / (gridRows - 1));
    }

    /**
     * Konvertiert mm-X-Position zu Grid-X-Koordinate
     */
    public int mmToGridX(int mmX) {
        int lagerMaxX = getLagerMaxX();
        int lagerMinX = getLagerMinX();
        int gridCols = getGridCols();
        int range = lagerMaxX - lagerMinX;
        if (range <= 0) return 1;
        int gridX = 1 + ((lagerMaxX - mmX) * (gridCols - 1) / range);
        return Math.max(1, Math.min(gridCols, gridX));
    }

    /**
     * Konvertiert mm-Y-Position zu Grid-Y-Koordinate
     */
    public int mmToGridY(int mmY) {
        int lagerMaxY = getLagerMaxY();
        int lagerMinY = getLagerMinY();
        int gridRows = getGridRows();
        int range = lagerMaxY - lagerMinY;
        if (range <= 0) return 1;
        int gridY = 1 + ((mmY - lagerMinY) * (gridRows - 1) / range);
        return Math.max(1, Math.min(gridRows, gridY));
    }

    /**
     * Lädt alle Einstellungen neu aus der Datenbank
     */
    public void reload() {
        log.info("Reloading settings from database...");
        loadAllSettings();
    }

    // === Inner Classes ===

    private static class SettingDefinition {
        final int defaultValue;
        final String category;
        final String description;

        SettingDefinition(int defaultValue, String category, String description) {
            this.defaultValue = defaultValue;
            this.category = category;
            this.description = description;
        }
    }

    private static class StringSettingDefinition {
        final String defaultValue;
        final String category;
        final String description;

        StringSettingDefinition(String defaultValue, String category, String description) {
            this.defaultValue = defaultValue;
            this.category = category;
            this.description = description;
        }
    }
}
