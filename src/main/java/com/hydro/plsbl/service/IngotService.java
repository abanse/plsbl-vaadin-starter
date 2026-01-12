package com.hydro.plsbl.service;

import com.hydro.plsbl.dto.IngotDTO;
import com.hydro.plsbl.entity.transdata.Ingot;
import com.hydro.plsbl.entity.transdata.StockyardStatus;
import com.hydro.plsbl.repository.IngotRepository;
import com.hydro.plsbl.repository.StockyardStatusRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service für Barren-Operationen
 */
@Service
@Transactional(readOnly = true)
public class IngotService {

    private static final Logger log = LoggerFactory.getLogger(IngotService.class);

    private final IngotRepository ingotRepository;
    private final StockyardStatusRepository stockyardStatusRepository;
    private final JdbcTemplate jdbcTemplate;

    public IngotService(IngotRepository ingotRepository,
                        StockyardStatusRepository stockyardStatusRepository,
                        JdbcTemplate jdbcTemplate) {
        this.ingotRepository = ingotRepository;
        this.stockyardStatusRepository = stockyardStatusRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Findet alle Barren auf einem Lagerplatz
     */
    public List<IngotDTO> findByStockyardId(Long stockyardId) {
        log.debug("Loading ingots for stockyard {}", stockyardId);

        List<Ingot> ingots = ingotRepository.findByStockyardId(stockyardId);

        return ingots.stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    /**
     * Findet einen Barren anhand der Nummer
     */
    public Optional<IngotDTO> findByIngotNo(String ingotNo) {
        return ingotRepository.findByIngotNo(ingotNo)
            .map(this::toDTO);
    }

    /**
     * Findet einen Barren anhand der ID
     */
    public Optional<IngotDTO> findById(Long id) {
        return ingotRepository.findById(id)
            .map(this::toDTO);
    }

    /**
     * Findet alle Barren eines Produkts
     */
    public List<IngotDTO> findByProductId(Long productId) {
        return ingotRepository.findByProductId(productId).stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    /**
     * Findet den obersten Barren auf einem Lagerplatz
     */
    public Optional<IngotDTO> findTopIngotOnStockyard(Long stockyardId) {
        return ingotRepository.findTopIngotOnStockyard(stockyardId)
            .map(this::toDTO);
    }

    /**
     * Zählt Barren auf einem Lagerplatz
     */
    public int countByStockyardId(Long stockyardId) {
        return ingotRepository.countByStockyardId(stockyardId);
    }

    /**
     * Findet die letzten N Barren
     */
    public List<IngotDTO> findLatest(int limit) {
        log.debug("Loading latest {} ingots", limit);
        return ingotRepository.findLatest(limit).stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    /**
     * Findet alle Barren
     */
    public List<IngotDTO> findAll() {
        log.debug("Loading all ingots");
        return ingotRepository.findAllOrdered().stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    /**
     * Zählt alle Barren
     */
    public int countAll() {
        return ingotRepository.countAll();
    }

    /**
     * Speichert einen neuen oder aktualisiert einen bestehenden Barren
     */
    @Transactional
    public IngotDTO save(IngotDTO dto) {
        log.debug("Saving ingot: {}", dto.getId());

        Ingot entity;
        if (dto.getId() != null) {
            // Update - mark as not new so Spring Data JDBC does UPDATE instead of INSERT
            entity = ingotRepository.findById(dto.getId())
                .orElseThrow(() -> new IllegalArgumentException("Barren nicht gefunden: " + dto.getId()));
            entity.markNotNew();
        } else {
            // Create - ID generieren (NOT NULL in Oracle)
            entity = new Ingot();
            Long nextId = getNextId();
            entity.setId(nextId);
        }

        // Werte übernehmen
        entity.setIngotNo(dto.getIngotNo());
        entity.setProductId(dto.getProductId());
        entity.setProductSuffix(dto.getProductSuffix());
        entity.setStockyardId(dto.getStockyardId());
        entity.setPilePosition(dto.getPilePosition());
        entity.setWeight(dto.getWeight());
        entity.setLength(dto.getLength());
        entity.setWidth(dto.getWidth());
        entity.setThickness(dto.getThickness());
        entity.setHeadSawn(dto.getHeadSawn());
        entity.setFootSawn(dto.getFootSawn());
        entity.setScrap(dto.getScrap());
        entity.setRevised(dto.getRevised());
        entity.setRotated(dto.getRotated());
        entity.setInStockSince(dto.getInStockSince());
        entity.setReleasedSince(dto.getReleasedSince());

        Ingot saved = ingotRepository.save(entity);
        log.info("Ingot saved with ID: {}", saved.getId());

        return toDTO(saved);
    }

    private Long getNextId() {
        try {
            Long maxId = jdbcTemplate.queryForObject(
                "SELECT NVL(MAX(ID), 0) + 1 FROM TD_INGOT", Long.class);
            return maxId != null ? maxId : 1L;
        } catch (Exception e) {
            log.warn("Could not get next ID, using timestamp", e);
            return System.currentTimeMillis();
        }
    }

    /**
     * Löscht einen Barren
     */
    @Transactional
    public void delete(Long id) {
        log.debug("Deleting ingot: {}", id);
        ingotRepository.deleteById(id);
        log.info("Ingot deleted: {}", id);
    }

    /**
     * Lagert einen Barren auf einen anderen Lagerplatz um
     */
    @Transactional
    public void relocate(Long ingotId, Long destinationStockyardId) {
        log.debug("Relocating ingot {} to stockyard {}", ingotId, destinationStockyardId);

        Ingot ingot = ingotRepository.findById(ingotId)
            .orElseThrow(() -> new IllegalArgumentException("Barren nicht gefunden: " + ingotId));

        Long sourceStockyardId = ingot.getStockyardId();
        Long productId = ingot.getProductId();

        ingot.markNotNew();
        ingot.setStockyardId(destinationStockyardId);

        // Neue Stapel-Position ermitteln (oberster Platz)
        int newPosition = countByStockyardId(destinationStockyardId) + 1;
        ingot.setPilePosition(newPosition);

        ingotRepository.save(ingot);

        // StockyardStatus aktualisieren
        updateStockyardStatusAfterRelocate(sourceStockyardId, destinationStockyardId, productId);

        log.info("Ingot {} relocated to stockyard {} at position {}",
            ingotId, destinationStockyardId, newPosition);
    }

    /**
     * Aktualisiert die StockyardStatus-Tabelle nach einer Umlagerung
     */
    private void updateStockyardStatusAfterRelocate(Long sourceStockyardId, Long destinationStockyardId, Long productId) {
        // 1. Quelle: Anzahl verringern
        stockyardStatusRepository.findByStockyardId(sourceStockyardId).ifPresent(status -> {
            int newCount = Math.max(0, status.getIngotsCount() - 1);
            if (newCount == 0) {
                // Status löschen wenn leer
                stockyardStatusRepository.delete(status);
                log.debug("StockyardStatus deleted for stockyard {}", sourceStockyardId);
            } else {
                // Anzahl verringern - direkt per SQL um Versionsprobleme zu vermeiden
                jdbcTemplate.update(
                    "UPDATE TD_STOCKYARDSTATUS SET INGOTS_COUNT = ?, SERIAL = SERIAL + 1 WHERE ID = ?",
                    newCount, status.getId());
                log.debug("StockyardStatus updated for source stockyard {}: count={}", sourceStockyardId, newCount);
            }
        });

        // 2. Ziel: Anzahl erhöhen oder neuen Status erstellen
        Optional<StockyardStatus> destStatus = stockyardStatusRepository.findByStockyardId(destinationStockyardId);
        if (destStatus.isPresent()) {
            // Anzahl erhöhen - direkt per SQL um Versionsprobleme zu vermeiden
            StockyardStatus status = destStatus.get();
            int newCount = status.getIngotsCount() + 1;
            jdbcTemplate.update(
                "UPDATE TD_STOCKYARDSTATUS SET INGOTS_COUNT = ?, SERIAL = SERIAL + 1 WHERE ID = ?",
                newCount, status.getId());
            log.debug("StockyardStatus updated for destination stockyard {}: count={}", destinationStockyardId, newCount);
        } else {
            // Neuen Status erstellen - YARD_USAGE vom Lagerplatz holen
            Long newId = getNextStatusId();
            String yardUsage = getYardUsageForStockyard(destinationStockyardId);
            jdbcTemplate.update(
                "INSERT INTO TD_STOCKYARDSTATUS (ID, STOCKYARD_ID, PRODUCT_ID, INGOTS_COUNT, YARD_USAGE, PILE_HEIGHT, SERIAL, TABLESERIAL) VALUES (?, ?, ?, 1, ?, 1, 1, 1)",
                newId, destinationStockyardId, productId, yardUsage);
            log.debug("StockyardStatus created for destination stockyard {} with yardUsage={}", destinationStockyardId, yardUsage);
        }
    }

    private Long getNextStatusId() {
        try {
            Long maxId = jdbcTemplate.queryForObject(
                "SELECT NVL(MAX(ID), 0) + 1 FROM TD_STOCKYARDSTATUS", Long.class);
            return maxId != null ? maxId : 1L;
        } catch (Exception e) {
            log.warn("Could not get next status ID, using timestamp", e);
            return System.currentTimeMillis();
        }
    }

    /**
     * Holt die YARD_USAGE vom Lagerplatz (MD_STOCKYARD)
     */
    private String getYardUsageForStockyard(Long stockyardId) {
        try {
            String usage = jdbcTemplate.queryForObject(
                "SELECT YARD_USAGE FROM MD_STOCKYARD WHERE ID = ?",
                String.class,
                stockyardId);
            return usage != null ? usage : "A"; // Default: Automatic
        } catch (Exception e) {
            log.warn("Could not get yard usage for stockyard {}, using default 'A'", stockyardId, e);
            return "A"; // Default: Automatic
        }
    }

    // === Mapping ===

    private IngotDTO toDTO(Ingot entity) {
        IngotDTO dto = new IngotDTO();
        dto.setId(entity.getId());
        dto.setIngotNo(entity.getIngotNo());
        dto.setProductId(entity.getProductId());
        dto.setProductSuffix(entity.getProductSuffix());
        dto.setStockyardId(entity.getStockyardId());
        dto.setPilePosition(entity.getPilePosition());
        dto.setWeight(entity.getWeight());
        dto.setLength(entity.getLength());
        dto.setWidth(entity.getWidth());
        dto.setThickness(entity.getThickness());
        dto.setHeadSawn(entity.getHeadSawn());
        dto.setFootSawn(entity.getFootSawn());
        dto.setScrap(entity.getScrap());
        dto.setRevised(entity.getRevised());
        dto.setRotated(entity.getRotated());
        dto.setInStockSince(entity.getInStockSince());
        dto.setReleasedSince(entity.getReleasedSince());

        // Produkt-Nummer laden
        if (entity.getProductId() != null) {
            try {
                String productNo = jdbcTemplate.queryForObject(
                    "SELECT PRODUCT_NO FROM MD_PRODUCT WHERE ID = ?",
                    String.class,
                    entity.getProductId()
                );
                dto.setProductNo(productNo);
            } catch (Exception e) {
                log.debug("Product not found for id {}", entity.getProductId());
            }
        }

        // Lagerplatz-Nummer laden
        if (entity.getStockyardId() != null) {
            try {
                String yardNo = jdbcTemplate.queryForObject(
                    "SELECT YARD_NO FROM MD_STOCKYARD WHERE ID = ?",
                    String.class,
                    entity.getStockyardId()
                );
                dto.setStockyardNo(yardNo);
            } catch (Exception e) {
                log.debug("Stockyard not found for id {}", entity.getStockyardId());
            }
        }

        return dto;
    }
}
