package com.hydro.plsbl.service;

import com.hydro.plsbl.dto.StockyardDTO;
import com.hydro.plsbl.dto.StockyardStatusDTO;
import com.hydro.plsbl.entity.enums.StockyardUsage;
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

    // Konstanten für Lagerplatz-Dimensionen
    private static final int SHORT_LENGTH = 1800;  // mm
    private static final int LONG_LENGTH = 2500;   // mm
    private static final int SHORT_MAX_INGOTS = 10;
    private static final int LONG_MAX_INGOTS = 6;
    
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
            // Create - direktes SQL (kompatibel mit H2 und Oracle)
            Long newId = getNextId();

            jdbcTemplate.update(
                "INSERT INTO MD_STOCKYARD (ID, YARD_NO, X_COORDINATE, Y_COORDINATE, DESCRIPTION, " +
                "YARD_TYPE, YARD_USAGE, BOTTOM_CENTER_X, BOTTOM_CENTER_Y, BOTTOM_CENTER_Z, " +
                "LENGTH, WIDTH, HEIGHT, MAX_INGOTS, TO_STOCK_ALLOWED, FROM_STOCK_ALLOWED, " +
                "MOVEMENT_COEFF, SERIAL) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 1)",
                newId,
                dto.getYardNumber(),
                dto.getXCoordinate(),
                dto.getYCoordinate(),
                dto.getDescription(),
                dto.getType() != null ? String.valueOf(dto.getType().getCode()) : null,
                dto.getUsage() != null ? String.valueOf(dto.getUsage().getCode()) : null,
                dto.getXPosition() > 0 ? dto.getXPosition() : 0,  // BOTTOM_CENTER_X
                dto.getYPosition() > 0 ? dto.getYPosition() : 0,  // BOTTOM_CENTER_Y
                dto.getZPosition() > 0 ? dto.getZPosition() : 0,  // BOTTOM_CENTER_Z
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
                "SELECT COALESCE(MAX(ID), 0) + 1 FROM MD_STOCKYARD", Long.class);
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
    
    // === Merge/Split Methoden ===

    /**
     * Prüft ob ein Lagerplatz zusammengefügt werden kann (SHORT und leer)
     */
    public boolean canMerge(Long stockyardId) {
        return stockyardRepository.findById(stockyardId)
            .map(yard -> yard.getUsage() == StockyardUsage.SHORT
                         && countIngotsOnStockyard(stockyardId) == 0)
            .orElse(false);
    }

    /**
     * Prüft ob ein Lagerplatz geteilt werden kann (LONG, leer, und mindestens eine Nachbarposition frei)
     */
    public boolean canSplit(Long stockyardId) {
        return stockyardRepository.findById(stockyardId)
            .map(yard -> {
                // Muss LONG sein
                if (yard.getUsage() != StockyardUsage.LONG) {
                    return false;
                }
                // Muss leer sein
                if (countIngotsOnStockyard(stockyardId) > 0) {
                    return false;
                }
                // Mindestens eine Nachbarposition muss frei sein
                int x = yard.getXCoordinate();
                int y = yard.getYCoordinate();
                boolean rightFree = stockyardRepository.countByCoordinates(x + 1, y) == 0;
                boolean leftFree = stockyardRepository.countByCoordinates(x - 1, y) == 0;
                return rightFree || leftFree;
            })
            .orElse(false);
    }

    /**
     * Findet benachbarte leere Lagerplätze für Merge-Operation.
     * Benachbart = gleiche Y-Koordinate, X-Koordinate +1 oder -1.
     * Beide Plätze müssen SHORT und leer sein.
     *
     * @param stockyardId Die Quell-Lagerplatz-ID
     * @return Liste der benachbarten leeren kurzen Lagerplätze
     * @throws IllegalStateException wenn Quell-Lagerplatz nicht leer oder nicht kurz ist
     */
    public List<StockyardDTO> findAdjacentEmptyStockyards(Long stockyardId) {
        log.debug("Finding adjacent empty stockyards for ID: {}", stockyardId);

        Stockyard source = stockyardRepository.findById(stockyardId)
            .orElseThrow(() -> new RuntimeException("Lagerplatz nicht gefunden: " + stockyardId));

        // Validierung: Quelle muss SHORT sein
        if (source.getUsage() != StockyardUsage.SHORT) {
            throw new IllegalStateException("Nur kurze Lagerplätze können zusammengefügt werden");
        }

        // Validierung: Quelle muss leer sein
        if (countIngotsOnStockyard(stockyardId) > 0) {
            throw new IllegalStateException("Lagerplatz muss leer sein");
        }

        int y = source.getYCoordinate();
        int x = source.getXCoordinate();

        // Benachbarte Plätze finden (X-1 und X+1 mit gleichem Y)
        List<Stockyard> adjacent = stockyardRepository.findAdjacentShortStockyards(y, x - 1, x + 1);

        // Filtern: nur leere Plätze
        return adjacent.stream()
            .filter(yard -> countIngotsOnStockyard(yard.getId()) == 0)
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    /**
     * Fügt zwei benachbarte kurze Lagerplätze zu einem langen zusammen.
     * Der linke Lagerplatz (niedrigere X) wird zum zusammengefügten Platz.
     * Der rechte Lagerplatz wird gelöscht.
     *
     * @param stockyardId1 ID des ersten Lagerplatzes
     * @param stockyardId2 ID des zweiten Lagerplatzes
     * @return Der zusammengefügte Lagerplatz-DTO
     * @throws IllegalStateException wenn Vorbedingungen nicht erfüllt sind
     */
    @Transactional
    public StockyardDTO mergeStockyards(Long stockyardId1, Long stockyardId2) {
        log.info("Merging stockyards {} and {}", stockyardId1, stockyardId2);

        Stockyard yard1 = stockyardRepository.findById(stockyardId1)
            .orElseThrow(() -> new RuntimeException("Lagerplatz nicht gefunden: " + stockyardId1));
        Stockyard yard2 = stockyardRepository.findById(stockyardId2)
            .orElseThrow(() -> new RuntimeException("Lagerplatz nicht gefunden: " + stockyardId2));

        // Validierungen
        validateMergeConditions(yard1, yard2);

        // Bestimme links (niedrigere X) und rechts (höhere X)
        Stockyard left = yard1.getXCoordinate() < yard2.getXCoordinate() ? yard1 : yard2;
        Stockyard right = yard1.getXCoordinate() < yard2.getXCoordinate() ? yard2 : yard1;

        // Linken Platz zu LONG umwandeln
        left.markNotNew();
        left.setUsage(StockyardUsage.LONG);
        left.setLength(LONG_LENGTH);
        left.setMaxIngots(LONG_MAX_INGOTS);

        // BOTTOM_CENTER_X neu berechnen (Mittelpunkt der beiden)
        int newCenterX = (left.getXPosition() + right.getXPosition()) / 2;
        left.setXPosition(newCenterX);

        // Linken Platz speichern
        left = stockyardRepository.save(left);

        // Rechten Platz löschen (samt Status falls vorhanden)
        statusRepository.findByStockyardId(right.getId()).ifPresent(statusRepository::delete);
        stockyardRepository.deleteById(right.getId());

        log.info("Stockyards merged: {} + {} -> {} (Lang)",
            stockyardId1, stockyardId2, left.getYardNumber());

        return toDTO(left);
    }

    private void validateMergeConditions(Stockyard yard1, Stockyard yard2) {
        // Beide müssen SHORT sein
        if (yard1.getUsage() != StockyardUsage.SHORT || yard2.getUsage() != StockyardUsage.SHORT) {
            throw new IllegalStateException("Beide Lagerplätze müssen kurz (S) sein");
        }

        // Gleiche Y-Koordinate
        if (yard1.getYCoordinate() != yard2.getYCoordinate()) {
            throw new IllegalStateException("Lagerplätze müssen gleiche Y-Koordinate haben");
        }

        // Benachbarte X-Koordinaten (Unterschied = 1)
        if (Math.abs(yard1.getXCoordinate() - yard2.getXCoordinate()) != 1) {
            throw new IllegalStateException("Lagerplätze müssen nebeneinander liegen");
        }

        // Beide müssen leer sein
        if (countIngotsOnStockyard(yard1.getId()) > 0) {
            throw new IllegalStateException("Lagerplatz " + yard1.getYardNumber() + " muss leer sein");
        }
        if (countIngotsOnStockyard(yard2.getId()) > 0) {
            throw new IllegalStateException("Lagerplatz " + yard2.getYardNumber() + " muss leer sein");
        }
    }

    /**
     * Teilt einen langen Lagerplatz in zwei kurze Lagerplätze.
     * Der Original-Platz wird zum linken (niedrigere X) kurzen Platz.
     * Ein neuer Platz wird bei X+1 als rechter kurzer Platz erstellt.
     *
     * @param stockyardId ID des zu teilenden langen Lagerplatzes
     * @return Array mit zwei DTOs: [links, rechts]
     * @throws IllegalStateException wenn Vorbedingungen nicht erfüllt sind
     */
    @Transactional
    public StockyardDTO[] splitStockyard(Long stockyardId) {
        log.info("Splitting stockyard {}", stockyardId);

        Stockyard source = stockyardRepository.findById(stockyardId)
            .orElseThrow(() -> new RuntimeException("Lagerplatz nicht gefunden: " + stockyardId));

        // Validierungen
        validateSplitConditions(source);

        int y = source.getYCoordinate();
        int sourceX = source.getXCoordinate();

        // Prüfen welche Richtung frei ist (X+1 oder X-1)
        boolean rightFree = stockyardRepository.countByCoordinates(sourceX + 1, y) == 0;
        boolean leftFree = stockyardRepository.countByCoordinates(sourceX - 1, y) == 0;

        if (!rightFree && !leftFree) {
            throw new IllegalStateException(
                "Beide Nachbarpositionen sind bereits belegt. Split nicht möglich.");
        }

        // Bestimme neue Position (bevorzugt rechts/X+1, sonst links/X-1)
        int newX = rightFree ? sourceX + 1 : sourceX - 1;

        // Positionen für Split berechnen
        int originalCenterX = source.getXPosition();
        int sourceCenterX, newCenterX;

        if (rightFree) {
            // Neuer Platz rechts (höhere X-Koordinate)
            sourceCenterX = originalCenterX - (LONG_LENGTH - SHORT_LENGTH) / 2;
            newCenterX = originalCenterX + (LONG_LENGTH - SHORT_LENGTH) / 2;
        } else {
            // Neuer Platz links (niedrigere X-Koordinate)
            sourceCenterX = originalCenterX + (LONG_LENGTH - SHORT_LENGTH) / 2;
            newCenterX = originalCenterX - (LONG_LENGTH - SHORT_LENGTH) / 2;
        }

        // Original zu SHORT umwandeln
        source.markNotNew();
        source.setUsage(StockyardUsage.SHORT);
        source.setLength(SHORT_LENGTH);
        source.setMaxIngots(SHORT_MAX_INGOTS);
        source.setXPosition(sourceCenterX);
        source = stockyardRepository.save(source);

        // Neuen Platz erstellen
        StockyardDTO newDTO = new StockyardDTO();
        newDTO.setYardNumber(String.format("%02d/%02d", newX, y));
        newDTO.setXCoordinate(newX);
        newDTO.setYCoordinate(y);
        newDTO.setDescription(source.getDescription());
        newDTO.setType(source.getType());
        newDTO.setUsage(StockyardUsage.SHORT);
        newDTO.setXPosition(newCenterX);
        newDTO.setYPosition(source.getYPosition());
        newDTO.setZPosition(source.getZPosition());
        newDTO.setLength(SHORT_LENGTH);
        newDTO.setWidth(source.getWidth());
        newDTO.setHeight(source.getHeight());
        newDTO.setMaxIngots(SHORT_MAX_INGOTS);
        newDTO.setToStockAllowed(source.isToStockAllowed());
        newDTO.setFromStockAllowed(source.isFromStockAllowed());

        StockyardDTO savedNew = save(newDTO);

        log.info("Stockyard split: {} -> {} (Kurz) + {} (Kurz)",
            stockyardId, source.getYardNumber(), savedNew.getYardNumber());

        // Rückgabe: [original, neu] sortiert nach X-Koordinate
        if (sourceX < newX) {
            return new StockyardDTO[] { toDTO(source), savedNew };
        } else {
            return new StockyardDTO[] { savedNew, toDTO(source) };
        }
    }

    private void validateSplitConditions(Stockyard source) {
        // Muss LONG sein
        if (source.getUsage() != StockyardUsage.LONG) {
            throw new IllegalStateException("Nur lange Lagerplätze können geteilt werden");
        }

        // Muss leer sein
        if (countIngotsOnStockyard(source.getId()) > 0) {
            throw new IllegalStateException("Lagerplatz muss leer sein");
        }
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
