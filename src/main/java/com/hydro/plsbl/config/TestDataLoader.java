package com.hydro.plsbl.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Lädt Testdaten beim Start der Anwendung.
 * Deaktiviert - Oracle DB hat bereits echte Daten!
 */
// @Component  // Deaktiviert - echte Daten vorhanden
public class TestDataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TestDataLoader.class);

    private final JdbcTemplate jdbcTemplate;

    public TestDataLoader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        log.info("========================================");
        log.info("=== TestDataLoader: START ===");
        log.info("========================================");

        try {
            // Erst Ingots einfügen (werden von CraneCommand und TransportOrder referenziert)
            Integer ingotCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM TD_INGOT", Integer.class);
            log.info("TD_INGOT Count vor Insert: {}", ingotCount);

            if (ingotCount == null || ingotCount == 0) {
                loadIngots();
            }

            // Prüfe CraneCommands
            Integer commandCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM TD_CRANECOMMAND", Integer.class);
            log.info("TD_CRANECOMMAND Count vor Insert: {}", commandCount);

            if (commandCount == null || commandCount == 0) {
                loadCraneCommands();
            }

            // Prüfe TransportOrders
            Integer orderCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM TD_TRANSPORTORDER", Integer.class);
            log.info("TD_TRANSPORTORDER Count vor Insert: {}", orderCount);

            if (orderCount == null || orderCount == 0) {
                loadTransportOrders();
            }

            // Finale Prüfung
            log.info("========================================");
            log.info("=== FINALE ZÄHLUNG ===");
            log.info("TD_INGOT: {}", jdbcTemplate.queryForObject("SELECT COUNT(*) FROM TD_INGOT", Integer.class));
            log.info("TD_CRANECOMMAND: {}", jdbcTemplate.queryForObject("SELECT COUNT(*) FROM TD_CRANECOMMAND", Integer.class));
            log.info("TD_TRANSPORTORDER: {}", jdbcTemplate.queryForObject("SELECT COUNT(*) FROM TD_TRANSPORTORDER", Integer.class));
            log.info("========================================");
            log.info("=== TestDataLoader: FERTIG ===");
            log.info("========================================");

        } catch (Exception e) {
            log.error("!!! FEHLER beim Laden der Testdaten !!!", e);
        }
    }

    private void loadIngots() {
        log.info("Lade Ingot-Testdaten...");

        try {
            // Minimale Ingot-Daten (nur ID, SERIAL, INGOT_NO)
            String sql = "INSERT INTO TD_INGOT (ID, SERIAL, INGOT_NO) VALUES (?, 1, ?)";

            for (int i = 1; i <= 10; i++) {
                jdbcTemplate.update(sql, i, "BAR-" + String.format("%03d", i));
            }

            log.info("10 Ingots eingefügt");
        } catch (Exception e) {
            log.error("Fehler bei Ingot-Insert: {}", e.getMessage(), e);
        }
    }

    private void loadCraneCommands() {
        log.info("Lade CraneCommand-Testdaten...");

        try {
            // IDs 1-10 (kleine Zahlen), INGOT_ID referenziert die eingefügten Ingots
            String sql = "INSERT INTO TD_CRANECOMMAND (ID, SERIAL, TABLESERIAL, CMD_TYPE, CRANE_MODE, ROTATE, INGOT_ID) " +
                         "VALUES (?, 1, ?, ?, ?, ?, ?)";

            jdbcTemplate.update(sql, 1, 1, "M", "A", 0, 1);
            jdbcTemplate.update(sql, 2, 2, "M", "A", 90, 2);
            jdbcTemplate.update(sql, 3, 3, "M", "M", 0, 3);
            jdbcTemplate.update(sql, 4, 4, "P", "A", 0, 4);
            jdbcTemplate.update(sql, 5, 5, "P", "M", 0, 5);
            jdbcTemplate.update(sql, 6, 6, "D", "A", 0, 6);
            jdbcTemplate.update(sql, 7, 7, "D", "A", 90, 7);
            jdbcTemplate.update(sql, 8, 8, "K", "A", 0, 8);
            jdbcTemplate.update(sql, 9, 9, "R", "M", 180, 9);
            jdbcTemplate.update(sql, 10, 10, "M", "A", 270, 10);

            log.info("10 CraneCommands eingefügt");
        } catch (Exception e) {
            log.error("Fehler bei CraneCommand-Insert: {}", e.getMessage(), e);
        }
    }

    private void loadTransportOrders() {
        log.info("Lade TransportOrder-Testdaten...");

        try {
            // IDs 1-10, INGOT_ID ist NOT NULL
            String sql = "INSERT INTO TD_TRANSPORTORDER (ID, SERIAL, TABLESERIAL, TRANSPORT_NO, NORMTEXT, " +
                         "INGOT_ID, FROM_PILE_POSITION, TO_PILE_POSITION) VALUES (?, 1, ?, ?, ?, ?, ?, ?)";

            jdbcTemplate.update(sql, 1, 1, "TR24-001", "S235JR", 1, 1, 3);
            jdbcTemplate.update(sql, 2, 2, "TR24-002", "S355J2", 2, 1, 1);
            jdbcTemplate.update(sql, 3, 3, "TR24-003", "S235JR", 3, 1, 1);
            jdbcTemplate.update(sql, 4, 4, "TR24-004", "1.4301", 4, 1, 3);
            jdbcTemplate.update(sql, 5, 5, "TR24-005", "S355J2", 5, 1, 2);
            jdbcTemplate.update(sql, 6, 6, "TR24-006", "S235JR", 6, 1, 3);
            jdbcTemplate.update(sql, 7, 7, "TR24-007", "1.4301", 7, 2, 2);
            jdbcTemplate.update(sql, 8, 8, "TR24-008", "S355J2", 8, 2, 2);
            jdbcTemplate.update(sql, 9, 9, "TR24-009", "S235JR", 9, 2, 2);
            jdbcTemplate.update(sql, 10, 10, "TR24-010", "1.4571", 10, 1, 4);

            log.info("10 TransportOrders eingefügt");
        } catch (Exception e) {
            log.error("Fehler bei TransportOrder-Insert: {}", e.getMessage(), e);
        }
    }
}
