package com.hydro.plsbl.service;

import com.hydro.plsbl.dto.StockyardDTO;
import com.hydro.plsbl.dto.StockyardStatusDTO;
import com.hydro.plsbl.entity.masterdata.Stockyard;
import com.hydro.plsbl.entity.transdata.StockyardStatus;
import com.hydro.plsbl.repository.StockyardRepository;
import com.hydro.plsbl.repository.StockyardStatusRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service für Stockyard (Lagerplatz) Operationen
 */
@Service
@Transactional(readOnly = true)
public class StockyardService {
    
    private static final Logger log = LoggerFactory.getLogger(StockyardService.class);
    
    private final StockyardRepository stockyardRepository;
    private final StockyardStatusRepository statusRepository;
    private final JdbcTemplate jdbcTemplate;
    
    public StockyardService(StockyardRepository stockyardRepository,
                           StockyardStatusRepository statusRepository,
                           JdbcTemplate jdbcTemplate) {
        this.stockyardRepository = stockyardRepository;
        this.statusRepository = statusRepository;
        this.jdbcTemplate = jdbcTemplate;
    }
    
    /**
     * Lädt alle Lagerplätze für die Stock-View mit Status-Informationen
     */
    public Map<Long, StockyardDTO> findAllForStockView() {
        log.debug("Loading all stockyards for stock view");
        
        List<Stockyard> stockyards = stockyardRepository.findAllForStockView();
        log.debug("Found {} stockyards", stockyards.size());
        
        // Alle IDs sammeln
        List<Long> stockyardIds = stockyards.stream()
            .map(Stockyard::getId)
            .collect(Collectors.toList());
        
        // Status für alle Lagerplätze laden
        Map<Long, StockyardStatus> statusMap = statusRepository.findByStockyardIdIn(stockyardIds)
            .stream()
            .collect(Collectors.toMap(StockyardStatus::getStockyardId, s -> s));
        
        // DTOs erstellen
        Map<Long, StockyardDTO> result = new LinkedHashMap<>();
        
        for (Stockyard yard : stockyards) {
            StockyardDTO dto = toDTO(yard);

            // Status hinzufügen
            StockyardStatus status = statusMap.get(yard.getId());
            if (status != null) {
                dto.setStatus(toStatusDTO(status, yard.getMaxIngots()));
            }

            // Debug für SAW-Plätze
            if (yard.getType() == com.hydro.plsbl.entity.enums.StockyardType.SAW) {
                log.info("SAW stockyard loaded: {} (ID={}) - hasStatus={}, ingotsCount={}",
                    yard.getYardNumber(), yard.getId(),
                    status != null,
                    status != null ? status.getIngotsCount() : 0);
            }

            result.put(dto.getId(), dto);
        }
        
        log.debug("Returning {} stockyard DTOs", result.size());
        return result;
    }
    
    /**
     * Findet einen Lagerplatz nach ID
     */
    public Optional<StockyardDTO> findById(Long id) {
        return stockyardRepository.findById(id)
            .map(yard -> {
                StockyardDTO dto = toDTO(yard);
                statusRepository.findByStockyardId(id)
                    .ifPresent(status -> dto.setStatus(toStatusDTO(status, yard.getMaxIngots())));
                return dto;
            });
    }
    
    /**
     * Findet einen Lagerplatz nach Platznummer
     */
    public Optional<StockyardDTO> findByYardNumber(String yardNumber) {
        return stockyardRepository.findByYardNumber(yardNumber)
            .map(yard -> {
                StockyardDTO dto = toDTO(yard);
                statusRepository.findByStockyardId(yard.getId())
                    .ifPresent(status -> dto.setStatus(toStatusDTO(status, yard.getMaxIngots())));
                return dto;
            });
    }
    
