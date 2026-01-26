package com.hydro.plsbl.service;

import com.hydro.plsbl.dto.CalloffDTO;
import com.hydro.plsbl.dto.CalloffSearchCriteria;
import com.hydro.plsbl.entity.transdata.Calloff;
import com.hydro.plsbl.repository.CalloffRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Service für Abruf-Operationen (Calloff)
 */
@Service
@Transactional(readOnly = true)
public class CalloffService {

    private static final Logger log = LoggerFactory.getLogger(CalloffService.class);

    private final CalloffRepository calloffRepository;
    private final JdbcTemplate jdbcTemplate;
    private final DataBroadcaster dataBroadcaster;

    public CalloffService(CalloffRepository calloffRepository, JdbcTemplate jdbcTemplate,
                          DataBroadcaster dataBroadcaster) {
        this.calloffRepository = calloffRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.dataBroadcaster = dataBroadcaster;
    }

    /**
     * Initialisiert Testdaten falls keine vorhanden
     */
    @PostConstruct
    @Transactional
    public void initTestDataIfEmpty() {
        try {
            // Prüfen ob Tabelle existiert und leer ist
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM TD_CALLOFF", Integer.class);

            if (count == null || count == 0) {
                log.info("Keine Calloff-Daten gefunden, erstelle Testdaten...");
                createTestData();
            } else {
                log.info("Calloff-Tabelle enthält {} Einträge", count);
            }

            // Spalten der Tabelle loggen
            logTableColumns();

        } catch (Exception e) {
            log.warn("Konnte Calloff-Daten nicht prüfen (Tabelle existiert evtl. nicht): {}", e.getMessage());
            // Tabelle existiert nicht - erstellen wir sie
            createTableAndTestData();
        }
    }

    /**
     * Erstellt Test-Abrufe zum Testen der Lieferfunktion.
     * Findet alle Produkte mit Barren im Lager und erstellt je einen Abruf.
     */
    @Transactional
    public void createTestCalloff() {
        try {
            log.info("=== Erstelle Test-Abrufe für alle Produkte mit Barren im Lager ===");

            // Alle Produkte finden, die Barren im Lager haben
            List<Long> productIds = new ArrayList<>();
            List<String> productInfos = new ArrayList<>();

            try {
                jdbcTemplate.query(
                    "SELECT i.PRODUCT_ID, p.PRODUCT_NO, COUNT(*) as CNT " +
                    "FROM TD_INGOT i LEFT JOIN MD_PRODUCT p ON i.PRODUCT_ID = p.ID " +
                    "WHERE i.STOCKYARD_ID IS NOT NULL AND i.PRODUCT_ID IS NOT NULL " +
                    "GROUP BY i.PRODUCT_ID, p.PRODUCT_NO ORDER BY CNT DESC",
                    rs -> {
                        Long productId = rs.getLong("PRODUCT_ID");
                        String productNo = rs.getString("PRODUCT_NO");
                        int count = rs.getInt("CNT");
                        productIds.add(productId);
                        productInfos.add(String.format("PRODUCT_ID=%d (%s): %d Barren", productId, productNo, count));
                    });
                log.info("Produkte mit Barren im Lager: {}", productInfos);
            } catch (Exception e) {
                log.warn("Konnte Produkte mit Barren nicht finden: {}", e.getMessage());
            }

            // Fallback: Falls keine Produkte mit Barren, alle Barren ohne Produkt-Filter verfügbar machen
            if (productIds.isEmpty()) {
                log.warn("Keine Produkte mit Barren gefunden, erstelle Fallback-Abruf ohne PRODUCT_ID");
                createSingleTestCalloff(null, "TEST-001");
                return;
            }

            // Für jedes Produkt einen Abruf erstellen
            for (Long productId : productIds) {
                String productNo = null;
                try {
                    productNo = jdbcTemplate.queryForObject(
                        "SELECT PRODUCT_NO FROM MD_PRODUCT WHERE ID = ?", String.class, productId);
                } catch (Exception e) {
                    productNo = "PROD-" + productId;
                }

                // SAP_PRODUCT_NO auf 9 Zeichen begrenzen
                String sapProductNo = productNo != null && productNo.length() > 9
                    ? productNo.substring(0, 9) : productNo;

                createSingleTestCalloff(productId, sapProductNo);
            }

            log.info("=== {} Test-Abrufe erstellt ===", productIds.size());

        } catch (Exception e) {
            log.error("Fehler beim Erstellen der Test-Abrufe: {}", e.getMessage(), e);
        }
    }

