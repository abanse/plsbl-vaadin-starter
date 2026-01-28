package com.hydro.plsbl.service;

import com.hydro.plsbl.dto.IngotDTO;
import com.hydro.plsbl.entity.transdata.Ingot;
import com.hydro.plsbl.entity.transdata.Shipment;
import com.hydro.plsbl.entity.transdata.ShipmentLine;
import com.hydro.plsbl.repository.IngotRepository;
import com.hydro.plsbl.repository.ShipmentLineRepository;
import com.hydro.plsbl.repository.ShipmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service für Lieferschein-Verwaltung (Shipment)
 */
@Service
@Transactional(readOnly = true)
public class ShipmentService {

    private static final Logger log = LoggerFactory.getLogger(ShipmentService.class);

    private final ShipmentRepository shipmentRepository;
    private final ShipmentLineRepository shipmentLineRepository;
    private final IngotRepository ingotRepository;
    private final IngotService ingotService;
    private final ProductService productService;
    private final JdbcTemplate jdbcTemplate;

    public ShipmentService(ShipmentRepository shipmentRepository,
                          ShipmentLineRepository shipmentLineRepository,
                          IngotRepository ingotRepository,
                          IngotService ingotService,
                          ProductService productService,
                          JdbcTemplate jdbcTemplate) {
        this.shipmentRepository = shipmentRepository;
        this.shipmentLineRepository = shipmentLineRepository;
        this.ingotRepository = ingotRepository;
        this.ingotService = ingotService;
        this.productService = productService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Erstellt einen neuen Lieferschein für die angegebenen Barren
     */
    @Transactional
    public Shipment createShipment(String orderNumber, String destination,
                                   String customerNumber, String address,
                                   List<IngotDTO> ingots) {
        log.info("=== CREATING SHIPMENT START ===");
        log.info("Erstelle Lieferschein für {} Barren, Auftrag: {}, Ziel: {}, Kunde: {}",
            ingots.size(), orderNumber, destination, customerNumber);

        if (ingots == null || ingots.isEmpty()) {
            log.error("!!! KEINE BARREN FÜR LIEFERSCHEIN !!!");
            throw new IllegalArgumentException("Keine Barren für Lieferschein angegeben");
        }

        // Neue Lieferschein-Nummer generieren
        String shipmentNumber;
        try {
            shipmentNumber = generateShipmentNumber();
            log.info("Lieferschein-Nummer generiert: {}", shipmentNumber);
        } catch (Exception e) {
            log.error("Fehler bei Nummern-Generierung: {}", e.getMessage(), e);
            throw e;
        }

        // Lieferschein erstellen
        Shipment shipment = new Shipment();
        shipment.setShipmentNumber(shipmentNumber);
        shipment.setOrderNumber(orderNumber);
        shipment.setDestination(destination);
        shipment.setCustomerNumber(customerNumber);
        shipment.setAddress(address);
        shipment.setDelivered(LocalDateTime.now());

        // Speichern um ID zu bekommen
        try {
            shipment = saveShipment(shipment);
            log.info("Lieferschein gespeichert: ID={}", shipment.getId());
        } catch (Exception e) {
            log.error("!!! FEHLER BEIM SPEICHERN DES LIEFERSCHEINS !!!");
            log.error("SQL-Fehler: {}", e.getMessage(), e);
            throw e;
        }

        final Long shipmentId = shipment.getId();

        // Positionen erstellen
        int position = 1;
        for (IngotDTO ingot : ingots) {
            try {
                ShipmentLine line = new ShipmentLine();
                line.setShipmentId(shipmentId);
                line.setPosition(position++);
                line.setIngotId(ingot.getId());
                line.setIngotNumber(ingot.getIngotNo());
                line.setWeight(ingot.getWeight());
                line.setLength(ingot.getLength());
                line.setWidth(ingot.getWidth());
                line.setThickness(ingot.getThickness());
                line.setHeadSawn(ingot.getHeadSawn());
                line.setFootSawn(ingot.getFootSawn());
                line.setScrap(ingot.getScrap());
                line.setRevised(ingot.getRevised());
                line.setProductNumber(ingot.getProductNo());
                line.setSapProductNumber(ingot.getProductNo());

                saveShipmentLine(line);
                log.info("  Position {} gespeichert: Barren {}", position - 1, ingot.getIngotNo());

                // Barren als geliefert markieren (vom Lager entfernen falls noch nicht geschehen)
                markIngotAsDelivered(ingot.getId());
            } catch (Exception e) {
                log.error("!!! FEHLER BEI POSITION {} (Barren {}) !!!", position - 1, ingot.getIngotNo());
                log.error("SQL-Fehler: {}", e.getMessage(), e);
                throw e;
            }
        }

        log.info("=== SHIPMENT CREATED SUCCESSFULLY ===");
        log.info("Lieferschein {} erstellt mit {} Positionen, ID={}", shipmentNumber, ingots.size(), shipment.getId());
        return shipment;
    }

    /**
     * Markiert einen Barren als geliefert (entfernt ihn vom Lagerplatz).
     * Prüft ob der Barren noch auf einem Lagerplatz ist bevor relocate aufgerufen wird.
     */
    @Transactional
    public void markIngotAsDelivered(Long ingotId) {
        log.info("Markiere Barren {} als geliefert", ingotId);

        // Prüfen ob Barren noch auf einem Lagerplatz ist
        // (BeladungProcessorService hat ihn evtl. schon entfernt)
        ingotRepository.findById(ingotId).ifPresent(ingot -> {
            if (ingot.getStockyardId() != null) {
                // Barren ist noch auf Lagerplatz - entfernen
                log.info("  Barren {} ist noch auf Platz {}, entferne...", ingotId, ingot.getStockyardId());
                ingotService.relocate(ingotId, null);
            } else {
                log.info("  Barren {} ist bereits vom Lagerplatz entfernt", ingotId);
            }
        });

        // RELEASED_SINCE setzen
        ingotRepository.findById(ingotId).ifPresent(ingot -> {
            ingot.markNotNew();
            ingot.setReleasedSince(LocalDateTime.now());
            ingotRepository.save(ingot);
            log.info("Barren {} wurde als geliefert markiert", ingot.getIngotNo());
        });
    }

    /**
     * Generiert eine neue Lieferschein-Nummer
     */
    private String generateShipmentNumber() {
        try {
            // Versuche nächste Nummer aus DB zu holen
            Long nextNum = shipmentRepository.getNextShipmentNumber();
            return String.valueOf(nextNum);
        } catch (Exception e) {
            // Fallback: Timestamp-basiert
            return String.valueOf(System.currentTimeMillis() % 1000000);
        }
    }

    /**
     * Speichert einen Lieferschein (via direktes JDBC für Oracle-Kompatibilität)
     */
    @Transactional
    public Shipment saveShipment(Shipment shipment) {
        log.info("saveShipment called, shipmentNo={}, existingId={}", shipment.getShipmentNumber(), shipment.getId());

        if (shipment.getId() == null) {
            Long newId = getNextId("TD_SHIPMENT");
            shipment.setId(newId);
            log.info("Generated new ID: {}", newId);
        }

        String sql = """
            INSERT INTO TD_SHIPMENT
            (ID, SERIAL, SHIPMENT_NO, ORDER_NO, DESTINATION, CUSTOMER_NO, ADDRESS, PRINTED, DELIVERED)
            VALUES (?, 1, ?, ?, ?, ?, ?, ?, ?)
            """;

        jdbcTemplate.update(sql,
            shipment.getId(),
            shipment.getShipmentNumber(),
            shipment.getOrderNumber(),
            shipment.getDestination(),
            shipment.getCustomerNumber(),
            shipment.getAddress(),
            shipment.getPrinted() != null ? Timestamp.valueOf(shipment.getPrinted()) : null,
            shipment.getDelivered() != null ? Timestamp.valueOf(shipment.getDelivered()) : null
        );

        log.info("Shipment saved with ID={}", shipment.getId());
        return shipment;
    }

    /**
     * Speichert eine Lieferschein-Position (via direktes JDBC für Oracle-Kompatibilität)
     */
    @Transactional
    public ShipmentLine saveShipmentLine(ShipmentLine line) {
        if (line.getId() == null) {
            Long newId = getNextId("TD_SHIPMENTLINE");
            line.setId(newId);
        }

        // Oracle-Tabelle HAT ON_TRUCK Spalte (NOT NULL)!
        String sql = """
            INSERT INTO TD_SHIPMENTLINE
            (ID, SERIAL, SHIPMENT_ID, SHIPMENT_POS, INGOT_NO, INGOT_ID, INGOT_COMMENT,
             WEIGHT, LENGTH, THICKNESS, WIDTH, HEAD_SAWN, FOOT_SAWN, SCRAP, REVISED,
             PRODUCT_NO, SAP_PRODUCT_NO, CALLOFF_NO, ORDER_POS, ON_TRUCK)
            VALUES (?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
            """;

        // Truncate string fields to match actual Oracle column sizes
        jdbcTemplate.update(sql,
            line.getId(),
            line.getShipmentId(),
            line.getPosition(),
            truncate(line.getIngotNumber(), 20),
            line.getIngotId(),
            truncate(line.getComment(), 200),
            line.getWeight(),
            line.getLength(),
            line.getThickness(),
            line.getWidth(),
            boolToInt(line.getHeadSawn()),
            boolToInt(line.getFootSawn()),
            boolToInt(line.getScrap()),
            boolToInt(line.getRevised()),
            truncate(line.getProductNumber(), 50),
            truncate(line.getSapProductNumber(), 9),
            truncate(line.getCalloffNumber(), 50),
            truncate(line.getOrderPosition(), 50)
        );

        log.debug("ShipmentLine saved: id={}, shipmentId={}, pos={}",
            line.getId(), line.getShipmentId(), line.getPosition());
        return line;
    }

    private Integer boolToInt(Boolean value) {
        return value != null && value ? 1 : 0;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private Long getNextId(String tableName) {
        try {
            Long maxId = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(ID), 0) + 1 FROM " + tableName, Long.class);
            return maxId != null ? maxId : 1L;
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    /**
     * Findet einen Lieferschein nach ID
     */
    public Optional<Shipment> findById(Long id) {
        return shipmentRepository.findById(id);
    }

    /**
     * Findet einen Lieferschein nach Nummer
     */
    public Optional<Shipment> findByShipmentNumber(String shipmentNumber) {
        return shipmentRepository.findByShipmentNumber(shipmentNumber);
    }

    /**
     * Lädt alle Lieferscheine
     */
    public List<Shipment> findAll() {
        return (List<Shipment>) shipmentRepository.findAll();
    }

    /**
     * Sucht Lieferscheine mit Filtern (dynamisches SQL für Oracle-Kompatibilität)
     */
    public List<Shipment> search(String shipmentNo, String orderNo, String customerNo,
                                 String destination, LocalDateTime fromDate, LocalDateTime toDate) {
        StringBuilder sql = new StringBuilder("SELECT * FROM TD_SHIPMENT WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (shipmentNo != null && !shipmentNo.isEmpty()) {
            sql.append(" AND SHIPMENT_NO LIKE ?");
            params.add("%" + shipmentNo + "%");
        }

        if (orderNo != null && !orderNo.isEmpty()) {
            sql.append(" AND ORDER_NO = ?");
            params.add(orderNo);
        }

        if (customerNo != null && !customerNo.isEmpty()) {
            sql.append(" AND CUSTOMER_NO = ?");
            params.add(customerNo);
        }

        if (destination != null && !destination.isEmpty()) {
            sql.append(" AND DESTINATION = ?");
            params.add(destination);
        }

        if (fromDate != null) {
            sql.append(" AND DELIVERED >= ?");
            params.add(Timestamp.valueOf(fromDate));
        }

        if (toDate != null) {
            sql.append(" AND DELIVERED <= ?");
            params.add(Timestamp.valueOf(toDate));
        }

        sql.append(" ORDER BY DELIVERED DESC");

        return jdbcTemplate.query(sql.toString(), params.toArray(), this::mapRowToShipment);
    }

    private Shipment mapRowToShipment(ResultSet rs, int rowNum) throws SQLException {
        Shipment shipment = new Shipment();
        shipment.setId(rs.getLong("ID"));
        shipment.setSerial(rs.getLong("SERIAL"));
        shipment.setShipmentNumber(rs.getString("SHIPMENT_NO"));
        shipment.setOrderNumber(rs.getString("ORDER_NO"));
        shipment.setDestination(rs.getString("DESTINATION"));
        shipment.setCustomerNumber(rs.getString("CUSTOMER_NO"));
        shipment.setAddress(rs.getString("ADDRESS"));

        Timestamp printed = rs.getTimestamp("PRINTED");
        if (printed != null) {
            shipment.setPrinted(printed.toLocalDateTime());
        }

        Timestamp delivered = rs.getTimestamp("DELIVERED");
        if (delivered != null) {
            shipment.setDelivered(delivered.toLocalDateTime());
        }

        shipment.markNotNew();
        return shipment;
    }

    /**
     * Lädt alle Positionen eines Lieferscheins
     */
    public List<ShipmentLine> findLinesByShipmentId(Long shipmentId) {
        return shipmentLineRepository.findByShipmentId(shipmentId);
    }

    /**
     * Berechnet das Gesamtgewicht eines Lieferscheins
     */
    public int getTotalWeight(Long shipmentId) {
        return shipmentLineRepository.getTotalWeight(shipmentId);
    }

    /**
     * Setzt den Druck-Zeitstempel
     */
    @Transactional
    public void markAsPrinted(Long shipmentId) {
        shipmentRepository.findById(shipmentId).ifPresent(shipment -> {
            if (shipment.getPrinted() == null) {
                shipment.markNotNew();
                shipment.setPrinted(LocalDateTime.now());
                shipmentRepository.save(shipment);
                log.info("Lieferschein {} als gedruckt markiert", shipment.getShipmentNumber());
            }
        });
    }

    /**
     * Zählt alle Lieferscheine
     */
    public long count() {
        return shipmentRepository.countAll();
    }

    /**
     * Prüft ob die Tabellen existieren
     */
    public boolean tablesExist() {
        try {
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM TD_SHIPMENT WHERE ROWNUM = 1", Integer.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
