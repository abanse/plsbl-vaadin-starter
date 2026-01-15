package com.hydro.plsbl.service;

import com.hydro.plsbl.dto.IngotDTO;
import com.hydro.plsbl.dto.TransportOrderDTO;
import com.hydro.plsbl.entity.masterdata.Product;
import com.hydro.plsbl.entity.masterdata.Stockyard;
import com.hydro.plsbl.entity.transdata.StockyardStatus;
import com.hydro.plsbl.kafka.KafkaConsumerService;
import com.hydro.plsbl.kafka.KafkaProducerService;
import com.hydro.plsbl.kafka.dto.KafkaPickupOrderMessage;
import com.hydro.plsbl.kafka.dto.KafkaSawFeedbackMessage;
import com.hydro.plsbl.repository.ProductRepository;
import com.hydro.plsbl.repository.StockyardRepository;
import com.hydro.plsbl.repository.StockyardStatusRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Service fuer die automatische Einlagerung von Barren von der Saege
 *
 * Verarbeitet Kafka-Nachrichten (PickupOrder) und:
 * 1. Erstellt oder findet das Produkt
 * 2. Erstellt den Barren auf der Saege-Position
 * 3. Findet einen geeigneten Ziel-Lagerplatz
 * 4. Erstellt einen Transport-Auftrag
 */
@Service
public class IngotStorageService {

    private static final Logger log = LoggerFactory.getLogger(IngotStorageService.class);

    // Saege-Position Yard-Type
    private static final String YARD_TYPE_SAW = "S";
    // Interne Lagerplaetze
    private static final String YARD_TYPE_INTERNAL = "I";

    private final KafkaConsumerService kafkaConsumerService;
    private final KafkaProducerService kafkaProducerService;
    private final IngotService ingotService;
    private final TransportOrderService transportOrderService;
    private final ProductRepository productRepository;
    private final StockyardRepository stockyardRepository;
    private final StockyardStatusRepository stockyardStatusRepository;
    private final JdbcTemplate jdbcTemplate;

    public IngotStorageService(
            KafkaConsumerService kafkaConsumerService,
            KafkaProducerService kafkaProducerService,
            IngotService ingotService,
            TransportOrderService transportOrderService,
            ProductRepository productRepository,
            StockyardRepository stockyardRepository,
            StockyardStatusRepository stockyardStatusRepository,
            JdbcTemplate jdbcTemplate) {
        this.kafkaConsumerService = kafkaConsumerService;
        this.kafkaProducerService = kafkaProducerService;
        this.ingotService = ingotService;
        this.transportOrderService = transportOrderService;
        this.productRepository = productRepository;
        this.stockyardRepository = stockyardRepository;
        this.stockyardStatusRepository = stockyardStatusRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        // Registriere Handler beim KafkaConsumerService
        kafkaConsumerService.setPickupOrderHandler(this::handlePickupOrder);
        log.info("IngotStorageService initialisiert - PickupOrderHandler registriert");
    }

    /**
     * Verarbeitet eine Einlagerungs-Anfrage von der Saege
     */
    @Transactional
    public void handlePickupOrder(KafkaPickupOrderMessage message) {
        log.info("Verarbeite Einlagerungs-Anfrage: Barren={}, Produkt={}",
            message.getIngotNumber(), message.getProductNumber());

        try {
            // 1. Produkt finden oder erstellen
            Long productId = findOrCreateProduct(message.getProductNumber());
            log.debug("Produkt gefunden/erstellt: ID={}", productId);

            // 2. Saege-Position finden
            Stockyard sawPosition = findSawPosition()
                .orElseThrow(() -> new IllegalStateException("Keine Saege-Position konfiguriert!"));
            log.debug("Saege-Position: {} (ID={})", sawPosition.getYardNumber(), sawPosition.getId());

            // 3. Barren erstellen (auf Saege-Position)
            IngotDTO ingot = createIngot(message, productId, sawPosition.getId());
            log.info("Barren erstellt: {} auf Position {}", ingot.getIngotNo(), sawPosition.getYardNumber());

            // 4. Ziel-Lagerplatz finden
            Stockyard targetYard = findTargetStockyard(productId, message.getTargetStockyardNumber())
                .orElseThrow(() -> new IllegalStateException("Kein geeigneter Lagerplatz gefunden!"));
            log.debug("Ziel-Lagerplatz: {} (ID={})", targetYard.getYardNumber(), targetYard.getId());

            // 5. Transport-Auftrag erstellen
            TransportOrderDTO order = createTransportOrder(ingot, sawPosition.getId(), targetYard.getId());
            log.info("Transport-Auftrag erstellt: {} von {} nach {}",
                order.getTransportNo(), sawPosition.getYardNumber(), targetYard.getYardNumber());

            // 6. Rueckmeldung an Saege senden
            sendSuccessFeedback(message.getIngotNumber(), targetYard.getYardNumber(), order.getTransportNo());

        } catch (Exception e) {
            log.error("Fehler bei Einlagerung: {}", e.getMessage(), e);
            sendErrorFeedback(message.getIngotNumber(), e.getMessage());
            throw e;
        }
    }

