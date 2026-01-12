package com.hydro.plsbl.service;

import com.hydro.plsbl.dto.CraneCommandDTO;
import com.hydro.plsbl.entity.transdata.CraneCommand;
import com.hydro.plsbl.repository.CraneCommandRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service für Kran-Kommando-Operationen
 */
@Service
@Transactional(readOnly = true)
public class CraneCommandService {

    private static final Logger log = LoggerFactory.getLogger(CraneCommandService.class);

    private final CraneCommandRepository commandRepository;
    private final JdbcTemplate jdbcTemplate;

    public CraneCommandService(CraneCommandRepository commandRepository, JdbcTemplate jdbcTemplate) {
        this.commandRepository = commandRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Findet alle Kommandos
     */
    public List<CraneCommandDTO> findAll() {
        log.debug("Loading all crane commands");
        return commandRepository.findAllOrdered().stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    /**
     * Findet die letzten N Kommandos
     */
    public List<CraneCommandDTO> findLatest(int limit) {
        log.debug("Loading latest {} crane commands", limit);
        return commandRepository.findLatest(limit).stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    /**
     * Findet Kommandos nach Typ
     */
    public List<CraneCommandDTO> findByCmdType(String cmdType) {
        return commandRepository.findByCmdType(cmdType).stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    /**
     * Findet Kommandos nach Kran-Modus
     */
    public List<CraneCommandDTO> findByCraneMode(String craneMode) {
        return commandRepository.findByCraneMode(craneMode).stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    /**
     * Findet ein Kommando nach ID
     */
    public Optional<CraneCommandDTO> findById(Long id) {
        return commandRepository.findById(id).map(this::toDTO);
    }

    /**
     * Zählt alle Kommandos
     */
    public int countAll() {
        return commandRepository.countAll();
    }

    /**
     * Speichert ein neues oder aktualisiert ein bestehendes Kommando
     */
    @Transactional
    public CraneCommandDTO save(CraneCommandDTO dto) {
        log.debug("Saving crane command: {}", dto.getId());

        CraneCommand entity;
        if (dto.getId() != null) {
            // Update - mark as not new so Spring Data JDBC does UPDATE instead of INSERT
            entity = commandRepository.findById(dto.getId())
                .orElseThrow(() -> new IllegalArgumentException("Kommando nicht gefunden: " + dto.getId()));
            entity.markNotNew();
        } else {
            // Create - ID und TableSerial setzen (NOT NULL in Oracle)
            entity = new CraneCommand();
            Long nextId = getNextId();
            entity.setId(nextId);
            Long nextSerial = getNextTableSerial();
            entity.setTableSerial(nextSerial);
        }

        // Werte übernehmen
        entity.setCmdType(dto.getCmdType());
        entity.setCraneMode(dto.getCraneMode());
        entity.setRotate(dto.getRotate());
        entity.setIngotId(dto.getIngotId());
        entity.setFromYardId(dto.getFromYardId());
        entity.setToYardId(dto.getToYardId());

        CraneCommand saved = commandRepository.save(entity);
        log.info("Crane command saved with ID: {}", saved.getId());

        return toDTO(saved);
    }

    private Long getNextId() {
        try {
            Long maxId = jdbcTemplate.queryForObject(
                "SELECT NVL(MAX(ID), 0) + 1 FROM TD_CRANECOMMAND", Long.class);
            return maxId != null ? maxId : 1L;
        } catch (Exception e) {
            log.warn("Could not get next ID, using timestamp", e);
            return System.currentTimeMillis();
        }
    }

    private Long getNextTableSerial() {
        try {
            Long maxSerial = jdbcTemplate.queryForObject(
                "SELECT NVL(MAX(TABLESERIAL), 0) + 1 FROM TD_CRANECOMMAND", Long.class);
            return maxSerial != null ? maxSerial : 1L;
        } catch (Exception e) {
            log.warn("Could not get next table serial, using timestamp", e);
            return System.currentTimeMillis();
        }
    }

    /**
     * Löscht ein Kommando
     */
    @Transactional
    public void delete(Long id) {
        log.debug("Deleting crane command: {}", id);
        commandRepository.deleteById(id);
        log.info("Crane command deleted: {}", id);
    }

    // === Mapping ===

    private CraneCommandDTO toDTO(CraneCommand entity) {
        CraneCommandDTO dto = new CraneCommandDTO();
        dto.setId(entity.getId());
        dto.setTableSerial(entity.getTableSerial());
        dto.setCmdType(entity.getCmdType());
        dto.setRotate(entity.getRotate());
        dto.setCraneMode(entity.getCraneMode());
        dto.setIngotId(entity.getIngotId());
        dto.setFromYardId(entity.getFromYardId());
        dto.setToYardId(entity.getToYardId());

        // Referenzen auflösen
        if (entity.getFromYardId() != null) {
            dto.setFromYardNo(loadStockyardNo(entity.getFromYardId()));
        }
        if (entity.getToYardId() != null) {
            dto.setToYardNo(loadStockyardNo(entity.getToYardId()));
        }
        if (entity.getIngotId() != null) {
            dto.setIngotNo(loadIngotNo(entity.getIngotId()));
        }

        return dto;
    }

    private String loadStockyardNo(Long id) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT YARD_NO FROM MD_STOCKYARD WHERE ID = ?",
                String.class, id);
        } catch (Exception e) {
            return null;
        }
    }

    private String loadIngotNo(Long id) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT INGOT_NO FROM TD_INGOT WHERE ID = ?",
                String.class, id);
        } catch (Exception e) {
            return null;
        }
    }
}
