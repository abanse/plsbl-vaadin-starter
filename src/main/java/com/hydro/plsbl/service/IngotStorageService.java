package com.hydro.plsbl.service;

import com.hydro.plsbl.dto.IngotDTO;
import com.hydro.plsbl.dto.IngotTypeDTO;
import com.hydro.plsbl.dto.TransportOrderDTO;
import com.hydro.plsbl.entity.masterdata.Product;
import com.hydro.plsbl.entity.enums.LengthType;
import com.hydro.plsbl.entity.enums.StockyardUsage;
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
    // Grenze fuer lange Barren in mm
    private static final int LONG_INGOT_THRESHOLD = 6000;

    private final KafkaConsumerService kafkaConsumerService;
    private final KafkaProducerService kafkaProducerService;
    private final IngotService ingotService;
    private final IngotTypeService ingotTypeService;
    private final TransportOrderService transportOrderService;
    private final ProductRepository productRepository;
    private final StockyardRepository stockyardRepository;
    private final StockyardStatusRepository stockyardStatusRepository;
    private final SawStatusService sawStatusService;
    private final ErrorBroadcaster errorBroadcaster;
    private final JdbcTemplate jdbcTemplate;

    public IngotStorageService(
            KafkaConsumerService kafkaConsumerService,
            KafkaProducerService kafkaProducerService,
            IngotService ingotService,
            IngotTypeService ingotTypeService,
            TransportOrderService transportOrderService,
            ProductRepository productRepository,
            StockyardRepository stockyardRepository,
            StockyardStatusRepository stockyardStatusRepository,
            SawStatusService sawStatusService,
            ErrorBroadcaster errorBroadcaster,
            JdbcTemplate jdbcTemplate) {
        this.kafkaConsumerService = kafkaConsumerService;
        this.kafkaProducerService = kafkaProducerService;
        this.ingotService = ingotService;
        this.ingotTypeService = ingotTypeService;
        this.transportOrderService = transportOrderService;
        this.productRepository = productRepository;
        this.stockyardRepository = stockyardRepository;
        this.stockyardStatusRepository = stockyardStatusRepository;
        this.sawStatusService = sawStatusService;
        this.errorBroadcaster = errorBroadcaster;
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
            // 0. Prüfen ob Barren bereits im Lager vorhanden ist
            checkIngotNotInStock(message.getIngotNumber());

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

            // 4. Ziel-Lagerplatz finden (basierend auf Barren-Laenge)
            Stockyard targetYard = findTargetStockyard(productId, message.getTargetStockyardNumber(), message.getLength())
                .orElseThrow(() -> new IllegalStateException("Kein geeigneter Lagerplatz gefunden!"));
            log.debug("Ziel-Lagerplatz: {} (ID={})", targetYard.getYardNumber(), targetYard.getId());

            // 5. Transport-Auftrag erstellen
            TransportOrderDTO order = createTransportOrder(ingot, sawPosition.getId(), targetYard.getId());
            log.info("Transport-Auftrag erstellt: {} von {} nach {}",
                order.getTransportNo(), sawPosition.getYardNumber(), targetYard.getYardNumber());

            // 6. Fehler im Säge-Status löschen (Einlagerung erfolgreich)
            sawStatusService.clearError();

            // 7. Rueckmeldung an Saege senden
            sendSuccessFeedback(message.getIngotNumber(), targetYard.getYardNumber(), order.getTransportNo());

        } catch (Exception e) {
            log.error("Fehler bei Einlagerung: {}", e.getMessage(), e);
            // Fehler im Säge-Status setzen (falls noch nicht durch checkIngotNotInStock gesetzt)
            sawStatusService.setError("FEHLER", e.getMessage());
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

        // 0. Prüfen ob Barren bereits im Lager vorhanden ist
        checkIngotNotInStock(ingotNumber);

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

        // 4. Ziel-Lagerplatz finden (basierend auf Barren-Laenge)
        Stockyard targetYard = findTargetStockyard(productId, null, length)
            .orElseThrow(() -> new IllegalStateException("Kein geeigneter Lagerplatz gefunden!"));
        log.info("Ziel-Lagerplatz: {} (ID={})", targetYard.getYardNumber(), targetYard.getId());

        // 5. Transport-Auftrag erstellen
        TransportOrderDTO order = createTransportOrder(ingot, sawPosition.getId(), targetYard.getId());

        // 6. Fehler im Säge-Status löschen (Einlagerung erfolgreich)
        sawStatusService.clearError();

        log.info("========================================");
        log.info("EINLAGERUNG ABGESCHLOSSEN");
        log.info("  Barren: {} (ID={})", ingot.getIngotNo(), ingot.getId());
        log.info("  Von: {} -> Nach: {}", sawPosition.getYardNumber(), targetYard.getYardNumber());
        log.info("  Transport-Auftrag: {}", order.getTransportNo());
        log.info("========================================");

        return order;
    }

    /**
     * Prüft ob ein Barren mit der gleichen Nummer bereits im Lager vorhanden ist.
     * Wirft eine Exception wenn der Barren bereits existiert und noch im Bestand ist.
     *
     * @param ingotNumber Die Barrennummer
     * @throws IllegalStateException wenn der Barren bereits im Lager ist
     */
    private void checkIngotNotInStock(String ingotNumber) {
        if (ingotNumber == null || ingotNumber.isBlank()) {
            return;
        }

        // Barren mit gleicher Nummer suchen
        Optional<IngotDTO> existingIngot = ingotService.findByIngotNo(ingotNumber);

        if (existingIngot.isPresent()) {
            IngotDTO ingot = existingIngot.get();

            // Prüfen ob noch im Bestand (stockyardId != null)
            if (ingot.getStockyardId() != null) {
                // Lagerplatz-Nummer ermitteln für bessere Fehlermeldung
                String yardNumber = "unbekannt";
                try {
                    Optional<Stockyard> yard = stockyardRepository.findById(ingot.getStockyardId());
                    if (yard.isPresent()) {
                        yardNumber = yard.get().getYardNumber();
                    }
                } catch (Exception e) {
                    log.warn("Konnte Lagerplatz nicht ermitteln: {}", e.getMessage());
                }

                String errorMsg = String.format(
                    "Barren %s ist bereits im Lager vorhanden (Lagerplatz: %s). " +
                    "Einlagerung nicht möglich!",
                    ingotNumber, yardNumber);

                log.error(errorMsg);

                // Fehler über Broadcaster senden (wird in der UI als Notification angezeigt)
                errorBroadcaster.broadcast("DUPLIKAT", errorMsg);

                throw new IllegalStateException(errorMsg);
            }

            // Barren existiert, ist aber nicht mehr im Bestand (geliefert/ausgelagert)
            log.info("Barren {} existiert, ist aber nicht mehr im Bestand - Neuanlage möglich", ingotNumber);
        }
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
            "INSERT INTO MD_PRODUCT (ID, SERIAL, TABLESERIAL, PRODUCT_NO, DESCRIPTION, MAX_PER_LOCATION) VALUES (?, 1, 1, ?, ?, 8)",
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
     * 1. Gewuenschter Lagerplatz (falls angegeben, verfuegbar und passende Groesse)
     * 2. Lagerplatz mit gleichem Produkt, Platz und EXAKT passender Groesse (SHORT/LONG)
     * 3. Leerer Lagerplatz mit EXAKT passender Groesse (SHORT/LONG)
     * 4. Fallback: AUTOMATIC Platz mit gleichem Produkt
     * 5. Fallback: Leerer AUTOMATIC Platz
     *
     * @param productId Produkt-ID
     * @param preferredYardNo Gewuenschte Platznummer (optional)
     * @param ingotLength Laenge des Barrens in mm - bestimmt ob SHORT oder LONG Platz
     */
    private Optional<Stockyard> findTargetStockyard(Long productId, String preferredYardNo, int ingotLength) {
        // Bestimme Barrentyp ueber IngotTypeService
        Optional<IngotTypeDTO> ingotType = ingotTypeService.determineIngotType(ingotLength, null, null, null, null);
        LengthType lengthType = ingotType.map(IngotTypeDTO::getLengthType).orElse(null);

        // LengthType auf StockyardUsage mappen
        // MEDIUM hat kein direktes Mapping -> AUTOMATIC als Fallback
        StockyardUsage requiredUsage;
        if (lengthType == LengthType.LONG) {
            requiredUsage = StockyardUsage.LONG;
        } else if (lengthType == LengthType.SHORT) {
            requiredUsage = StockyardUsage.SHORT;
        } else {
            // MEDIUM oder unbekannt -> Fallback-Logik
            requiredUsage = ingotLength > LONG_INGOT_THRESHOLD ? StockyardUsage.LONG : StockyardUsage.SHORT;
        }

        log.info("========================================");
        log.info("SUCHE ZIEL-LAGERPLATZ");
        log.info("  Barren-Laenge: {}mm", ingotLength);
        if (ingotType.isPresent()) {
            IngotTypeDTO type = ingotType.get();
            log.info("  Barrentyp: {} ({})", type.getName(), type.getLengthType() != null ? type.getLengthType().getDisplayName() : "?");
            log.info("  Intern erlaubt: {}, Extern erlaubt: {}", type.getInternalAllowed(), type.getExternalAllowed());
        } else {
            log.info("  Barrentyp: UNBEKANNT (Fallback: Grenze {}mm)", LONG_INGOT_THRESHOLD);
        }
        log.info("  Ziel-Kategorie: {}", requiredUsage.getDisplayName());
        log.info("========================================");

        // 1. Gewuenschter Lagerplatz pruefen (muss exakt passen oder AUTOMATIC sein)
        if (preferredYardNo != null && !preferredYardNo.isBlank()) {
            Optional<Stockyard> preferred = stockyardRepository.findByYardNumber(preferredYardNo);
            if (preferred.isPresent() && isYardAvailable(preferred.get(), productId)
                    && hasMatchingUsageStrict(preferred.get(), requiredUsage)) {
                log.debug("Gewuenschter Lagerplatz verfuegbar: {}", preferredYardNo);
                return preferred;
            }
            log.debug("Gewuenschter Lagerplatz {} nicht verfuegbar oder falsche Groesse", preferredYardNo);
        }

        // 2. Alle internen Lagerplaetze laden
        List<Stockyard> internalYards = stockyardRepository.findByType(YARD_TYPE_INTERNAL);

        // Nach EXAKT passender Groesse filtern (SHORT oder LONG, NICHT AUTOMATIC)
        List<Stockyard> exactMatchYards = internalYards.stream()
            .filter(yard -> hasExactUsage(yard, requiredUsage))
            .collect(Collectors.toList());
        log.info("  Gefundene {} Lagerplaetze: {} Stueck",
            requiredUsage.getDisplayName(), exactMatchYards.size());
        if (!exactMatchYards.isEmpty()) {
            log.info("  Erste 5: {}", exactMatchYards.stream()
                .limit(5)
                .map(y -> y.getYardNumber())
                .collect(Collectors.joining(", ")));
        }

        // AUTOMATIC Plaetze als Fallback
        List<Stockyard> automaticYards = internalYards.stream()
            .filter(yard -> yard.getUsage() == null || yard.getUsage() == StockyardUsage.AUTOMATIC)
            .collect(Collectors.toList());
        log.info("  AUTOMATIC Fallback-Plaetze: {} Stueck", automaticYards.size());

        // 3. Lagerplaetze mit gleichem Produkt suchen (EXAKT passende Groesse zuerst)
        List<StockyardStatus> productLocations = stockyardStatusRepository.findByProductId(productId);
        for (StockyardStatus status : productLocations) {
            Optional<Stockyard> yard = stockyardRepository.findById(status.getStockyardId());
            if (yard.isPresent() && isYardAvailable(yard.get(), productId)
                    && hasExactUsage(yard.get(), requiredUsage)) {
                log.info("Lagerplatz mit gleichem Produkt und EXAKT passender Groesse gefunden: {}",
                    yard.get().getYardNumber());
                return yard;
            }
        }

        // 4. Leeren Lagerplatz mit EXAKT passender Groesse suchen
        for (Stockyard yard : exactMatchYards) {
            if (yard.isToStockAllowed() && isYardEmpty(yard.getId())) {
                log.info("========================================");
                log.info("ERGEBNIS: Leerer {} Platz gefunden!", requiredUsage.getDisplayName());
                log.info("  Lagerplatz: {} (ID={})", yard.getYardNumber(), yard.getId());
                log.info("  Position: X={}, Y={}, Z={}", yard.getXPosition(), yard.getYPosition(), yard.getZPosition());
                log.info("========================================");
                return Optional.of(yard);
            }
        }

        // 4b. Nicht-vollen Lagerplatz mit EXAKT passender Groesse suchen
        // (fuer neues Produkt - Platz hat noch Kapazitaet)
        for (Stockyard yard : exactMatchYards) {
            if (yard.isToStockAllowed() && hasCapacity(yard, productId)) {
                log.info("========================================");
                log.info("ERGEBNIS: {} Platz mit Kapazitaet gefunden!", requiredUsage.getDisplayName());
                log.info("  Lagerplatz: {} (ID={})", yard.getYardNumber(), yard.getId());
                log.info("  Position: X={}, Y={}, Z={}", yard.getXPosition(), yard.getYPosition(), yard.getZPosition());
                log.info("========================================");
                return Optional.of(yard);
            }
        }

        // === FALLBACK: AUTOMATIC Plaetze ===
        log.info("Kein exakt passender Platz gefunden, suche AUTOMATIC Fallback...");

        // 5. AUTOMATIC Platz mit gleichem Produkt
        for (StockyardStatus status : productLocations) {
            Optional<Stockyard> yard = stockyardRepository.findById(status.getStockyardId());
            if (yard.isPresent() && isYardAvailable(yard.get(), productId)
                    && (yard.get().getUsage() == null || yard.get().getUsage() == StockyardUsage.AUTOMATIC)) {
                log.info("AUTOMATIC Fallback mit gleichem Produkt gefunden: {}", yard.get().getYardNumber());
                return yard;
            }
        }

        // 6. Leerer AUTOMATIC Platz
        for (Stockyard yard : automaticYards) {
            if (yard.isToStockAllowed() && isYardEmpty(yard.getId())) {
                log.info("Leerer AUTOMATIC Fallback gefunden: {}", yard.getYardNumber());
                return Optional.of(yard);
            }
        }

        log.warn("Kein geeigneter Lagerplatz fuer {} Barren ({}mm) gefunden!",
            requiredUsage.getDisplayName(), ingotLength);
        return Optional.empty();
    }

    /**
     * Prueft ob ein Lagerplatz EXAKT die passende Verwendung (SHORT/LONG) hat.
     * AUTOMATIC wird hier NICHT akzeptiert.
     */
    private boolean hasExactUsage(Stockyard yard, StockyardUsage requiredUsage) {
        StockyardUsage yardUsage = yard.getUsage();
        return yardUsage == requiredUsage;
    }

    /**
     * Prueft ob ein Lagerplatz die passende Verwendung hat (inkl. AUTOMATIC als Fallback).
     */
    private boolean hasMatchingUsageStrict(Stockyard yard, StockyardUsage requiredUsage) {
        StockyardUsage yardUsage = yard.getUsage();
        if (yardUsage == null || yardUsage == StockyardUsage.AUTOMATIC) {
            return true; // Gewuenschter Platz darf AUTOMATIC sein
        }
        return yardUsage == requiredUsage;
    }

    /**
     * Prueft ob ein Lagerplatz verfuegbar ist (nicht voll und Einlagern erlaubt)
     * Beruecksichtigt auch offene Transportauftraege, die diesen Platz als Ziel haben.
     */
    private boolean isYardAvailable(Stockyard yard, Long productId) {
        if (!yard.isToStockAllowed()) {
            log.info("  -> {} NICHT verfuegbar: Einlagern nicht erlaubt", yard.getYardNumber());
            return false;
        }

        // Aktuelle Belegung pruefen - direkt aus DB zaehlen
        int currentCount = ingotService.countByStockyardId(yard.getId());
        int maxIngots = yard.getMaxIngots();

        // Offene Transportauftraege zaehlen, die diesen Platz als Ziel haben
        // Status: P=PENDING, I=IN_PROGRESS, U=PICKED_UP, H=PAUSED
        int pendingTransports = countPendingTransportsToYard(yard.getId());

        // Gesamtanzahl = aktuelle Barren + erwartete Barren aus offenen Auftraegen
        int totalExpectedCount = currentCount + pendingTransports;

        log.info("  -> {} Pruefung: aktuell={}, pending={}, gesamt={}, max={}",
            yard.getYardNumber(), currentCount, pendingTransports, totalExpectedCount, maxIngots);

        if (totalExpectedCount >= maxIngots) {
            log.info("  -> {} NICHT verfuegbar: VOLL ({} >= {})",
                yard.getYardNumber(), totalExpectedCount, maxIngots);
            return false;
        }

        // Bei belegtem Platz: Produkt pruefen
        if (currentCount > 0) {
            Optional<StockyardStatus> status = stockyardStatusRepository.findByStockyardId(yard.getId());
            if (status.isPresent() && status.get().getProductId() != null) {
                boolean sameProduct = status.get().getProductId().equals(productId);
                if (!sameProduct) {
                    log.info("  -> {} NICHT verfuegbar: Anderes Produkt", yard.getYardNumber());
                }
                return sameProduct;
            }
        }

        log.info("  -> {} VERFUEGBAR", yard.getYardNumber());
        return true;
    }

    /**
     * Zaehlt offene Transportauftraege, die einen bestimmten Lagerplatz als Ziel haben.
     */
    private int countPendingTransportsToYard(Long yardId) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM TD_TRANSPORTORDER WHERE TO_YARD_ID = ? AND STATUS IN ('P', 'I', 'U', 'H')",
                Integer.class, yardId);
            int result = count != null ? count : 0;
            if (result > 0) {
                log.info("     Offene Transporte zu Platz ID={}: {}", yardId, result);
            }
            return result;
        } catch (Exception e) {
            log.warn("Fehler beim Zaehlen der offenen Transportauftraege fuer Platz {}: {}", yardId, e.getMessage());
            return 0;
        }
    }

    /**
     * Prueft ob ein Lagerplatz leer ist
     */
    private boolean isYardEmpty(Long stockyardId) {
        int count = ingotService.countByStockyardId(stockyardId);
        log.info("  isYardEmpty check: StockyardID={}, count={}, isEmpty={}", stockyardId, count, count == 0);
        return count == 0;
    }

    /**
     * Prueft ob ein Lagerplatz noch Kapazitaet hat und das gleiche Produkt hat
     * (oder leer ist, dann kann jedes Produkt darauf)
     */
    private boolean hasCapacity(Stockyard yard, Long productId) {
        int currentCount = ingotService.countByStockyardId(yard.getId());
        int pendingTransports = countPendingTransportsToYard(yard.getId());
        int totalExpected = currentCount + pendingTransports;
        int maxIngots = yard.getMaxIngots();

        // Kein Platz mehr?
        if (totalExpected >= maxIngots) {
            return false;
        }

        // Platz ist leer - kann jedes Produkt aufnehmen
        if (currentCount == 0) {
            log.info("  hasCapacity: {} ist leer, Kapazitaet {}/{}", yard.getYardNumber(), totalExpected, maxIngots);
            return true;
        }

        // Platz hat Barren - pruefen ob gleiches Produkt
        Optional<StockyardStatus> status = stockyardStatusRepository.findByStockyardId(yard.getId());
        if (status.isPresent() && status.get().getProductId() != null) {
            boolean sameProduct = status.get().getProductId().equals(productId);
            if (sameProduct) {
                log.info("  hasCapacity: {} hat gleiches Produkt, Kapazitaet {}/{}",
                    yard.getYardNumber(), totalExpected, maxIngots);
                return true;
            } else {
                log.debug("  hasCapacity: {} hat anderes Produkt", yard.getYardNumber());
                return false;
            }
        }

        // Kein Produkt zugeordnet - kann verwendet werden
        log.info("  hasCapacity: {} hat kein Produkt zugeordnet, Kapazitaet {}/{}",
            yard.getYardNumber(), totalExpected, maxIngots);
        return true;
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
     * Generiert eine neue Transport-Auftragsnummer (max 10 Zeichen für Oracle)
     * Format: TAYY-NNNN (z.B. TA26-0001)
     */
    private String generateTransportNo() {
        int year = java.time.LocalDate.now().getYear() % 100; // 2-stelliges Jahr
        String prefix = "TA" + String.format("%02d", year) + "-";
        try {
            Integer maxNo = jdbcTemplate.queryForObject(
                "SELECT NVL(MAX(TO_NUMBER(SUBSTR(TRANSPORT_NO, 6))), 0) + 1 FROM TD_TRANSPORTORDER WHERE TRANSPORT_NO LIKE ?",
                Integer.class, prefix + "%");
            return prefix + String.format("%04d", maxNo != null ? maxNo : 1);
        } catch (Exception e) {
            log.warn("Could not generate transport number, using timestamp", e);
            return prefix + String.format("%04d", (System.currentTimeMillis() % 10000));
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