    /**
     * Manuelle Einlagerung (fuer Test ohne Kafka)
     */
    @Transactional
    public TransportOrderDTO processStorageRequest(
            String ingotNumber,
            String productNumber,
            int weight,
            int length,
            int width,
            int height,
            boolean headSawn,
            boolean footSawn) {

        log.info("========================================");
        log.info("NEUE EINLAGERUNG GESTARTET");
        log.info("  Barren-Nr: {}", ingotNumber);
        log.info("  Produkt: {}", productNumber);
        log.info("  Gewicht: {} kg", weight);
        log.info("  Länge: {} mm", length);
        log.info("  Breite: {} mm", width);
        log.info("  Höhe: {} mm", height);
        log.info("========================================");

        // Kafka-Message erstellen
        KafkaPickupOrderMessage message = new KafkaPickupOrderMessage();
        message.setIngotNumber(ingotNumber);
        message.setProductNumber(productNumber);
        message.setWeight(weight);
        message.setLength(length);
        message.setWidth(width);
        message.setHeight(height);
        message.setHeadSawn(headSawn);
        message.setFootSawn(footSawn);

        // 1. Produkt finden oder erstellen
        Long productId = findOrCreateProduct(productNumber);

        // 2. Saege-Position finden
        Stockyard sawPosition = findSawPosition()
            .orElseThrow(() -> new IllegalStateException("Keine Saege-Position konfiguriert!"));
        log.info("SAW Position gefunden: {} (ID={})", sawPosition.getYardNumber(), sawPosition.getId());

        // 3. Barren erstellen
        IngotDTO ingot = createIngot(message, productId, sawPosition.getId());
        log.info("Barren erstellt auf SAW: ingotNo={}, stockyardId={}", ingot.getIngotNo(), ingot.getStockyardId());

        // Debug: Prüfen was jetzt tatsächlich auf der Säge liegt
        try {
            List<String> ingotsOnSaw = jdbcTemplate.queryForList(
                "SELECT INGOT_NO FROM TD_INGOT WHERE STOCKYARD_ID = ?",
                String.class, sawPosition.getId());
            log.info(">>> AKTUELLE BARREN AUF SÄGE NACH ERSTELLUNG: {} <<<", ingotsOnSaw);
        } catch (Exception e) {
            log.warn("Debug-Abfrage fehlgeschlagen: {}", e.getMessage());
        }

        // 4. Ziel-Lagerplatz finden
        Stockyard targetYard = findTargetStockyard(productId, null)
            .orElseThrow(() -> new IllegalStateException("Kein geeigneter Lagerplatz gefunden!"));
        log.info("Ziel-Lagerplatz: {} (ID={})", targetYard.getYardNumber(), targetYard.getId());

        // 5. Transport-Auftrag erstellen
        TransportOrderDTO order = createTransportOrder(ingot, sawPosition.getId(), targetYard.getId());

        log.info("========================================");
        log.info("EINLAGERUNG ABGESCHLOSSEN");
        log.info("  Barren: {} (ID={})", ingot.getIngotNo(), ingot.getId());
        log.info("  Von: {} -> Nach: {}", sawPosition.getYardNumber(), targetYard.getYardNumber());
        log.info("  Transport-Auftrag: {}", order.getTransportNo());
        log.info("========================================");

        return order;
    }