    /**
     * Speichert einen Lagerplatz
     */
    @Transactional
    public StockyardDTO save(StockyardDTO dto) {
        log.debug("Saving stockyard: {}", dto.getId());

        if (dto.getId() != null) {
            // Update - existierenden Eintrag aktualisieren
            Stockyard entity = stockyardRepository.findById(dto.getId())
                .orElseThrow(() -> new RuntimeException("Stockyard not found: " + dto.getId()));
            entity.markNotNew();
            updateFromDTO(entity, dto);
            entity = stockyardRepository.save(entity);
            log.info("Stockyard updated with ID: {}", entity.getId());
            return toDTO(entity);
        } else {
            // Create - direktes SQL für Oracle
            // Nur Spalten verwenden die sicher existieren
            Long newId = getNextId();

            jdbcTemplate.update(
                "INSERT INTO MD_STOCKYARD (ID, YARD_NO, X_COORDINATE, Y_COORDINATE, DESCRIPTION, " +
                "YARD_TYPE, YARD_USAGE, LENGTH, WIDTH, HEIGHT, MAX_INGOTS, TO_STOCK_ALLOWED, FROM_STOCK_ALLOWED, " +
                "HIDE_IF_EMPTY, MOVEMENT_COEFF, SERIAL, TABLESERIAL) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 1, 1, 1)",
                newId,
                dto.getYardNumber(),
                dto.getXCoordinate(),
                dto.getYCoordinate(),
                dto.getDescription(),
                dto.getType() != null ? String.valueOf(dto.getType().getCode()) : null,
                dto.getUsage() != null ? String.valueOf(dto.getUsage().getCode()) : null,
                dto.getLength() > 0 ? dto.getLength() : 1000,
                dto.getWidth() > 0 ? dto.getWidth() : 1000,
                dto.getHeight() > 0 ? dto.getHeight() : 1000,
                dto.getMaxIngots() > 0 ? dto.getMaxIngots() : 10,
                dto.isToStockAllowed() ? 1 : 0,
                dto.isFromStockAllowed() ? 1 : 0
            );

            dto.setId(newId);
            log.info("Stockyard created with ID: {}", newId);
            return dto;
        }
    }

    private Long getNextId() {
        try {
            Long maxId = jdbcTemplate.queryForObject(
                "SELECT NVL(MAX(ID), 0) + 1 FROM MD_STOCKYARD", Long.class);
            return maxId != null ? maxId : 1L;
        } catch (Exception e) {
            log.warn("Could not get next ID, using timestamp", e);
            return System.currentTimeMillis();
        }
    }

    /**
     * Löscht einen Lagerplatz
     * @throws IllegalStateException wenn noch Barren auf dem Lagerplatz sind
     */
    @Transactional
    public void delete(Long id) {
        log.debug("Deleting stockyard: {}", id);

        // Prüfen ob noch Barren auf dem Lagerplatz sind
        int ingotCount = countIngotsOnStockyard(id);
        if (ingotCount > 0) {
            throw new IllegalStateException(
                "Lagerplatz kann nicht gelöscht werden: Es befinden sich noch " + ingotCount + " Barren darauf.");
        }

        // Zuerst den Status löschen (falls vorhanden)
        statusRepository.findByStockyardId(id).ifPresent(status -> {
            statusRepository.delete(status);
            log.debug("StockyardStatus deleted for stockyard {}", id);
        });

        // Dann den Lagerplatz selbst löschen
        stockyardRepository.deleteById(id);
        log.info("Stockyard deleted: {}", id);
    }

    /**
     * Löscht einen Lagerplatz FORCIERT (auch wenn Barren darauf sind)
     * Die Barren werden vom Platz entfernt (STOCKYARD_ID = NULL)
     */
    @Transactional
    public void forceDelete(Long id) {
        log.info("Force deleting stockyard: {}", id);

        // Barren vom Platz entfernen (nicht löschen, nur STOCKYARD_ID auf NULL setzen)
        int updated = jdbcTemplate.update(
            "UPDATE TD_INGOT SET STOCKYARD_ID = NULL, PILE_POSITION = NULL WHERE STOCKYARD_ID = ?",
            id);
        if (updated > 0) {
            log.info("Removed {} ingots from stockyard {}", updated, id);
        }

        // Status löschen
        statusRepository.findByStockyardId(id).ifPresent(status -> {
            statusRepository.delete(status);
            log.debug("StockyardStatus deleted for stockyard {}", id);
        });

        // Lagerplatz löschen
        stockyardRepository.deleteById(id);
        log.info("Stockyard force deleted: {}", id);
    }

    /**
     * Löscht einen Lagerplatz nach Platznummer (FORCIERT)
     */
    @Transactional
    public boolean forceDeleteByYardNumber(String yardNumber) {
        log.info("Force deleting stockyard by yard number: {}", yardNumber);

        return stockyardRepository.findByYardNumber(yardNumber)
            .map(yard -> {
                forceDelete(yard.getId());
                return true;
            })
            .orElseGet(() -> {
                log.warn("Stockyard not found: {}", yardNumber);
                return false;
            });
    }

