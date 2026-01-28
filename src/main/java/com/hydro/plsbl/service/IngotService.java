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
     * Findet verfügbare Barren für einen Abruf (Lieferung)
     * Filter:
     * - Barren mit passendem Produkt
     * - Auf Lager (STOCKYARD_ID != null)
     * - Nicht auf externem Lagerplatz (YARD_TYPE != 'E')
     * - Auslagern erlaubt (FROM_STOCK_ALLOWED = 1)
     * - Nicht Schrott
     * - Nicht mit Korrektur (REVISED = 0 oder NULL)
     */
    public List<IngotDTO> findAvailableForDelivery(Long productId) {
        log.info("Finding available ingots for delivery, productId={}", productId);

        // Diagnose: Prüfe was rausgefiltert wird
        try {
            Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM TD_INGOT WHERE PRODUCT_ID = ? AND STOCKYARD_ID IS NOT NULL",
                Integer.class, productId);
            Integer notScrap = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM TD_INGOT WHERE PRODUCT_ID = ? AND STOCKYARD_ID IS NOT NULL AND (SCRAP IS NULL OR SCRAP = 0)",
                Integer.class, productId);
            Integer fromStockAllowed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM TD_INGOT i JOIN MD_STOCKYARD s ON i.STOCKYARD_ID = s.ID " +
                "WHERE i.PRODUCT_ID = ? AND s.FROM_STOCK_ALLOWED = 1",
                Integer.class, productId);
            log.info("Diagnose für PRODUCT_ID={}: Gesamt={}, NichtSchrott={}, Auslagern-erlaubt={}",
                productId, total, notScrap, fromStockAllowed);
        } catch (Exception e) {
            log.warn("Diagnose fehlgeschlagen: {}", e.getMessage());
        }

        // Filter: Extern ausschliessen, Auslagern muss erlaubt sein, kein Schrott
        String sql = """
            SELECT i.*, s.YARD_NO as STOCKYARD_NO, p.PRODUCT_NO
            FROM TD_INGOT i
            JOIN MD_STOCKYARD s ON i.STOCKYARD_ID = s.ID
            LEFT JOIN MD_PRODUCT p ON i.PRODUCT_ID = p.ID
            WHERE i.PRODUCT_ID = ?
              AND i.STOCKYARD_ID IS NOT NULL
              AND (i.SCRAP IS NULL OR i.SCRAP = 0)
              AND s.YARD_TYPE != 'E'
              AND s.FROM_STOCK_ALLOWED = 1
            ORDER BY s.YARD_NO, i.PILE_POSITION DESC
            """;
        // Filter fuer Korrektur werden im Java-Code geprueft und geloggt

        List<IngotDTO> allIngots = jdbcTemplate.query(sql, (rs, rowNum) -> {
            IngotDTO dto = new IngotDTO();
            dto.setId(rs.getLong("ID"));
            dto.setIngotNo(rs.getString("INGOT_NO"));
            dto.setProductId(rs.getObject("PRODUCT_ID") != null ? rs.getLong("PRODUCT_ID") : null);
            dto.setProductSuffix(rs.getString("PRODUCT_SUFFIX"));
            dto.setStockyardId(rs.getObject("STOCKYARD_ID") != null ? rs.getLong("STOCKYARD_ID") : null);
            dto.setPilePosition(rs.getObject("PILE_POSITION") != null ? rs.getInt("PILE_POSITION") : null);
            dto.setWeight(rs.getObject("WEIGHT") != null ? rs.getInt("WEIGHT") : null);
            dto.setLength(rs.getObject("LENGTH") != null ? rs.getInt("LENGTH") : null);
            dto.setWidth(rs.getObject("WIDTH") != null ? rs.getInt("WIDTH") : null);
            dto.setThickness(rs.getObject("THICKNESS") != null ? rs.getInt("THICKNESS") : null);
            dto.setStockyardNo(rs.getString("STOCKYARD_NO"));
            dto.setProductNo(rs.getString("PRODUCT_NO"));

            Integer scrapInt = rs.getObject("SCRAP", Integer.class);
            dto.setScrap(scrapInt != null && scrapInt == 1);

            Integer revisedInt = rs.getObject("REVISED", Integer.class);
            dto.setRevised(revisedInt != null && revisedInt == 1);

            if (rs.getTimestamp("RELEASED_SINCE") != null) {
                dto.setReleasedSince(rs.getTimestamp("RELEASED_SINCE").toLocalDateTime());
            }

            return dto;
        }, productId);

        log.info("Abfrage für PRODUCT_ID={}: {} Barren gefunden (vor Filter)", productId, allIngots.size());

        // Filtern: Nur nicht-korrigierte Barren (RELEASED_SINCE Filter entfernt)
        List<IngotDTO> filtered = allIngots.stream()
            .filter(dto -> {
                boolean revised = dto.getRevised() != null && dto.getRevised();
                if (revised) {
                    log.debug("Barren {} rausgefiltert: korrigiert", dto.getIngotNo());
                }
                return !revised;
            })
            .collect(java.util.stream.Collectors.toList());

        log.info("Nach Filter: {} Barren verfügbar (nicht korrigiert)", filtered.size());

        return filtered;
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
     * Findet alle Barren die im Lager sind und vom Kran geliefert werden koennen.
     * Filter:
     * - YARD_TYPE != 'E' (externe Plaetze nur per Stapler erreichbar)
     * - FROM_STOCK_ALLOWED = 1 (Auslagern muss erlaubt sein)
     * - SCRAP = 0 (kein Schrott)
     */
    public List<IngotDTO> findAllInStock() {
        log.info("=== findAllInStock START ===");

        // Diagnostik: Wie viele Barren mit welchen Filtern?
        try {
            Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM TD_INGOT WHERE STOCKYARD_ID IS NOT NULL", Integer.class);
            Integer notScrap = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM TD_INGOT WHERE STOCKYARD_ID IS NOT NULL AND (SCRAP IS NULL OR SCRAP = 0)", Integer.class);
            Integer craneAccessible = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM TD_INGOT i JOIN MD_STOCKYARD s ON i.STOCKYARD_ID = s.ID WHERE s.YARD_TYPE != 'E'", Integer.class);
            Integer fromStockAllowed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM TD_INGOT i JOIN MD_STOCKYARD s ON i.STOCKYARD_ID = s.ID WHERE s.FROM_STOCK_ALLOWED = 1", Integer.class);
            Integer deliverable = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM TD_INGOT i JOIN MD_STOCKYARD s ON i.STOCKYARD_ID = s.ID " +
                "WHERE s.YARD_TYPE != 'E' AND s.FROM_STOCK_ALLOWED = 1 AND (i.SCRAP IS NULL OR i.SCRAP = 0)", Integer.class);
            log.info("Diagnose: Gesamt={}, NichtSchrott={}, Kran-erreichbar={}, Auslagern-erlaubt={}, Lieferbar={}",
                total, notScrap, craneAccessible, fromStockAllowed, deliverable);
        } catch (Exception e) {
            log.warn("Diagnose fehlgeschlagen: {}", e.getMessage());
        }

        // Query mit allen Filtern:
        // - Nicht extern (YARD_TYPE != 'E')
        // - Auslagern erlaubt (FROM_STOCK_ALLOWED = 1)
        // - Kein Schrott
        String sql = """
            SELECT i.*, s.YARD_NO as STOCKYARD_NO, p.PRODUCT_NO as PRODUCT_NUMBER
            FROM TD_INGOT i
            JOIN MD_STOCKYARD s ON i.STOCKYARD_ID = s.ID
            LEFT JOIN MD_PRODUCT p ON i.PRODUCT_ID = p.ID
            WHERE i.STOCKYARD_ID IS NOT NULL
              AND (i.SCRAP IS NULL OR i.SCRAP = 0)
              AND s.YARD_TYPE != 'E'
              AND s.FROM_STOCK_ALLOWED = 1
            ORDER BY s.YARD_NO, i.PILE_POSITION DESC
            """;

        List<IngotDTO> result = jdbcTemplate.query(sql, (rs, rowNum) -> {
            IngotDTO dto = new IngotDTO();
            dto.setId(rs.getLong("ID"));
            dto.setIngotNo(rs.getString("INGOT_NO"));
            dto.setProductId(rs.getObject("PRODUCT_ID") != null ? rs.getLong("PRODUCT_ID") : null);
            dto.setProductNo(rs.getString("PRODUCT_NUMBER"));
            dto.setStockyardId(rs.getObject("STOCKYARD_ID") != null ? rs.getLong("STOCKYARD_ID") : null);
            dto.setStockyardNo(rs.getString("STOCKYARD_NO"));
            dto.setPilePosition(rs.getObject("PILE_POSITION") != null ? rs.getInt("PILE_POSITION") : null);
            dto.setWeight(rs.getObject("WEIGHT") != null ? rs.getInt("WEIGHT") : null);
            dto.setLength(rs.getObject("LENGTH") != null ? rs.getInt("LENGTH") : null);
            dto.setWidth(rs.getObject("WIDTH") != null ? rs.getInt("WIDTH") : null);
            dto.setThickness(rs.getObject("THICKNESS") != null ? rs.getInt("THICKNESS") : null);
            dto.setHeadSawn(rs.getObject("HEAD_SAWN") != null ? rs.getBoolean("HEAD_SAWN") : null);
            dto.setFootSawn(rs.getObject("FOOT_SAWN") != null ? rs.getBoolean("FOOT_SAWN") : null);
            dto.setScrap(rs.getObject("SCRAP") != null ? rs.getBoolean("SCRAP") : null);
            dto.setRevised(rs.getObject("REVISED") != null ? rs.getBoolean("REVISED") : null);
            return dto;
        });

        log.info("=== findAllInStock ENDE: {} Barren gefunden ===", result.size());
        return result;
    }

    /**
     * Findet alle Barren auf Lagerplätzen eines bestimmten Typs
     * @param stockyardType Der Lagerplatz-Typ (z.B. 'L' für Loading, 'E' für External)
     */
    public List<IngotDTO> findByStockyardType(String stockyardType) {
        log.debug("Loading ingots on stockyards of type {}", stockyardType);

        String sql = "SELECT i.* FROM TD_INGOT i " +
                     "JOIN MD_STOCKYARD s ON i.STOCKYARD_ID = s.ID " +
                     "WHERE s.YARD_TYPE = ? " +
                     "ORDER BY s.YARD_NO, i.PILE_POSITION DESC";

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            IngotDTO dto = new IngotDTO();
            dto.setId(rs.getLong("ID"));
            dto.setIngotNo(rs.getString("INGOT_NO"));
            dto.setProductId(rs.getObject("PRODUCT_ID") != null ? rs.getLong("PRODUCT_ID") : null);
            dto.setProductSuffix(rs.getString("PRODUCT_SUFFIX"));
            dto.setStockyardId(rs.getObject("STOCKYARD_ID") != null ? rs.getLong("STOCKYARD_ID") : null);
            dto.setPilePosition(rs.getObject("PILE_POSITION") != null ? rs.getInt("PILE_POSITION") : null);
            dto.setWeight(rs.getObject("WEIGHT") != null ? rs.getInt("WEIGHT") : null);
            dto.setLength(rs.getObject("LENGTH") != null ? rs.getInt("LENGTH") : null);
            dto.setWidth(rs.getObject("WIDTH") != null ? rs.getInt("WIDTH") : null);
            dto.setThickness(rs.getObject("THICKNESS") != null ? rs.getInt("THICKNESS") : null);
            dto.setHeadSawn(rs.getObject("HEAD_SAWN") != null ? rs.getBoolean("HEAD_SAWN") : null);
            dto.setFootSawn(rs.getObject("FOOT_SAWN") != null ? rs.getBoolean("FOOT_SAWN") : null);
            dto.setScrap(rs.getObject("SCRAP") != null ? rs.getBoolean("SCRAP") : null);
            dto.setRevised(rs.getObject("REVISED") != null ? rs.getBoolean("REVISED") : null);
            dto.setRotated(rs.getObject("ROTATED") != null ? rs.getBoolean("ROTATED") : null);

            // Produkt-Nummer und Lagerplatz-Nummer nachladen
            if (dto.getProductId() != null) {
                try {
                    dto.setProductNo(jdbcTemplate.queryForObject(
                        "SELECT PRODUCT_NO FROM MD_PRODUCT WHERE ID = ?", String.class, dto.getProductId()));
                } catch (Exception e) { /* ignorieren */ }
            }
            if (dto.getStockyardId() != null) {
                try {
                    dto.setStockyardNo(jdbcTemplate.queryForObject(
                        "SELECT YARD_NO FROM MD_STOCKYARD WHERE ID = ?", String.class, dto.getStockyardId()));
                } catch (Exception e) { /* ignorieren */ }
            }

            return dto;
        }, stockyardType);
    }

    /**
     * Findet alle freigegebenen Barren (releasedSince != null)
     */
    public List<IngotDTO> findReleased() {
        log.debug("Loading released ingots");

        String sql = "SELECT * FROM TD_INGOT WHERE RELEASED_SINCE IS NOT NULL ORDER BY RELEASED_SINCE DESC";

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            IngotDTO dto = new IngotDTO();
            dto.setId(rs.getLong("ID"));
            dto.setIngotNo(rs.getString("INGOT_NO"));
            dto.setProductId(rs.getObject("PRODUCT_ID") != null ? rs.getLong("PRODUCT_ID") : null);
            dto.setStockyardId(rs.getObject("STOCKYARD_ID") != null ? rs.getLong("STOCKYARD_ID") : null);
            dto.setPilePosition(rs.getObject("PILE_POSITION") != null ? rs.getInt("PILE_POSITION") : null);
            dto.setWeight(rs.getObject("WEIGHT") != null ? rs.getInt("WEIGHT") : null);
            dto.setLength(rs.getObject("LENGTH") != null ? rs.getInt("LENGTH") : null);
            dto.setWidth(rs.getObject("WIDTH") != null ? rs.getInt("WIDTH") : null);
            dto.setThickness(rs.getObject("THICKNESS") != null ? rs.getInt("THICKNESS") : null);

            if (rs.getTimestamp("RELEASED_SINCE") != null) {
                dto.setReleasedSince(rs.getTimestamp("RELEASED_SINCE").toLocalDateTime());
            }

            // Referenzen nachladen
            if (dto.getProductId() != null) {
                try {
                    dto.setProductNo(jdbcTemplate.queryForObject(
                        "SELECT PRODUCT_NO FROM MD_PRODUCT WHERE ID = ?", String.class, dto.getProductId()));
                } catch (Exception e) { /* ignorieren */ }
            }
            if (dto.getStockyardId() != null) {
                try {
                    dto.setStockyardNo(jdbcTemplate.queryForObject(
                        "SELECT YARD_NO FROM MD_STOCKYARD WHERE ID = ?", String.class, dto.getStockyardId()));
                } catch (Exception e) { /* ignorieren */ }
            }

            return dto;
        });
    }

    /**
     * Gibt einen Barren frei (setzt releasedSince)
     */
    @Transactional
    public void release(Long ingotId) {
        log.info("Releasing ingot {}", ingotId);
        jdbcTemplate.update(
            "UPDATE TD_INGOT SET RELEASED_SINCE = CURRENT_TIMESTAMP WHERE ID = ?", ingotId);
    }

    /**
     * Hebt die Freigabe eines Barrens auf
     */
    @Transactional
    public void unreleaseIngot(Long ingotId) {
        log.info("Unreleasing ingot {}", ingotId);
        jdbcTemplate.update(
            "UPDATE TD_INGOT SET RELEASED_SINCE = NULL WHERE ID = ?", ingotId);
    }

    /**
     * Markiert einen Barren als ausgeliefert (entfernt vom Lagerplatz)
     */
    @Transactional
    public void markAsShipped(Long ingotId) {
        log.info("Marking ingot {} as shipped", ingotId);

        // Stockyard-ID holen für Status-Update
        Long stockyardId = jdbcTemplate.queryForObject(
            "SELECT STOCKYARD_ID FROM TD_INGOT WHERE ID = ?", Long.class, ingotId);

        // Barren vom Lagerplatz entfernen
        jdbcTemplate.update(
            "UPDATE TD_INGOT SET STOCKYARD_ID = NULL, PILE_POSITION = NULL, SERIAL = SERIAL + 1 WHERE ID = ?", ingotId);

        // Stapelpositionen der verbleibenden Barren neu berechnen
        if (stockyardId != null) {
            recalculatePilePositions(stockyardId);

            // StockyardStatus aktualisieren
            stockyardStatusRepository.findByStockyardId(stockyardId).ifPresent(status -> {
                int newCount = Math.max(0, status.getIngotsCount() - 1);
                if (newCount == 0) {
                    stockyardStatusRepository.delete(status);
                } else {
                    jdbcTemplate.update(
                        "UPDATE TD_STOCKYARDSTATUS SET INGOTS_COUNT = ?, SERIAL = SERIAL + 1 WHERE ID = ?",
                        newCount, status.getId());
                }
            });
        }
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

        boolean isNewIngot = (dto.getId() == null);

        if (isNewIngot) {
            // CREATE - Direktes SQL für Oracle-Kompatibilität (TABLESERIAL, etc.)
            Long nextId = getNextId();
            dto.setId(nextId);

            jdbcTemplate.update(
                "INSERT INTO TD_INGOT (ID, SERIAL, TABLESERIAL, INGOT_NO, PRODUCT_ID, PRODUCT_SUFFIX, " +
                "STOCKYARD_ID, PILE_POSITION, WEIGHT, LENGTH, WIDTH, THICKNESS, " +
                "HEAD_SAWN, FOOT_SAWN, SCRAP, REVISED, ROTATED, PICKUPS, MOVEMENTS, TRANSPORTS, LOADINGS, IN_STOCK_SINCE, RELEASED_SINCE) " +
                "VALUES (?, 1, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, 0, 0, ?, ?)",
                nextId,
                dto.getIngotNo(),
                dto.getProductId(),
                dto.getProductSuffix(),
                dto.getStockyardId(),
                dto.getPilePosition(),
                dto.getWeight(),
                dto.getLength(),
                dto.getWidth(),
                dto.getThickness(),
                dto.getHeadSawn() != null && dto.getHeadSawn() ? 1 : 0,
                dto.getFootSawn() != null && dto.getFootSawn() ? 1 : 0,
                dto.getScrap() != null && dto.getScrap() ? 1 : 0,
                dto.getRevised() != null && dto.getRevised() ? 1 : 0,
                dto.getRotated() != null && dto.getRotated() ? 1 : 0,
                dto.getInStockSince(),
                dto.getReleasedSince()
            );

            log.info("Ingot created via SQL: ID={}, ingotNo={}, stockyardId={}",
                nextId, dto.getIngotNo(), dto.getStockyardId());

            // StockyardStatus aktualisieren
            if (dto.getStockyardId() != null) {
                log.info("Updating StockyardStatus for new ingot on stockyard {}", dto.getStockyardId());
                updateStockyardStatusForNewIngot(dto.getStockyardId(), dto.getProductId());
            }

            return dto;
        } else {
            // UPDATE - Repository verwenden
            Ingot entity = ingotRepository.findById(dto.getId())
                .orElseThrow(() -> new IllegalArgumentException("Barren nicht gefunden: " + dto.getId()));
            entity.markNotNew();

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
            log.info("Ingot updated: ID={}, ingotNo={}, stockyardId={}",
                saved.getId(), saved.getIngotNo(), saved.getStockyardId());

            return toDTO(saved);
        }
    }

    /**
     * Aktualisiert StockyardStatus wenn ein neuer Barren erstellt wird
     */
    private void updateStockyardStatusForNewIngot(Long stockyardId, Long productId) {
        log.info("updateStockyardStatusForNewIngot called: stockyardId={}, productId={}", stockyardId, productId);
        Optional<StockyardStatus> existingStatus = stockyardStatusRepository.findByStockyardId(stockyardId);
        if (existingStatus.isPresent()) {
            // Anzahl erhöhen
            StockyardStatus status = existingStatus.get();
            int newCount = status.getIngotsCount() + 1;
            jdbcTemplate.update(
                "UPDATE TD_STOCKYARDSTATUS SET INGOTS_COUNT = ?, SERIAL = SERIAL + 1 WHERE ID = ?",
                newCount, status.getId());
            log.info("StockyardStatus UPDATED for stockyard {}: count={}", stockyardId, newCount);
        } else {
            // Neuen Status erstellen
            Long newId = getNextStatusId();
            String yardUsage = getYardUsageForStockyard(stockyardId);
            jdbcTemplate.update(
                "INSERT INTO TD_STOCKYARDSTATUS (ID, STOCKYARD_ID, PRODUCT_ID, INGOTS_COUNT, YARD_USAGE, PILE_HEIGHT, SERIAL, TABLESERIAL, SCRAP_ON_TOP, REVISED_ON_TOP) VALUES (?, ?, ?, 1, ?, 1, 1, 1, 0, 0)",
                newId, stockyardId, productId, yardUsage);
            log.info("StockyardStatus CREATED for stockyard {} with ID={}, product={}, yardUsage={}", stockyardId, newId, productId, yardUsage);
        }
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
     * Löscht einen Barren und aktualisiert den StockyardStatus
     */
    @Transactional
    public void delete(Long id) {
        log.info("Deleting ingot: {}", id);

        // Zuerst Stockyard-ID holen für Status-Update
        Long stockyardId = null;
        try {
            stockyardId = jdbcTemplate.queryForObject(
                "SELECT STOCKYARD_ID FROM TD_INGOT WHERE ID = ?", Long.class, id);
        } catch (Exception e) {
            log.warn("Could not get stockyard for ingot {}: {}", id, e.getMessage());
        }

        // Barren löschen
        ingotRepository.deleteById(id);
        log.info("Ingot deleted: {}", id);

        // StockyardStatus aktualisieren
        if (stockyardId != null) {
            updateStockyardStatusAfterDelete(stockyardId);
        }
    }

    /**
     * Aktualisiert StockyardStatus nachdem ein Barren gelöscht wurde
     */
    private void updateStockyardStatusAfterDelete(Long stockyardId) {
        log.info("Updating StockyardStatus after delete for stockyard {}", stockyardId);

        stockyardStatusRepository.findByStockyardId(stockyardId).ifPresent(status -> {
            // Tatsächliche Anzahl aus DB zählen
            int actualCount = countByStockyardId(stockyardId);
            log.info("Actual ingot count on stockyard {}: {}", stockyardId, actualCount);

            if (actualCount == 0) {
                // Status löschen wenn leer
                stockyardStatusRepository.delete(status);
                log.info("StockyardStatus DELETED for stockyard {} (now empty)", stockyardId);
            } else {
                // Anzahl aktualisieren
                jdbcTemplate.update(
                    "UPDATE TD_STOCKYARDSTATUS SET INGOTS_COUNT = ?, SERIAL = SERIAL + 1 WHERE ID = ?",
                    actualCount, status.getId());
                log.info("StockyardStatus UPDATED for stockyard {}: count={}", stockyardId, actualCount);
            }
        });
    }

    /**
     * Lagert einen Barren auf einen anderen Lagerplatz um.
     * Bei destinationStockyardId = null wird der Barren vom Lager entfernt (z.B. auf LKW geladen).
     */
    @Transactional
    public void relocate(Long ingotId, Long destinationStockyardId) {
        log.info("Relocating ingot {} to stockyard {}", ingotId, destinationStockyardId);

        Ingot ingot = ingotRepository.findById(ingotId)
            .orElseThrow(() -> new IllegalArgumentException("Barren nicht gefunden: " + ingotId));

        Long sourceStockyardId = ingot.getStockyardId();
        Integer oldPilePosition = ingot.getPilePosition();
        Long productId = ingot.getProductId();

        log.info("  Quelle: Lagerplatz={}, Position={}", sourceStockyardId, oldPilePosition);

        ingot.markNotNew();
        ingot.setStockyardId(destinationStockyardId);

        if (destinationStockyardId == null) {
            // Barren wird vom Lager entfernt (z.B. auf LKW)
            ingot.setPilePosition(null);
            log.info("  Barren {} wird aus Lager entfernt (STOCKYARD_ID=NULL, PILE_POSITION=NULL)",
                ingot.getIngotNo());
        } else {
            // Auf neuen Lagerplatz - oberste Position
            int newPosition = countByStockyardId(destinationStockyardId) + 1;
            ingot.setPilePosition(newPosition);
            log.info("  Barren {} wird auf Lagerplatz {} Position {} gelegt",
                ingot.getIngotNo(), destinationStockyardId, newPosition);
        }

        ingotRepository.save(ingot);

        // Stapelpositionen der verbleibenden Barren auf dem Quell-Lagerplatz neu berechnen
        if (sourceStockyardId != null) {
            recalculatePilePositions(sourceStockyardId);
        }

        // StockyardStatus aktualisieren
        updateStockyardStatusAfterRelocate(sourceStockyardId, destinationStockyardId, productId);

        log.info("Ingot {} relocated: {} -> {} (position: {} -> {})",
            ingotId, sourceStockyardId, destinationStockyardId,
            oldPilePosition, ingot.getPilePosition());
    }

    /**
     * Berechnet die Stapelpositionen aller Barren auf einem Lagerplatz neu.
     * Wird aufgerufen nachdem ein Barren entfernt wurde um Lücken zu schließen.
     * Aktualisiert auch SERIAL für Versionskontrolle.
     */
    private void recalculatePilePositions(Long stockyardId) {
        log.info("Recalculating pile positions for stockyard {}", stockyardId);

        // Alle Barren auf dem Platz holen, sortiert nach aktueller Position
        List<Long> ingotIds = jdbcTemplate.queryForList(
            "SELECT ID FROM TD_INGOT WHERE STOCKYARD_ID = ? ORDER BY PILE_POSITION ASC NULLS LAST",
            Long.class, stockyardId);

        // Neue fortlaufende Positionen vergeben (1, 2, 3, ...) und SERIAL erhöhen
        for (int i = 0; i < ingotIds.size(); i++) {
            int newPosition = i + 1;
            jdbcTemplate.update(
                "UPDATE TD_INGOT SET PILE_POSITION = ?, SERIAL = SERIAL + 1 WHERE ID = ?",
                newPosition, ingotIds.get(i));
        }

        log.info("  {} Barren neu nummeriert (1..{})", ingotIds.size(), ingotIds.size());
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

        // 2. Ziel: Anzahl erhöhen oder neuen Status erstellen (nur wenn Ziel vorhanden)
        if (destinationStockyardId == null) {
            log.debug("No destination stockyard - skipping destination status update");
            return;
        }

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
                "INSERT INTO TD_STOCKYARDSTATUS (ID, STOCKYARD_ID, PRODUCT_ID, INGOTS_COUNT, YARD_USAGE, PILE_HEIGHT, SERIAL, TABLESERIAL, SCRAP_ON_TOP, REVISED_ON_TOP) VALUES (?, ?, ?, 1, ?, 1, 1, 1, 0, 0)",
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

    /**
     * Erstellt Test-Barren für die Beladungs-Simulation
     * @return Anzahl der erstellten Barren
     */
    @Transactional
    public int createTestIngots() {
        log.info("=== Erstelle Test-Barren ===");

        try {
            // Prüfen ob bereits Barren im Lager sind
            int existing = findAllInStock().size();
            if (existing >= 10) {
                log.info("Bereits {} Barren im Lager, keine neuen erstellt", existing);
                return existing;
            }

            // Verfügbare Lagerplätze finden (Typ 'L' = Lager)
            List<Long> stockyardIds = jdbcTemplate.queryForList(
                "SELECT ID FROM MD_STOCKYARD WHERE YARD_TYPE = 'L' ORDER BY YARD_NO",
                Long.class);

            if (stockyardIds.isEmpty()) {
                // Fallback: alle Stockyards
                stockyardIds = jdbcTemplate.queryForList(
                    "SELECT ID FROM MD_STOCKYARD ORDER BY ID",
                    Long.class);
            }

            if (stockyardIds.isEmpty()) {
                log.warn("Keine Lagerplätze gefunden!");
                return 0;
            }

            log.info("Gefunden: {} Lagerplätze", stockyardIds.size());

            // Test-Barren erstellen
            int created = 0;
            for (int i = 0; i < Math.min(10, stockyardIds.size()); i++) {
                Long stockyardId = stockyardIds.get(i);
                Long nextId = getNextId();
                String ingotNo = String.format("TEST-%06d", nextId);

                // Zufällige Größen
                int length = 1800 + (i % 3) * 500;  // 1800, 2300, 2800
                int width = 400 + (i % 2) * 100;    // 400, 500
                int thickness = 180 + (i % 2) * 40; // 180, 220
                int weight = 8000 + i * 500;        // 8000 - 12500

                jdbcTemplate.update(
                    "INSERT INTO TD_INGOT (ID, SERIAL, INGOT_NO, STOCKYARD_ID, PILE_POSITION, " +
                    "WEIGHT, LENGTH, WIDTH, THICKNESS, HEAD_SAWN, FOOT_SAWN, SCRAP, REVISED, ROTATED, " +
                    "PICKUPS, MOVEMENTS, TRANSPORTS, LOADINGS, IN_STOCK_SINCE) " +
                    "VALUES (?, 1, ?, ?, ?, ?, ?, ?, ?, 0, 0, 0, 0, 0, 0, 0, 0, 0, CURRENT_TIMESTAMP)",
                    nextId, ingotNo, stockyardId, 1, weight, length, width, thickness
                );

                log.info("Test-Barren erstellt: {} auf Lagerplatz {} ({} kg)", ingotNo, stockyardId, weight);
                created++;
            }

            log.info("=== {} Test-Barren erstellt ===", created);
            return created;

        } catch (Exception e) {
            log.error("Fehler beim Erstellen der Test-Barren: {}", e.getMessage(), e);
            return 0;
        }
    }
}