    /**
     * Findet oder erstellt ein Produkt
     */
    private Long findOrCreateProduct(String productNumber) {
        if (productNumber == null || productNumber.isBlank()) {
            throw new IllegalArgumentException("Produktnummer darf nicht leer sein!");
        }

        // Existierendes Produkt suchen
        Optional<Product> existing = productRepository.findByProductNo(productNumber);
        if (existing.isPresent()) {
            return existing.get().getId();
        }

        // Neues Produkt erstellen
        log.info("Erstelle neues Produkt: {}", productNumber);

        Long newId = getNextProductId();
        jdbcTemplate.update(
            "INSERT INTO MD_PRODUCT (ID, SERIAL, PRODUCT_NO, DESCRIPTION, MAX_PER_LOCATION) VALUES (?, 1, ?, ?, 8)",
            newId, productNumber, "Auto-erstellt: " + productNumber);

        return newId;
    }

    private Long getNextProductId() {
        try {
            Long maxId = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(ID), 0) + 1 FROM MD_PRODUCT", Long.class);
            return maxId != null ? maxId : 1L;
        } catch (Exception e) {
            log.warn("Could not get next product ID, using timestamp", e);
            return System.currentTimeMillis();
        }
    }

    /**
     * Findet die Saege-Position
     */
    private Optional<Stockyard> findSawPosition() {
        List<Stockyard> sawPositions = stockyardRepository.findByType(YARD_TYPE_SAW);
        return sawPositions.stream().findFirst();
    }

    /**
     * Erstellt einen neuen Barren auf der Säge-Position.
     * Alte Barren werden NICHT gelöscht - sie bleiben in der Warteschlange
     * und werden nacheinander durch Transport-Aufträge abgearbeitet.
     */
    private IngotDTO createIngot(KafkaPickupOrderMessage message, Long productId, Long stockyardId) {
        // Nächste freie Stapel-Position ermitteln (für Warteschlange)
        int nextPosition = getNextPilePosition(stockyardId);
        log.info("Neuer Barren wird auf Position {} in der Warteschlange erstellt", nextPosition);

        IngotDTO dto = new IngotDTO();
        dto.setIngotNo(message.getIngotNumber());
        dto.setProductId(productId);
        dto.setStockyardId(stockyardId);
        dto.setPilePosition(nextPosition); // Position in der Warteschlange
        dto.setWeight(message.getWeight());
        dto.setLength(message.getLength());
        dto.setWidth(message.getWidth());
        dto.setThickness(message.getHeight());
        dto.setHeadSawn(message.isHeadSawn());
        dto.setFootSawn(message.isFootSawn());
        dto.setRotated(message.isRotated());
        dto.setInStockSince(LocalDateTime.now());

        return ingotService.save(dto);
    }

    /**
     * Ermittelt die nächste freie Stapel-Position auf einem Lagerplatz
     */
    private int getNextPilePosition(Long stockyardId) {
        try {
            Integer maxPosition = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(PILE_POSITION), 0) FROM TD_INGOT WHERE STOCKYARD_ID = ?",
                Integer.class, stockyardId);
            return (maxPosition != null ? maxPosition : 0) + 1;
        } catch (Exception e) {
            log.warn("Konnte Stapel-Position nicht ermitteln: {}", e.getMessage());
            return 1;
        }
    }

    /**
     * Gibt die Anzahl der wartenden Barren auf der Säge zurück
     */
    public int getQueueSize() {
        try {
            Stockyard sawPosition = findSawPosition().orElse(null);
            if (sawPosition == null) return 0;

            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM TD_INGOT WHERE STOCKYARD_ID = ?",
                Integer.class, sawPosition.getId());
            return count != null ? count : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Gibt die Liste der Barren in der Warteschlange zurück (sortiert nach Position)
     */
    public java.util.List<Map<String, Object>> getQueueItems() {
        try {
            Stockyard sawPosition = findSawPosition().orElse(null);
            if (sawPosition == null) return java.util.Collections.emptyList();

            return jdbcTemplate.queryForList(
                "SELECT ID, INGOT_NO, PILE_POSITION, LENGTH, WEIGHT FROM TD_INGOT WHERE STOCKYARD_ID = ? ORDER BY PILE_POSITION ASC",
                sawPosition.getId());
        } catch (Exception e) {
            log.warn("Konnte Warteschlange nicht laden: {}", e.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    /**
     * Entfernt alle Barren von der Säge-Position und gibt die Anzahl zurück.
     * @return Anzahl der gelöschten Barren
     */
    public int clearSawPosition() {
        Stockyard sawPosition = findSawPosition().orElse(null);
        if (sawPosition == null) {
            log.warn("Keine Säge-Position gefunden");
            return 0;
        }
        return clearSawPosition(sawPosition.getId());
    }

    /**
     * Entfernt alle Barren von der Säge-Position (nur für manuelle Bereinigung).
     * Verwendet direktes SQL für maximale Zuverlässigkeit.
     * @return Anzahl der gelöschten Barren
     */
    public int clearSawPosition(Long sawStockyardId) {
        log.info("=== CLEAR SAW POSITION (ID={}) ===", sawStockyardId);
        int deletedIngots = 0;

        // 1. Prüfen was aktuell auf der Säge liegt (Debug)
        try {
            List<String> currentIngots = jdbcTemplate.queryForList(
                "SELECT INGOT_NO FROM TD_INGOT WHERE STOCKYARD_ID = ?",
                String.class, sawStockyardId);
            log.info("Aktuelle Barren auf Säge: {}", currentIngots);
        } catch (Exception e) {
            log.warn("Konnte aktuelle Barren nicht abfragen: {}", e.getMessage());
        }

        // 2. Transport-Aufträge für alle Barren auf der Säge löschen
        try {
            int deletedOrders = jdbcTemplate.update(
                "DELETE FROM TD_TRANSPORTORDER WHERE INGOT_ID IN (SELECT ID FROM TD_INGOT WHERE STOCKYARD_ID = ?)",
                sawStockyardId);
            log.info("Transport-Aufträge gelöscht: {}", deletedOrders);
        } catch (Exception e) {
            log.warn("Fehler beim Löschen der Transport-Aufträge: {}", e.getMessage());
        }

        // 3. Alle Barren auf der Säge direkt per SQL löschen
        try {
            deletedIngots = jdbcTemplate.update(
                "DELETE FROM TD_INGOT WHERE STOCKYARD_ID = ?", sawStockyardId);
            log.info("Barren gelöscht: {}", deletedIngots);
        } catch (Exception e) {
            log.warn("Fehler beim Löschen der Barren: {}", e.getMessage());
        }

        // 4. StockyardStatus löschen
        try {
            int deletedStatus = jdbcTemplate.update(
                "DELETE FROM TD_STOCKYARDSTATUS WHERE STOCKYARD_ID = ?", sawStockyardId);
            log.info("StockyardStatus gelöscht: {}", deletedStatus);
        } catch (Exception e) {
            log.warn("Fehler beim Löschen des StockyardStatus: {}", e.getMessage());
        }

        log.info("=== SAW POSITION CLEARED ({} Barren) ===", deletedIngots);
        return deletedIngots;
    }

    /**
     * Findet einen geeigneten Ziel-Lagerplatz
     *
     * Prioritaet:
     * 1. Gewuenschter Lagerplatz (falls angegeben und verfuegbar)
     * 2. Lagerplatz mit gleichem Produkt und Platz
     * 3. Leerer Lagerplatz
     */
    private Optional<Stockyard> findTargetStockyard(Long productId, String preferredYardNo) {
        // 1. Gewuenschter Lagerplatz pruefen
        if (preferredYardNo != null && !preferredYardNo.isBlank()) {
            Optional<Stockyard> preferred = stockyardRepository.findByYardNumber(preferredYardNo);
            if (preferred.isPresent() && isYardAvailable(preferred.get(), productId)) {
                log.debug("Gewuenschter Lagerplatz verfuegbar: {}", preferredYardNo);
                return preferred;
            }
            log.debug("Gewuenschter Lagerplatz {} nicht verfuegbar", preferredYardNo);
        }

        // 2. Alle internen Lagerplaetze laden
        List<Stockyard> internalYards = stockyardRepository.findByType(YARD_TYPE_INTERNAL);

        // 3. Lagerplaetze mit gleichem Produkt suchen
        List<StockyardStatus> productLocations = stockyardStatusRepository.findByProductId(productId);
        for (StockyardStatus status : productLocations) {
            Optional<Stockyard> yard = stockyardRepository.findById(status.getStockyardId());
            if (yard.isPresent() && isYardAvailable(yard.get(), productId)) {
                log.debug("Lagerplatz mit gleichem Produkt gefunden: {}", yard.get().getYardNumber());
                return yard;
            }
        }

        // 4. Leeren Lagerplatz suchen
        for (Stockyard yard : internalYards) {
            if (yard.isToStockAllowed() && isYardEmpty(yard.getId())) {
                log.debug("Leerer Lagerplatz gefunden: {}", yard.getYardNumber());
                return Optional.of(yard);
            }
        }

        log.warn("Kein geeigneter Lagerplatz gefunden!");
        return Optional.empty();
    }

    /**
     * Prueft ob ein Lagerplatz verfuegbar ist (nicht voll und Einlagern erlaubt)
     */
    private boolean isYardAvailable(Stockyard yard, Long productId) {
        if (!yard.isToStockAllowed()) {
            return false;
        }

        // Aktuelle Belegung pruefen
        int currentCount = ingotService.countByStockyardId(yard.getId());
        if (currentCount >= yard.getMaxIngots()) {
            return false;
        }

        // Bei belegtem Platz: Produkt pruefen
        if (currentCount > 0) {
            Optional<StockyardStatus> status = stockyardStatusRepository.findByStockyardId(yard.getId());
            if (status.isPresent() && status.get().getProductId() != null) {
                // Nur gleiches Produkt erlaubt
                return status.get().getProductId().equals(productId);
            }
        }

        return true;
    }

    /**
     * Prueft ob ein Lagerplatz leer ist
     */
    private boolean isYardEmpty(Long stockyardId) {
        return ingotService.countByStockyardId(stockyardId) == 0;
    }

    /**
     * Erstellt einen Transport-Auftrag
     */
    private TransportOrderDTO createTransportOrder(IngotDTO ingot, Long fromYardId, Long toYardId) {
        TransportOrderDTO dto = new TransportOrderDTO();
        dto.setTransportNo(generateTransportNo());
        dto.setNormText("Einlagerung von Saege");
        dto.setIngotId(ingot.getId());
        dto.setFromYardId(fromYardId);
        dto.setFromPilePosition(ingot.getPilePosition());
        dto.setToYardId(toYardId);
        dto.setPriority(10); // Hohe Prioritaet fuer Einlagerung

        return transportOrderService.save(dto);
    }

    /**
     * Generiert eine neue Transport-Auftragsnummer
     */
    private String generateTransportNo() {
        String prefix = "TA-" + java.time.LocalDate.now().getYear() + "-";
        try {
            Integer maxNo = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(CAST(SUBSTR(TRANSPORT_NO, 9) AS INTEGER)), 0) + 1 FROM TD_TRANSPORTORDER WHERE TRANSPORT_NO LIKE ?",
                Integer.class, prefix + "%");
            return prefix + String.format("%04d", maxNo != null ? maxNo : 1);
        } catch (Exception e) {
            log.warn("Could not generate transport number, using timestamp", e);
            return prefix + System.currentTimeMillis();
        }
    }

    /**
     * Sendet Erfolgs-Rueckmeldung an die Saege
     */
    private void sendSuccessFeedback(String ingotNumber, String targetYard, String transportNo) {
        if (!kafkaProducerService.isEnabled()) {
            log.debug("Kafka deaktiviert - keine Rueckmeldung gesendet");
            return;
        }

        KafkaSawFeedbackMessage feedback = KafkaSawFeedbackMessage.pickupConfirmed(ingotNumber);
        feedback.setStockyardNumber(targetYard);
        feedback.setTimestamp(LocalDateTime.now());

        kafkaProducerService.sendSawFeedback(feedback);
        log.info("Erfolgs-Rueckmeldung an Saege gesendet: {} -> {}", ingotNumber, targetYard);
    }

    /**
     * Sendet Fehler-Rueckmeldung an die Saege
     */
    private void sendErrorFeedback(String ingotNumber, String errorMessage) {
        if (!kafkaProducerService.isEnabled()) {
            log.debug("Kafka deaktiviert - keine Rueckmeldung gesendet");
            return;
        }

        KafkaSawFeedbackMessage feedback = KafkaSawFeedbackMessage.pickupFailed(ingotNumber, errorMessage);
        feedback.setTimestamp(LocalDateTime.now());

        kafkaProducerService.sendSawFeedback(feedback);
        log.warn("Fehler-Rueckmeldung an Saege gesendet: {} - {}", ingotNumber, errorMessage);
    }
}