    /**
     * Zählt die Barren auf einem Lagerplatz
     */
    public int countIngotsOnStockyard(Long stockyardId) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM TD_INGOT WHERE STOCKYARD_ID = ?",
                Integer.class,
                stockyardId);
            return count != null ? count : 0;
        } catch (Exception e) {
            log.warn("Could not count ingots on stockyard {}", stockyardId, e);
            return 0;
        }
    }
    
    /**
     * Ermittelt die Grid-Dimensionen (max X und Y Koordinaten)
     */
    public int[] getGridDimensions() {
        String sql = "SELECT MAX(X_COORDINATE), MAX(Y_COORDINATE) FROM MD_STOCKYARD";
        return jdbcTemplate.queryForObject(sql, (rs, rowNum) ->
            new int[] { rs.getInt(1), rs.getInt(2) }
        );
    }

    /**
     * Findet verfügbare Ziel-Lagerplätze (Einlagern erlaubt, nicht voll)
     */
    public List<StockyardDTO> findAvailableDestinations() {
        log.debug("Loading available destination stockyards");

        List<Stockyard> stockyards = stockyardRepository.findAvailableDestinations();

        // IDs sammeln für Status-Abfrage
        List<Long> stockyardIds = stockyards.stream()
            .map(Stockyard::getId)
            .collect(Collectors.toList());

        // Status laden
        Map<Long, StockyardStatus> statusMap = statusRepository.findByStockyardIdIn(stockyardIds)
            .stream()
            .collect(Collectors.toMap(StockyardStatus::getStockyardId, s -> s));

        // DTOs erstellen und nach verfügbarem Platz filtern
        return stockyards.stream()
            .map(yard -> {
                StockyardDTO dto = toDTO(yard);
                StockyardStatus status = statusMap.get(yard.getId());
                if (status != null) {
                    dto.setStatus(toStatusDTO(status, yard.getMaxIngots()));
                }
                return dto;
            })
            .filter(dto -> !dto.isFull()) // Nur nicht-volle Plätze
            .collect(Collectors.toList());
    }
    
    // === Mapping Methoden ===
    
    private StockyardDTO toDTO(Stockyard entity) {
        StockyardDTO dto = new StockyardDTO();
        dto.setId(entity.getId());
        dto.setYardNumber(entity.getYardNumber());
        dto.setXCoordinate(entity.getXCoordinate());
        dto.setYCoordinate(entity.getYCoordinate());
        dto.setDescription(entity.getDescription());
        dto.setType(entity.getType());
        dto.setUsage(entity.getUsage());
        dto.setXPosition(entity.getXPosition());
        dto.setYPosition(entity.getYPosition());
        dto.setZPosition(entity.getZPosition());
        dto.setLength(entity.getLength());
        dto.setWidth(entity.getWidth());
        dto.setHeight(entity.getHeight());
        dto.setMaxIngots(entity.getMaxIngots());
        dto.setToStockAllowed(entity.isToStockAllowed());
        dto.setFromStockAllowed(entity.isFromStockAllowed());
        return dto;
    }
    
    private StockyardStatusDTO toStatusDTO(StockyardStatus status, int maxIngots) {
        StockyardStatusDTO dto = new StockyardStatusDTO();
        dto.setId(status.getId());
        dto.setStockyardId(status.getStockyardId());
        dto.setProductId(status.getProductId());
        dto.setIngotsCount(status.getIngotsCount());
        dto.setEmpty(status.getIngotsCount() == 0);
        dto.setFull(status.getIngotsCount() >= maxIngots);
        dto.setNeighborId(status.getNeighborId());

        // Produkt-Nummer laden (optional)
        if (status.getProductId() != null) {
            try {
                String productNo = jdbcTemplate.queryForObject(
                    "SELECT PRODUCT_NO FROM MD_PRODUCT WHERE ID = ?",
                    String.class,
                    status.getProductId()
                );
                dto.setProductNumber(productNo);
            } catch (Exception e) {
                // Produkt nicht gefunden - ignorieren
            }
        }

        // Barren-Nummer laden (für alle Plätze mit Barren, z.B. SAW-Plätze)
        if (status.getIngotsCount() > 0) {
            try {
                String ingotNo = jdbcTemplate.queryForObject(
                    "SELECT INGOT_NO FROM TD_INGOT WHERE STOCKYARD_ID = ? ORDER BY PILE_POSITION DESC FETCH FIRST 1 ROWS ONLY",
                    String.class,
                    status.getStockyardId()
                );
                dto.setIngotNumber(ingotNo);
            } catch (Exception e) {
                log.debug("Ingot number not found for stockyard {}: {}",
                    status.getStockyardId(), e.getMessage());
            }
        }

        return dto;
    }
    
    private void updateFromDTO(Stockyard entity, StockyardDTO dto) {
        entity.setYardNumber(dto.getYardNumber());
        entity.setXCoordinate(dto.getXCoordinate());
        entity.setYCoordinate(dto.getYCoordinate());
        entity.setDescription(dto.getDescription());
        entity.setType(dto.getType());
        entity.setUsage(dto.getUsage());
        entity.setXPosition(dto.getXPosition());
        entity.setYPosition(dto.getYPosition());
        entity.setZPosition(dto.getZPosition());
        entity.setLength(dto.getLength());
        entity.setWidth(dto.getWidth());
        entity.setHeight(dto.getHeight());
        entity.setMaxIngots(dto.getMaxIngots());
        entity.setToStockAllowed(dto.isToStockAllowed());
        entity.setFromStockAllowed(dto.isFromStockAllowed());
    }
}