    /**
     * Erstellt einen einzelnen Test-Abruf
     */
    private void createSingleTestCalloff(Long productId, String sapProductNo) {
        try {
            // Nächste ID ermitteln
            Long maxId = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(ID), 0) + 1 FROM TD_CALLOFF", Long.class);
            if (maxId == null) maxId = 1L;

            // Anzahl Barren für dieses Produkt ermitteln
            int barrenCount = 5;
            if (productId != null) {
                try {
                    Integer count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM TD_INGOT WHERE PRODUCT_ID = ? AND STOCKYARD_ID IS NOT NULL",
                        Integer.class, productId);
                    barrenCount = count != null ? Math.min(count, 10) : 5;
                } catch (Exception e) {
                    log.debug("Konnte Barren-Anzahl nicht ermitteln: {}", e.getMessage());
                }
            }

            String sql = """
                INSERT INTO TD_CALLOFF (ID, SERIAL, CALLOFF_NO, ORDER_NO, ORDER_POS,
                    CUSTOMER_NO, CUSTOMER_ADDRESS, DESTINATION, SAP_PRODUCT_NO, PRODUCT_ID,
                    AMOUNT_REQUESTED, AMOUNT_DELIVERED, DELIVERY, APPROVED, COMPLETED, RECEIVED, NORMTEXT)
                VALUES (?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, 1, 0, ?, ?)
                """;

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime deliveryDate = now.plusDays(2);

            jdbcTemplate.update(sql,
                maxId,
                String.valueOf(99000 + maxId),  // CALLOFF_NO (max 10 Zeichen)
                "45099" + String.format("%03d", maxId % 1000),  // ORDER_NO (max 8 Zeichen)
                "10",                      // ORDER_POS
                "100999",                  // CUSTOMER_NO
                "Test Adresse\n12345 Teststadt",  // CUSTOMER_ADDRESS
                "NF2",                     // DESTINATION
                sapProductNo,              // SAP_PRODUCT_NO
                productId,                 // PRODUCT_ID
                barrenCount,               // AMOUNT_REQUESTED
                java.sql.Timestamp.valueOf(deliveryDate),  // DELIVERY
                java.sql.Timestamp.valueOf(now),           // RECEIVED
                "Test-Abruf für Lieferung" // NORMTEXT
            );

