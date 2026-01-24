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

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
     * Zählt Barren auf mehreren Lagerplätzen DIREKT aus TD_INGOT.
     * Dies liefert aktuelle Daten, nicht aus TD_STOCKYARDSTATUS (kann veraltet sein).
     *
     * @param stockyardIds Liste der Lagerplatz-IDs
     * @return Map mit Lagerplatz-ID -> Anzahl Barren
     */
    private Map<Long, Integer> countIngotsOnStockyards(List<Long> stockyardIds) {
        if (stockyardIds == null || stockyardIds.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            String placeholders = stockyardIds.stream()
                .map(id -> "?")
                .collect(Collectors.joining(","));

            String sql = String.format(
                "SELECT STOCKYARD_ID, COUNT(*) as CNT FROM TD_INGOT " +
                "WHERE STOCKYARD_ID IN (%s) " +
                "GROUP BY STOCKYARD_ID",
                placeholders);

            Map<Long, Integer> result = new HashMap<>();
            jdbcTemplate.query(sql, rs -> {
                Long yardId = rs.getLong("STOCKYARD_ID");
                int count = rs.getInt("CNT");
                result.put(yardId, count);
            }, stockyardIds.toArray());

            return result;
        } catch (Exception e) {
            log.warn("Fehler beim Zaehlen der Barren: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Zählt offene Transport-Aufträge ZU den angegebenen Lagerplätzen.
     * Status: PENDING (P), IN_PROGRESS (I), PICKED_UP (U), PAUSED (H)
     * Dies verhindert, dass volle Lagerplätze als Ziel ausgewählt werden.
     *
     * @param stockyardIds Liste der Lagerplatz-IDs
     * @return Map mit Lagerplatz-ID -> Anzahl offener Transporte
     */
    private Map<Long, Integer> countPendingTransportsToYards(List<Long> stockyardIds) {
        if (stockyardIds == null || stockyardIds.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            // SQL für Oracle und H2 kompatibel
            String placeholders = stockyardIds.stream()
                .map(id -> "?")
                .collect(Collectors.joining(","));

            String sql = String.format(
                "SELECT TO_YARD_ID, COUNT(*) as CNT FROM TD_TRANSPORTORDER " +
                "WHERE TO_YARD_ID IN (%s) AND STATUS IN ('P', 'I', 'U', 'H') " +
                "GROUP BY TO_YARD_ID",
                placeholders);

            Map<Long, Integer> result = new HashMap<>();
            jdbcTemplate.query(sql, rs -> {
                Long yardId = rs.getLong("TO_YARD_ID");
                int count = rs.getInt("CNT");
                result.put(yardId, count);
            }, stockyardIds.toArray());

            if (!result.isEmpty()) {
                log.debug("Offene Transporte zu Lagerplaetzen: {}", result);
            }

            return result;
        } catch (Exception e) {
            log.warn("Fehler beim Zaehlen offener Transporte: {}", e.getMessage());
            return Collections.emptyMap();
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
     * Findet verfügbare Lagerplätze eines bestimmten Typs
     * Berücksichtigt auch offene Transport-Aufträge (PENDING, IN_PROGRESS, PICKED_UP, PAUSED)
     * Zählt Barren DIREKT aus TD_INGOT (nicht aus TD_STOCKYARDSTATUS) für aktuelle Daten.
     */
    public List<StockyardDTO> findAvailableByType(String typeCode) {
        log.info("=== findAvailableByType START (Typ={}) ===", typeCode);

        List<Stockyard> stockyards = stockyardRepository.findByType(typeCode);

        // IDs sammeln für Status-Abfrage
        List<Long> stockyardIds = stockyards.stream()
            .map(Stockyard::getId)
            .collect(Collectors.toList());

        // Status laden (für UI-Anzeige)
        Map<Long, StockyardStatus> statusMap = statusRepository.findByStockyardIdIn(stockyardIds)
            .stream()
            .collect(Collectors.toMap(StockyardStatus::getStockyardId, s -> s));

        // Offene Transport-Aufträge pro Ziel-Lagerplatz zählen
        Map<Long, Integer> pendingTransportsMap = countPendingTransportsToYards(stockyardIds);

        // Aktuelle Barren-Anzahl DIREKT aus TD_INGOT zählen (nicht aus Status-Tabelle)
        Map<Long, Integer> actualIngotCounts = countIngotsOnStockyards(stockyardIds);

        // DTOs erstellen und nach verfügbarem Platz filtern (inkl. offener Transporte)
        List<StockyardDTO> result = stockyards.stream()
            .map(yard -> {
                StockyardDTO dto = toDTO(yard);
                StockyardStatus status = statusMap.get(yard.getId());
                if (status != null) {
                    dto.setStatus(toStatusDTO(status, yard.getMaxIngots()));
                }
                return dto;
            })
            .filter(dto -> {
                // 1. Prüfen ob Einlagern erlaubt ist
                if (!dto.isToStockAllowed()) {
                    log.info("Lagerplatz {} (Typ {}): EINLAGERN NICHT ERLAUBT -> ABGELEHNT",
                        dto.getYardNumber(), typeCode);
                    return false;
                }

                // 2. Kapazitätsprüfung: Aktuelle Barren + offene Transporte
                int currentCount = actualIngotCounts.getOrDefault(dto.getId(), 0);
                int pendingTransports = pendingTransportsMap.getOrDefault(dto.getId(), 0);
                int totalExpected = currentCount + pendingTransports;
                int maxIngots = dto.getMaxIngots();

                if (totalExpected >= maxIngots) {
                    log.info("Lagerplatz {} (Typ {}): VOLL aktuell={}, pending={}, gesamt={}, max={}",
                        dto.getYardNumber(), typeCode, currentCount, pendingTransports, totalExpected, maxIngots);
                    return false;
                }
                return true;
            })
            .collect(Collectors.toList());

        log.info("=== findAvailableByType END: {} verfuegbare Plaetze ===", result.size());
        return result;
    }

    /**
     * Findet den ersten verfügbaren Lagerplatz eines bestimmten Typs
     */
    public Optional<StockyardDTO> findFirstAvailableByType(String typeCode) {
        return findAvailableByType(typeCode).stream().findFirst();
    }

    /**
     * Findet den Ziel-Lagerplatz aus einem offenen Transport-Auftrag für einen Barren.
     * Wird verwendet um den tatsächlichen Ziel-Platz zu markieren (nicht zu schätzen).
     *
     * @param ingotId ID des Barrens
     * @return Optional mit Ziel-Lagerplatz-ID oder leer wenn kein Auftrag gefunden
     */
    public Optional<Long> findPendingTransportOrderTarget(Long ingotId) {
        try {
            // Status-Codes: P=PENDING, I=IN_PROGRESS, U=PICKED_UP, H=PAUSED
            Long toYardId = jdbcTemplate.queryForObject(
                "SELECT TO_YARD_ID FROM TD_TRANSPORTORDER WHERE INGOT_ID = ? AND STATUS IN ('P', 'I', 'U', 'H') ORDER BY ID DESC FETCH FIRST 1 ROWS ONLY",
                Long.class, ingotId);
            log.info("Transport-Auftrag gefunden für Barren {}: Ziel-Platz ID={}", ingotId, toYardId);
            return Optional.ofNullable(toYardId);
        } catch (Exception e) {
            log.info("Kein offener Transport-Auftrag für Barren {} gefunden: {}", ingotId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Findet den Ziel-Lagerplatz aus einem aktiven Transport-Auftrag (IN_PROGRESS oder PICKED_UP)
     * der VON DER SÄGE kommt.
     * Wird verwendet, um die Markierung zu behalten, während der Kran einen Barren transportiert.
     *
     * WICHTIG: Nur Transporte von der Säge (YARD_TYPE='S') werden berücksichtigt,
     * damit alte/andere Transportaufträge nicht fälschlich markiert werden.
     *
     * @return Optional mit Ziel-Lagerplatz-ID oder leer wenn kein aktiver Transport läuft
     */
    public Optional<Long> findActiveTransportOrderTarget() {
        try {
            // Status-Codes: I=IN_PROGRESS, U=PICKED_UP (Kran ist aktiv unterwegs)
            // PENDING wird NICHT berücksichtigt - Markierung erscheint erst wenn Transport startet
            // Nach Abschluss (COMPLETED) wird die Markierung gelöscht
            // NUR Transporte von der Säge (YARD_TYPE='S') berücksichtigen!
            Long toYardId = jdbcTemplate.queryForObject(
                "SELECT t.TO_YARD_ID FROM TD_TRANSPORTORDER t " +
                "JOIN MD_STOCKYARD s ON t.FROM_YARD_ID = s.ID " +
                "WHERE t.STATUS IN ('I', 'U') AND s.YARD_TYPE = 'S' " +
                "ORDER BY t.ID DESC FETCH FIRST 1 ROWS ONLY",
                Long.class);
            log.debug("Aktiver Transport-Auftrag von Säge gefunden: Ziel-Platz ID={}", toYardId);
            return Optional.ofNullable(toYardId);
        } catch (Exception e) {
            log.debug("Kein aktiver Transport-Auftrag von Säge gefunden");
            return Optional.empty();
        }
    }

    /**
     * Findet den LONG-Lagerplatz, der einen bestimmten Lagerplatz abdeckt.
     * Wird verwendet wenn ein Ziel-Lagerplatz von einem LONG "geschluckt" wurde.
     *
     * Beispiel: 12/07 wurde zu 13/07 (LONG) zusammengefügt.
     * Wenn wir 12/07's ID übergeben, bekommen wir 13/07's ID zurück.
     *
     * @param stockyardId ID des (möglicherweise versteckten) Lagerplatzes
     * @return Optional mit ID des LONG-Lagerplatzes, oder leer wenn kein LONG gefunden
     */
    public Optional<Long> findCoveringLongStockyard(Long stockyardId) {
        try {
            // Zuerst die Koordinaten des Ziel-Lagerplatzes holen
            var coordResult = jdbcTemplate.queryForMap(
                "SELECT X_COORDINATE, Y_COORDINATE FROM MD_STOCKYARD WHERE ID = ?",
                stockyardId);

            int targetX = ((Number) coordResult.get("X_COORDINATE")).intValue();
            int targetY = ((Number) coordResult.get("Y_COORDINATE")).intValue();

            log.debug("Suche LONG für Position {}/{} (ID={})", targetX, targetY, stockyardId);

            // Suche nach LONG-Lagerplatz bei X+1 mit gleichem Y
            // LONG bei X deckt X und X-1 ab, also wenn unser Target bei X ist,
            // müsste der LONG bei X+1 sein
            Long longId = jdbcTemplate.queryForObject(
                "SELECT ID FROM MD_STOCKYARD WHERE X_COORDINATE = ? AND Y_COORDINATE = ? AND YARD_USAGE = 'L'",
                Long.class, targetX + 1, targetY);

            log.info("LONG gefunden: ID={} deckt Position {}/{} ab", longId, targetX, targetY);
            return Optional.of(longId);

        } catch (Exception e) {
            log.debug("Kein LONG-Lagerplatz gefunden für ID={}: {}", stockyardId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Findet einen passenden Lagerplatz für einen Barren basierend auf seiner Länge.
     * Lange Barren (>6000mm) -> LONG Lagerplätze
     * Kurze Barren (<=6000mm) -> SHORT Lagerplätze
     *
     * @param ingotLength Länge des Barrens in mm
     * @return Optional mit passendem Lagerplatz oder leer wenn keiner gefunden
     */
    public Optional<StockyardDTO> findMatchingStockyard(int ingotLength) {
        // Grenze für lange Barren (>6000mm = Lang)
        boolean isLongIngot = ingotLength > 6000;
        StockyardUsage targetUsage = isLongIngot ? StockyardUsage.LONG : StockyardUsage.SHORT;

        log.info("Suche passenden Lagerplatz für Barren mit Länge {}mm -> {}",
            ingotLength, targetUsage.getDisplayName());

        List<StockyardDTO> available = findAvailableDestinations();

        // Nach passender Verwendung filtern
        Optional<StockyardDTO> match = available.stream()
            .filter(yard -> yard.getUsage() == targetUsage)
            .filter(yard -> yard.getType() == com.hydro.plsbl.entity.enums.StockyardType.INTERNAL) // Nur interne Plätze
            .findFirst();

        if (match.isPresent()) {
            log.info("Passender Lagerplatz gefunden: {} ({})",
                match.get().getYardNumber(), match.get().getUsage().getDisplayName());
        } else {
            log.warn("Kein passender Lagerplatz für {} Barren gefunden",
                isLongIngot ? "langen" : "kurzen");
        }

        return match;
    }

    /**
     * Findet verfügbare Ziel-Lagerplätze (Einlagern erlaubt, nicht voll)
     * Berücksichtigt auch offene Transport-Aufträge (PENDING, IN_PROGRESS, PICKED_UP, PAUSED)
     * Zählt Barren DIREKT aus TD_INGOT (nicht aus TD_STOCKYARDSTATUS) für aktuelle Daten.
     */
    public List<StockyardDTO> findAvailableDestinations() {
        log.info("######################################################");
        log.info("### findAvailableDestinations V2 - MIT KAPAZITÄTSPRÜFUNG ###");
        log.info("######################################################");

        List<Stockyard> stockyards = stockyardRepository.findAvailableDestinations();
        log.info("Gefundene Lagerplaetze aus DB: {}", stockyards.size());

        // IDs sammeln für Status-Abfrage
        List<Long> stockyardIds = stockyards.stream()
            .map(Stockyard::getId)
            .collect(Collectors.toList());

        // Status laden (für UI-Anzeige)
        Map<Long, StockyardStatus> statusMap = statusRepository.findByStockyardIdIn(stockyardIds)
            .stream()
            .collect(Collectors.toMap(StockyardStatus::getStockyardId, s -> s));

        // Offene Transport-Aufträge pro Ziel-Lagerplatz zählen
        Map<Long, Integer> pendingTransportsMap = countPendingTransportsToYards(stockyardIds);
        log.info("Pending Transports Map: {}", pendingTransportsMap);

        // Aktuelle Barren-Anzahl DIREKT aus TD_INGOT zählen (nicht aus Status-Tabelle)
        Map<Long, Integer> actualIngotCounts = countIngotsOnStockyards(stockyardIds);
        log.info("Actual Ingot Counts (aus TD_INGOT): {}", actualIngotCounts);

        // DTOs erstellen und nach verfügbarem Platz filtern (inkl. offener Transporte)
        List<StockyardDTO> result = stockyards.stream()
            .map(yard -> {
                StockyardDTO dto = toDTO(yard);
                StockyardStatus status = statusMap.get(yard.getId());

                // WICHTIG: Status mit ECHTEN Zahlen aus TD_INGOT aktualisieren!
                int actualCount = actualIngotCounts.getOrDefault(yard.getId(), 0);

                if (status != null) {
                    StockyardStatusDTO statusDTO = toStatusDTO(status, yard.getMaxIngots());
                    // Überschreibe mit echtem Count aus TD_INGOT
                    statusDTO.setIngotsCount(actualCount);
                    dto.setStatus(statusDTO);
                } else if (actualCount > 0) {
                    // Kein Status vorhanden aber Barren da - neuen Status erstellen
                    StockyardStatusDTO statusDTO = new StockyardStatusDTO();
                    statusDTO.setIngotsCount(actualCount);
                    dto.setStatus(statusDTO);
                }

                return dto;
            })
            .filter(dto -> {
                // 1. Prüfen ob Einlagern erlaubt ist
                if (!dto.isToStockAllowed()) {
                    log.info("PRÜFE {}: EINLAGERN NICHT ERLAUBT -> ABGELEHNT", dto.getYardNumber());
                    return false;
                }

                // 2. Kapazitätsprüfung: Aktuelle Barren + offene Transporte
                int currentCount = actualIngotCounts.getOrDefault(dto.getId(), 0);
                int pendingTransports = pendingTransportsMap.getOrDefault(dto.getId(), 0);
                int totalExpected = currentCount + pendingTransports;
                int maxIngots = dto.getMaxIngots();

                log.info("PRÜFE {}: einlagern=JA, aktuell={}, pending={}, gesamt={}, max={} -> {}",
                    dto.getYardNumber(), currentCount, pendingTransports, totalExpected, maxIngots,
                    totalExpected >= maxIngots ? "VOLL" : "OK");

                if (totalExpected >= maxIngots) {
                    return false;
                }
                return true;
            })
            .collect(Collectors.toList());

        log.info("### ERGEBNIS: {} verfuegbare Plaetze ###", result.size());
        log.info("### Erste 10: {} ###", result.stream().limit(10).map(StockyardDTO::getYardNumber).collect(Collectors.joining(", ")));
        log.info("######################################################");
        return result;
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

        // Behalte den Platz mit HÖHERER X-Koordinate (visuell links im Grid)
        // Das Grid zeigt X=17 links und X=1 rechts
        // Der LONG-Platz spannt sich im CSS nach rechts (zu niedrigeren X-Werten)
        Stockyard keep = yard1.getXCoordinate() > yard2.getXCoordinate() ? yard1 : yard2;
        Stockyard delete = yard1.getXCoordinate() > yard2.getXCoordinate() ? yard2 : yard1;

        // Behaltenen Platz zu LONG umwandeln
        keep.markNotNew();
        keep.setUsage(StockyardUsage.LONG);
        keep.setLength(LONG_LENGTH);
        keep.setMaxIngots(LONG_MAX_INGOTS);

        // BOTTOM_CENTER_X neu berechnen (Mittelpunkt der beiden)
        int newCenterX = (keep.getXPosition() + delete.getXPosition()) / 2;
        keep.setXPosition(newCenterX);

        // Behaltenen Platz speichern
        keep = stockyardRepository.save(keep);

        // Anderen Platz löschen (samt Status falls vorhanden)
        statusRepository.findByStockyardId(delete.getId()).ifPresent(statusRepository::delete);
        stockyardRepository.deleteById(delete.getId());

        log.info("Stockyards merged: {} + {} -> {} (Lang)",
            stockyardId1, stockyardId2, keep.getYardNumber());

        return toDTO(keep);
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
        // Erster Barren in Warteschlange = niedrigste Position (ASC) = nächster zu verarbeiten
        // WICHTIG: Nur Barren mit ABGESCHLOSSENEN Transportaufträgen ausschließen
        // (COMPLETED='C' wird nicht angezeigt, aktive Transporte werden weiterhin angezeigt)
        if (status.getIngotsCount() > 0) {
            try {
                String ingotNo = jdbcTemplate.queryForObject(
                    "SELECT INGOT_NO FROM TD_INGOT i WHERE i.STOCKYARD_ID = ? " +
                    "AND NOT EXISTS (" +
                    "  SELECT 1 FROM TD_TRANSPORTORDER t " +
                    "  WHERE t.INGOT_ID = i.ID " +
                    "  AND t.FROM_YARD_ID = i.STOCKYARD_ID " +
                    "  AND t.STATUS = 'C'" +
                    ") " +
                    "ORDER BY i.PILE_POSITION ASC FETCH FIRST 1 ROWS ONLY",
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
