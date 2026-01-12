package com.hydro.plsbl.service;

import com.hydro.plsbl.dto.TransportOrderDTO;
import com.hydro.plsbl.entity.transdata.TransportOrder;
import com.hydro.plsbl.repository.TransportOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service für Transportauftrag-Operationen
 */
@Service
@Transactional(readOnly = true)
public class TransportOrderService {

    private static final Logger log = LoggerFactory.getLogger(TransportOrderService.class);

    private final TransportOrderRepository orderRepository;
    private final JdbcTemplate jdbcTemplate;

    public TransportOrderService(TransportOrderRepository orderRepository, JdbcTemplate jdbcTemplate) {
        this.orderRepository = orderRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Findet alle Aufträge
     */
    public List<TransportOrderDTO> findAll() {
        log.debug("Loading all transport orders");
        return orderRepository.findAllOrdered().stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    /**
     * Findet die letzten N Aufträge
     */
    public List<TransportOrderDTO> findLatest(int limit) {
        log.debug("Loading latest {} transport orders", limit);
        return orderRepository.findLatest(limit).stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    /**
     * Findet einen Auftrag nach ID
     */
    public Optional<TransportOrderDTO> findById(Long id) {
        return orderRepository.findById(id).map(this::toDTO);
    }

    /**
     * Findet einen Auftrag nach Transportnummer
     */
    public Optional<TransportOrderDTO> findByTransportNo(String transportNo) {
        return orderRepository.findByTransportNo(transportNo).map(this::toDTO);
    }

    /**
     * Zählt alle Aufträge
     */
    public int countAll() {
        return orderRepository.countAll();
    }

    /**
     * Speichert einen neuen oder aktualisiert einen bestehenden Auftrag
     */
    @Transactional
    public TransportOrderDTO save(TransportOrderDTO dto) {
        log.debug("Saving transport order: {}", dto.getId());

        TransportOrder entity;
        if (dto.getId() != null) {
            // Update - mark as not new so Spring Data JDBC does UPDATE instead of INSERT
            entity = orderRepository.findById(dto.getId())
                .orElseThrow(() -> new IllegalArgumentException("Auftrag nicht gefunden: " + dto.getId()));
            entity.markNotNew();
        } else {
            // Create - ID, TableSerial und Timestamps setzen (NOT NULL in Oracle)
            entity = new TransportOrder();
            Long nextId = getNextId();
            entity.setId(nextId);
            Long nextSerial = getNextTableSerial();
            entity.setTableSerial(nextSerial);
            // Default-Timestamps für NOT NULL Spalten (1970-01-01 = nicht gesetzt)
            LocalDateTime notSet = LocalDateTime.of(1970, 1, 1, 0, 0, 0);
            entity.setPrinted(notSet);
            entity.setDelivered(notSet);
        }

        // Werte übernehmen
        entity.setTransportNo(dto.getTransportNo());
        entity.setNormText(dto.getNormText());
        entity.setIngotId(dto.getIngotId());
        entity.setFromYardId(dto.getFromYardId());
        entity.setFromPilePosition(dto.getFromPilePosition());
        entity.setToYardId(dto.getToYardId());
        entity.setToPilePosition(dto.getToPilePosition());
        entity.setCalloffId(dto.getCalloffId());

        TransportOrder saved = orderRepository.save(entity);
        log.info("Transport order saved with ID: {}", saved.getId());

        return toDTO(saved);
    }

    private Long getNextId() {
        try {
            Long maxId = jdbcTemplate.queryForObject(
                "SELECT NVL(MAX(ID), 0) + 1 FROM TD_TRANSPORTORDER", Long.class);
            return maxId != null ? maxId : 1L;
        } catch (Exception e) {
            log.warn("Could not get next ID, using timestamp", e);
            return System.currentTimeMillis();
        }
    }

    private Long getNextTableSerial() {
        try {
            Long maxSerial = jdbcTemplate.queryForObject(
                "SELECT NVL(MAX(TABLESERIAL), 0) + 1 FROM TD_TRANSPORTORDER", Long.class);
            return maxSerial != null ? maxSerial : 1L;
        } catch (Exception e) {
            log.warn("Could not get next table serial, using timestamp", e);
            return System.currentTimeMillis();
        }
    }

    /**
     * Löscht einen Auftrag
     */
    @Transactional
    public void delete(Long id) {
        log.debug("Deleting transport order: {}", id);
        orderRepository.deleteById(id);
        log.info("Transport order deleted: {}", id);
    }

    // === Mapping ===

    private TransportOrderDTO toDTO(TransportOrder entity) {
        TransportOrderDTO dto = new TransportOrderDTO();
        dto.setId(entity.getId());
        dto.setTableSerial(entity.getTableSerial());
        dto.setTransportNo(entity.getTransportNo());
        dto.setNormText(entity.getNormText());
        dto.setCalloffId(entity.getCalloffId());
        dto.setIngotId(entity.getIngotId());
        dto.setFromYardId(entity.getFromYardId());
        dto.setFromPilePosition(entity.getFromPilePosition());
        dto.setToYardId(entity.getToYardId());
        dto.setToPilePosition(entity.getToPilePosition());

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