            log.info("Test-Abruf {} erstellt: {} Barren bestellt, PRODUCT_ID={} ({}), Liefertermin {}, Ziel NF2",
                99000 + maxId, barrenCount, productId, sapProductNo, deliveryDate.toLocalDate());

        } catch (Exception e) {
            log.error("Fehler beim Erstellen des Test-Abrufs: {}", e.getMessage());
        }
    }

    /**
     * Loggt die Spaltennamen der TD_CALLOFF Tabelle
     */
    private void logTableColumns() {
        try {
            List<String> columns = jdbcTemplate.query(
                "SELECT COLUMN_NAME FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'TD_CALLOFF' ORDER BY COLUMN_ID",
                (rs, rowNum) -> rs.getString("COLUMN_NAME"));
            log.info("TD_CALLOFF Spalten: {}", columns);

            // Datenverteilung loggen
            Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM TD_CALLOFF", Integer.class);
            Integer completed = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM TD_CALLOFF WHERE COMPLETED = 1", Integer.class);
            Integer incomplete = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM TD_CALLOFF WHERE COMPLETED = 0 OR COMPLETED IS NULL", Integer.class);

            // Diagnose: Welche Produkte haben die Barren im Lager?
            try {
                List<String> ingotProducts = jdbcTemplate.query(
                    "SELECT DISTINCT i.PRODUCT_ID, p.PRODUCT_NO, COUNT(*) as CNT " +
                    "FROM TD_INGOT i LEFT JOIN MD_PRODUCT p ON i.PRODUCT_ID = p.ID " +
                    "WHERE i.STOCKYARD_ID IS NOT NULL GROUP BY i.PRODUCT_ID, p.PRODUCT_NO",
                    (rs, rowNum) -> "PRODUCT_ID=" + rs.getLong("PRODUCT_ID") +
                                   " (" + rs.getString("PRODUCT_NO") + "): " + rs.getInt("CNT") + " Barren");
                log.info("Barren im Lager nach Produkt: {}", ingotProducts);
            } catch (Exception e) {
                log.warn("Konnte Barren-Produkte nicht abfragen: {}", e.getMessage());
            }
            Integer approved = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM TD_CALLOFF WHERE APPROVED = 1", Integer.class);
            log.info("TD_CALLOFF Daten: Gesamt={}, Erledigt={}, Offen={}, Genehmigt={}", total, completed, incomplete, approved);

            // Alte Test-Abrufe löschen (haben evtl. falsche PRODUCT_ID)
            try {
                int deleted = jdbcTemplate.update("DELETE FROM TD_CALLOFF WHERE NORMTEXT = 'Test-Abruf für Lieferung'");
                if (deleted > 0) {
                    log.info("Alte Test-Abrufe gelöscht: {}", deleted);
                }
            } catch (Exception e) {
                log.warn("Konnte alte Test-Abrufe nicht löschen: {}", e.getMessage());
            }

            // Test-Abruf erstellen falls keine offenen vorhanden
            incomplete = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM TD_CALLOFF WHERE COMPLETED = 0 OR COMPLETED IS NULL", Integer.class);
            if (incomplete == null || incomplete == 0) {
                createTestCalloff();
            }
        } catch (Exception e) {
            log.warn("Konnte Spalten/Daten nicht abfragen: {}", e.getMessage());
        }
    }

    /**
     * Erstellt die Tabelle und Testdaten
     */
    @Transactional
    public void createTableAndTestData() {
        try {
            log.info("Erstelle TD_CALLOFF Tabelle...");

            // Alte Tabelle löschen falls vorhanden (für Schema-Migration)
            try {
                jdbcTemplate.execute("DROP TABLE IF EXISTS TD_CALLOFF");
            } catch (Exception e) {
                log.debug("Tabelle existierte nicht: {}", e.getMessage());
            }

            // Tabelle erstellen (H2-kompatibel mit BOOLEAN statt NUMBER)
            jdbcTemplate.execute("""
                CREATE TABLE TD_CALLOFF (
                    ID BIGINT PRIMARY KEY,
                    SERIAL BIGINT DEFAULT 1,
                    TABLESERIAL BIGINT,
                    CALLOFF_NUMBER VARCHAR(50),
                    ORDER_NUMBER VARCHAR(50),
                    ORDER_POSITION VARCHAR(20),
                    CUSTOMER_NUMBER VARCHAR(50),
                    CUSTOMER_NAME VARCHAR(200),
                    CUSTOMER_ADDRESS VARCHAR(500),
                    DESTINATION VARCHAR(50),
                    SAP_PRODUCT_NUMBER VARCHAR(50),
                    PRODUCT_ID BIGINT,
                    AMOUNT_REQUESTED INT,
                    AMOUNT_DELIVERED INT DEFAULT 0,
                    DELIVERY_DATE DATE,
                    APPROVED BOOLEAN DEFAULT FALSE,
                    COMPLETED BOOLEAN DEFAULT FALSE,
                    RECEIVED TIMESTAMP,
                    NOTES VARCHAR(1000)
                )
                """);

            log.info("TD_CALLOFF Tabelle erstellt");
            createTestData();

        } catch (Exception e) {
            log.error("Fehler beim Erstellen der TD_CALLOFF Tabelle: {}", e.getMessage());
        }
    }

    /**
     * Erstellt Testdaten für die Beladungs-Simulation
     */
    @Transactional
    public void createTestData() {
        log.info("Erstelle Calloff-Testdaten...");

        try {
            String sql = """
                INSERT INTO TD_CALLOFF (ID, SERIAL, TABLESERIAL, CALLOFF_NUMBER, ORDER_NUMBER, ORDER_POSITION,
                    CUSTOMER_NUMBER, CUSTOMER_NAME, CUSTOMER_ADDRESS, DESTINATION, SAP_PRODUCT_NUMBER,
                    AMOUNT_REQUESTED, AMOUNT_DELIVERED, DELIVERY_DATE, APPROVED, COMPLETED, RECEIVED)
                VALUES (?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

            LocalDateTime now = LocalDateTime.now();
            LocalDate today = LocalDate.now();

            // Testdaten für verschiedene Kunden und Ziele
            Object[][] testData = {
                // ID, TableSerial, CalloffNo, OrderNo, OrderPos, CustomerNo, CustomerName, Address, Dest, SapProduct, Requested, Delivered, DeliveryDate, Approved, Completed
                {1L, 1L, "ABR-2025-001", "4500001234", "10", "100123", "Hydro Aluminium DE", "Koblenzer Str. 122, 41468 Neuss", "NF2", "MAT-AL-001", 6, 0, today.plusDays(1), true, false},
                {2L, 2L, "ABR-2025-002", "4500001234", "20", "100123", "Hydro Aluminium DE", "Koblenzer Str. 122, 41468 Neuss", "NF2", "MAT-AL-002", 4, 0, today.plusDays(1), true, false},
                {3L, 3L, "ABR-2025-003", "4500001235", "10", "100456", "Aluminium Werk Köln", "Industriestr. 50, 50667 Köln", "LKW", "MAT-AL-001", 8, 2, today, true, false},
                {4L, 4L, "ABR-2025-004", "4500001236", "10", "100789", "Metallbau Schmidt", "Hauptstr. 1, 40210 Düsseldorf", "EXT", "MAT-AL-003", 3, 0, today.plusDays(3), true, false},
                {5L, 5L, "ABR-2025-005", "4500001237", "10", "100111", "Stahlhandel Meyer", "Hafenstr. 25, 47119 Duisburg", "NF2", "MAT-AL-001", 5, 0, today.plusDays(2), true, false},
                {6L, 6L, "ABR-2025-006", "4500001238", "10", "100222", "Automotive GmbH", "Motorweg 10, 44135 Dortmund", "LKW", "MAT-AL-002", 10, 4, today, true, false},
                {7L, 7L, "ABR-2025-007", "4500001239", "10", "100333", "Bau AG", "Betonstr. 5, 45127 Essen", "NF2", "MAT-AL-004", 2, 0, today.plusDays(5), false, false},  // Nicht genehmigt
                {8L, 8L, "ABR-2025-008", "4500001240", "10", "100444", "Technik Plus", "Innovationspark 12, 52064 Aachen", "LKW", "MAT-AL-001", 6, 6, today.minusDays(1), true, true},  // Abgeschlossen
                {9L, 9L, "ABR-2025-009", "4500001241", "10", "100555", "Metall Express", "Schnellweg 99, 41061 Mönchengladbach", "EXT", "MAT-AL-005", 4, 0, today.plusDays(1), true, false},
                {10L, 10L, "ABR-2025-010", "4500001242", "10", "100666", "Industrie Nord", "Fabrikstr. 77, 47798 Krefeld", "NF2", "MAT-AL-001", 7, 3, today, true, false},
            };

            for (Object[] data : testData) {
                jdbcTemplate.update(sql,
                    data[0], data[1], data[2], data[3], data[4],
                    data[5], data[6], data[7], data[8], data[9],
                    data[10], data[11], data[12], data[13], data[14], now);
            }

            log.info("{} Calloff-Testdaten erstellt", testData.length);

        } catch (Exception e) {
            log.error("Fehler beim Erstellen der Calloff-Testdaten: {}", e.getMessage(), e);
        }
    }

    // === Query Methods ===

    /**
     * Findet alle Abrufe
     */
    public List<CalloffDTO> findAll() {
        return StreamSupport.stream(calloffRepository.findAll().spliterator(), false)
            .map(CalloffDTO::new)
            .collect(Collectors.toList());
    }

    /**
     * Findet alle Abrufe sortiert
     */
    public List<CalloffDTO> findAllOrdered() {
        return calloffRepository.findAllOrdered().stream()
            .map(CalloffDTO::new)
            .collect(Collectors.toList());
    }

    /**
     * Findet einen Abruf nach ID
     */
    public Optional<CalloffDTO> findById(Long id) {
        return calloffRepository.findById(id)
            .map(CalloffDTO::new);
    }

    /**
     * Findet einen Abruf nach Abrufnummer
     */
    public Optional<CalloffDTO> findByCalloffNumber(String calloffNumber) {
        return calloffRepository.findByCalloffNumber(calloffNumber)
            .map(CalloffDTO::new);
    }

    /**
     * Findet alle offenen (nicht abgeschlossenen) Abrufe
     */
    public List<CalloffDTO> findIncomplete() {
        return calloffRepository.findIncomplete().stream()
            .map(CalloffDTO::new)
            .collect(Collectors.toList());
    }

    /**
     * Findet alle lieferbaren Abrufe (genehmigt, nicht abgeschlossen)
     */
    public List<CalloffDTO> findDeliverable() {
        return calloffRepository.findDeliverable().stream()
            .map(CalloffDTO::new)
            .collect(Collectors.toList());
    }

    /**
     * Findet alle lieferbaren Abrufe für ein bestimmtes Ziel
     */
    public List<CalloffDTO> findDeliverableByDestination(String destination) {
        return calloffRepository.findByDestination(destination).stream()
            .map(CalloffDTO::new)
            .filter(CalloffDTO::isDeliverable)
            .collect(Collectors.toList());
    }

    /**
     * Findet alle nicht genehmigten Abrufe
     */
    public List<CalloffDTO> findNotApproved() {
        return calloffRepository.findNotApproved().stream()
            .map(CalloffDTO::new)
            .collect(Collectors.toList());
    }

    /**
     * Findet Abrufe nach Kundennummer
     */
    public List<CalloffDTO> findByCustomerNumber(String customerNumber) {
        return calloffRepository.findByCustomerNumber(customerNumber).stream()
            .map(CalloffDTO::new)
            .collect(Collectors.toList());
    }

    /**
     * Findet fällige Abrufe bis zu einem Datum
     */
    public List<CalloffDTO> findDueUntil(LocalDate toDate) {
        return calloffRepository.findDueUntil(toDate).stream()
            .map(CalloffDTO::new)
            .collect(Collectors.toList());
    }

    /**
     * Sucht Abrufe nach Muster
     */
    public List<CalloffDTO> search(String pattern) {
        String searchPattern = "%" + (pattern != null ? pattern : "") + "%";
        return calloffRepository.searchByPattern(searchPattern).stream()
            .map(CalloffDTO::new)
            .collect(Collectors.toList());
    }

    /**
     * Gibt alle eindeutigen Ziele zurück
     */
    public List<String> findAllDestinations() {
        try {
            return jdbcTemplate.queryForList(
                "SELECT DISTINCT DESTINATION FROM TD_CALLOFF WHERE DESTINATION IS NOT NULL ORDER BY DESTINATION",
                String.class);
        } catch (Exception e) {
            log.warn("Konnte Destinations nicht laden: {}", e.getMessage());
            return List.of("NF2", "LKW", "EXT");
        }
    }

    // === Mutation Methods ===

    /**
     * Genehmigt einen Abruf
     */
    @Transactional
    public void approve(Long calloffId) {
        log.info("approve() aufgerufen für ID: {}", calloffId);
        calloffRepository.findById(calloffId).ifPresent(calloff -> {
            calloff.setApproved(true);
            calloff.markNotNew();
            calloffRepository.save(calloff);
            log.info("Calloff {} genehmigt, sende Broadcast...", calloff.getCalloffNumber());
            dataBroadcaster.broadcast(DataBroadcaster.DataEventType.CALLOFF_CHANGED);
            log.info("Broadcast CALLOFF_CHANGED gesendet");
        });
    }

    /**
     * Widerruft die Genehmigung eines Abrufs
     */
    @Transactional
    public void revokeApproval(Long calloffId) {
        log.info("revokeApproval() aufgerufen für ID: {}", calloffId);
        calloffRepository.findById(calloffId).ifPresent(calloff -> {
            calloff.setApproved(false);
            calloff.markNotNew();
            calloffRepository.save(calloff);
            log.info("Genehmigung für Calloff {} widerrufen, sende Broadcast...", calloff.getCalloffNumber());
            dataBroadcaster.broadcast(DataBroadcaster.DataEventType.CALLOFF_CHANGED);
            log.info("Broadcast CALLOFF_CHANGED gesendet");
        });
    }

    /**
     * Markiert einen Abruf als abgeschlossen
     */
    @Transactional
    public void complete(Long calloffId) {
        log.info("complete() aufgerufen für ID: {}", calloffId);
        calloffRepository.findById(calloffId).ifPresent(calloff -> {
            calloff.setCompleted(true);
            calloff.markNotNew();
            calloffRepository.save(calloff);
            log.info("Calloff {} abgeschlossen, sende Broadcast...", calloff.getCalloffNumber());
            dataBroadcaster.broadcast(DataBroadcaster.DataEventType.CALLOFF_CHANGED);
            log.info("Broadcast CALLOFF_CHANGED gesendet");
        });
    }

    /**
     * Erhöht die gelieferte Menge
     */
    @Transactional
    public void addDelivered(Long calloffId, int amount) {
        calloffRepository.findById(calloffId).ifPresent(calloff -> {
            int delivered = calloff.getAmountDelivered() != null ? calloff.getAmountDelivered() : 0;
            calloff.setAmountDelivered(delivered + amount);

            // Automatisch abschließen wenn vollständig geliefert
            if (calloff.getRemainingAmount() <= 0) {
                calloff.setCompleted(true);
            }

            calloff.markNotNew();
            calloffRepository.save(calloff);
            log.info("Calloff {} - {} Einheiten geliefert (gesamt: {})",
                calloff.getCalloffNumber(), amount, calloff.getAmountDelivered());
            dataBroadcaster.broadcast(DataBroadcaster.DataEventType.CALLOFF_CHANGED);
        });
    }

    /**
     * Erhöht die gelieferte Menge (SQL-Version für Oracle)
     */
    @Transactional
    public void addDeliveredAmount(Long calloffId, int amount) {
        log.info("addDeliveredAmount: calloffId={}, amount={}", calloffId, amount);

        // Aktuelle Werte holen
        Integer currentDelivered = jdbcTemplate.queryForObject(
            "SELECT AMOUNT_DELIVERED FROM TD_CALLOFF WHERE ID = ?", Integer.class, calloffId);
        Integer requested = jdbcTemplate.queryForObject(
            "SELECT AMOUNT_REQUESTED FROM TD_CALLOFF WHERE ID = ?", Integer.class, calloffId);

        int newDelivered = (currentDelivered != null ? currentDelivered : 0) + amount;
        int remaining = (requested != null ? requested : 0) - newDelivered;

        // Update mit Oracle-Spaltennamen
        if (remaining <= 0) {
            // Automatisch abschließen wenn vollständig geliefert
            jdbcTemplate.update(
                "UPDATE TD_CALLOFF SET AMOUNT_DELIVERED = ?, COMPLETED = 1, SERIAL = SERIAL + 1 WHERE ID = ?",
                newDelivered, calloffId);
            log.info("Calloff {} vollständig geliefert und abgeschlossen ({}/{})",
                calloffId, newDelivered, requested);
        } else {
            jdbcTemplate.update(
                "UPDATE TD_CALLOFF SET AMOUNT_DELIVERED = ?, SERIAL = SERIAL + 1 WHERE ID = ?",
                newDelivered, calloffId);
            log.info("Calloff {} - {} Einheiten geliefert (gesamt: {}, offen: {})",
                calloffId, amount, newDelivered, remaining);
        }

        dataBroadcaster.broadcast(DataBroadcaster.DataEventType.CALLOFF_CHANGED);
    }

    /**
     * Zählt alle Abrufe
     */
    public int countAll() {
        try {
            return calloffRepository.countAll();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Zählt offene Abrufe
     */
    public int countIncomplete() {
        try {
            return calloffRepository.countIncomplete();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Sucht Abrufe nach erweiterten Kriterien (wie in der Original-Applikation)
     * Verwendet die tatsächlichen Oracle-Spaltennamen:
     * CALLOFF_NO, ORDER_NO, ORDER_POS, CUSTOMER_NO, SAP_PRODUCT_NO, DELIVERY
     */
    public List<CalloffDTO> searchByCriteria(CalloffSearchCriteria criteria) {
        StringBuilder sql = new StringBuilder("SELECT c.* FROM TD_CALLOFF c WHERE 1=1");
        List<Object> params = new ArrayList<>();

        // Text-Filter (Oracle-Spaltennamen)
        if (criteria.getCalloffNumber() != null && !criteria.getCalloffNumber().isEmpty()) {
            sql.append(" AND c.CALLOFF_NO LIKE ?");
            params.add("%" + criteria.getCalloffNumber() + "%");
        }

        if (criteria.getOrderNumber() != null && !criteria.getOrderNumber().isEmpty()) {
            sql.append(" AND c.ORDER_NO LIKE ?");
            params.add("%" + criteria.getOrderNumber() + "%");
        }

        if (criteria.getOrderPosition() != null && !criteria.getOrderPosition().isEmpty()) {
            sql.append(" AND c.ORDER_POS LIKE ?");
            params.add("%" + criteria.getOrderPosition() + "%");
        }

        if (criteria.getCustomerNumber() != null && !criteria.getCustomerNumber().isEmpty()) {
            sql.append(" AND c.CUSTOMER_NO LIKE ?");
            params.add("%" + criteria.getCustomerNumber() + "%");
        }

        if (criteria.isNoDestination()) {
            sql.append(" AND (c.DESTINATION IS NULL OR c.DESTINATION = '')");
        } else if (criteria.getDestination() != null && !criteria.getDestination().isEmpty()) {
            sql.append(" AND c.DESTINATION LIKE ?");
            params.add("%" + criteria.getDestination() + "%");
        }

        if (criteria.getSapProductNumber() != null && !criteria.getSapProductNumber().isEmpty()) {
            sql.append(" AND c.SAP_PRODUCT_NO LIKE ?");
            params.add("%" + criteria.getSapProductNumber() + "%");
        }

        // Artikel (Produkt-Nummer aus MD_PRODUCT verknüpfen wenn vorhanden)
        if (criteria.getProductNumber() != null && !criteria.getProductNumber().isEmpty()) {
            sql.append(" AND (c.SAP_PRODUCT_NO LIKE ? OR EXISTS (SELECT 1 FROM MD_PRODUCT p WHERE p.ID = c.PRODUCT_ID AND p.PRODUCT_NUMBER LIKE ?))");
            params.add("%" + criteria.getProductNumber() + "%");
            params.add("%" + criteria.getProductNumber() + "%");
        }

        // Liefertermin-Filter (Oracle Spalte: DELIVERY)
        if (criteria.getDeliveryDateFrom() != null) {
            sql.append(" AND c.DELIVERY >= ?");
            params.add(java.sql.Timestamp.valueOf(criteria.getDeliveryDateFrom()));
        }

        if (criteria.getDeliveryDateTo() != null) {
            sql.append(" AND c.DELIVERY <= ?");
            params.add(java.sql.Timestamp.valueOf(criteria.getDeliveryDateTo()));
        }

        // Suchmuster (durchsucht mehrere Felder mit Oracle-Spaltennamen)
        if (criteria.getSearchPattern() != null && !criteria.getSearchPattern().isEmpty()) {
            sql.append(" AND (c.CALLOFF_NO LIKE ? OR c.ORDER_NO LIKE ? OR c.CUSTOMER_NO LIKE ? OR c.SAP_PRODUCT_NO LIKE ? OR c.DESTINATION LIKE ?)");
            String pattern = "%" + criteria.getSearchPattern() + "%";
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }

        // Checkbox-Filter (NUMBER(1)-Spalten in Oracle: 0=false, 1=true)
        if (criteria.isIncompleteOnly()) {
            sql.append(" AND (c.COMPLETED = 0 OR c.COMPLETED IS NULL)");
        }

        if (criteria.isCompletedOnly()) {
            sql.append(" AND c.COMPLETED = 1");
        }

        if (criteria.isApprovedOnly()) {
            sql.append(" AND c.APPROVED = 1");
        }

        if (criteria.isNotApprovedOnly()) {
            sql.append(" AND (c.APPROVED = 0 OR c.APPROVED IS NULL)");
        }

        sql.append(" ORDER BY c.DELIVERY ASC NULLS LAST, c.ID ASC");

        log.info("Calloff-Suche SQL: {}", sql);
        log.info("Calloff-Suche Parameter: {}", params);
        log.info("Criteria: incompleteOnly={}, completedOnly={}, notApprovedOnly={}",
            criteria.isIncompleteOnly(), criteria.isCompletedOnly(), criteria.isNotApprovedOnly());

        try {
            List<CalloffDTO> results = jdbcTemplate.query(sql.toString(), params.toArray(), new CalloffRowMapper());
            log.info("Calloff-Suche ergab {} Treffer", results.size());
            return results;
        } catch (Exception e) {
            log.error("Fehler bei der Suche nach Abrufen: {} - SQL: {}", e.getMessage(), sql, e);
            return List.of();
        }
    }

    /**
     * RowMapper für Calloff-Abfragen
     * Verwendet die tatsächlichen Oracle-Spaltennamen:
     * CALLOFF_NO, ORDER_NO, ORDER_POS, CUSTOMER_NO, SAP_PRODUCT_NO, DELIVERY, NORMTEXT
     */
    private static class CalloffRowMapper implements RowMapper<CalloffDTO> {
        @Override
        public CalloffDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
            CalloffDTO dto = new CalloffDTO();
            dto.setId(rs.getLong("ID"));
            // TABLESERIAL existiert nicht in Oracle DB
            dto.setCalloffNumber(rs.getString("CALLOFF_NO"));
            dto.setOrderNumber(rs.getString("ORDER_NO"));
            dto.setOrderPosition(rs.getString("ORDER_POS"));
            dto.setCustomerNumber(rs.getString("CUSTOMER_NO"));
            // CUSTOMER_NAME existiert nicht, verwende NORMTEXT als Fallback
            dto.setCustomerName(rs.getString("NORMTEXT"));
            dto.setCustomerAddress(rs.getString("CUSTOMER_ADDRESS"));
            dto.setDestination(rs.getString("DESTINATION"));
            dto.setSapProductNumber(rs.getString("SAP_PRODUCT_NO"));
            dto.setProductId(rs.getObject("PRODUCT_ID", Long.class));
            dto.setAmountRequested(rs.getObject("AMOUNT_REQUESTED", Integer.class));
            dto.setAmountDelivered(rs.getObject("AMOUNT_DELIVERED", Integer.class));

            // Oracle Spalte: DELIVERY (kann DATE oder TIMESTAMP sein)
            java.sql.Timestamp delivery = rs.getTimestamp("DELIVERY");
            if (delivery != null) {
                dto.setDeliveryDate(delivery.toLocalDateTime().toLocalDate());
            }

            // Oracle speichert Boolean als NUMBER(1): 0=false, 1=true
            Integer approvedInt = rs.getObject("APPROVED", Integer.class);
            dto.setApproved(approvedInt != null && approvedInt == 1);
            Integer completedInt = rs.getObject("COMPLETED", Integer.class);
            dto.setCompleted(completedInt != null && completedInt == 1);

            java.sql.Timestamp received = rs.getTimestamp("RECEIVED");
            if (received != null) {
                dto.setReceived(received.toLocalDateTime());
            }

            // NOTES existiert nicht in Oracle DB
            return dto;
        }
    }
}
