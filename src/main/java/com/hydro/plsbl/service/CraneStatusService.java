package com.hydro.plsbl.service;

import com.hydro.plsbl.dto.CraneStatusDTO;
import com.hydro.plsbl.dto.IngotDTO;
import com.hydro.plsbl.entity.transdata.CraneStatus;
import com.hydro.plsbl.repository.CraneStatusRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Service für Kran-Status-Operationen
 */
@Service
@Transactional(readOnly = true)
public class CraneStatusService {

    private static final Logger log = LoggerFactory.getLogger(CraneStatusService.class);

    private final CraneStatusRepository craneStatusRepository;
    private final IngotService ingotService;
    private final JdbcTemplate jdbcTemplate;

    public CraneStatusService(CraneStatusRepository craneStatusRepository,
                              IngotService ingotService,
                              JdbcTemplate jdbcTemplate) {
        this.craneStatusRepository = craneStatusRepository;
        this.ingotService = ingotService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Holt den aktuellen Kran-Status
     */
    public Optional<CraneStatusDTO> getCurrentStatus() {
        log.debug("Loading current crane status");

        return craneStatusRepository.findCurrent()
            .or(() -> craneStatusRepository.findFirst())
            .map(this::toDTO);
    }

    // === Mapping ===

    private CraneStatusDTO toDTO(CraneStatus entity) {
        CraneStatusDTO dto = new CraneStatusDTO();
        dto.setId(entity.getId());
        dto.setXPosition(entity.getXPosition());
        dto.setYPosition(entity.getYPosition());
        dto.setZPosition(entity.getZPosition());
        dto.setCraneMode(entity.getCraneMode());
        dto.setGripperState(entity.getGripperState());
        dto.setJobState(entity.getJobState());
        dto.setDaemonState(entity.getDaemonState());
        dto.setWorkPhase(entity.getWorkPhase());
        dto.setFromStockyardId(entity.getFromStockyardId());
        dto.setToStockyardId(entity.getToStockyardId());
        dto.setIncident(entity.getIncident());
        dto.setIncidentText(entity.getIncidentText());
        dto.setDoorsOpen(entity.getDoorsOpen());
        dto.setGatesOpen(entity.getGatesOpen());

        // Stockyard-Nummern laden
        if (entity.getFromStockyardId() != null) {
            dto.setFromStockyardNo(loadStockyardNo(entity.getFromStockyardId()));
        }
        if (entity.getToStockyardId() != null) {
            dto.setToStockyardNo(loadStockyardNo(entity.getToStockyardId()));
        }

        // Barren im Greifer laden (wenn Greifer geschlossen und Auftrag vorhanden)
        if (dto.isGripperHolding() && entity.getFromStockyardId() != null) {
            loadIngotInGripper(dto, entity.getFromStockyardId());
        }

        return dto;
    }

    /**
     * Lädt den Barren im Greifer (oberster Barren vom Quell-Lagerplatz)
     */
    private void loadIngotInGripper(CraneStatusDTO dto, Long fromStockyardId) {
        try {
            Optional<IngotDTO> topIngot = ingotService.findTopIngotOnStockyard(fromStockyardId);
            if (topIngot.isPresent()) {
                IngotDTO ingot = topIngot.get();
                dto.setIngotNo(ingot.getIngotNo());
                dto.setIngotProductNo(ingot.getProductNo());
                dto.setIngotLength(ingot.getLength());
                dto.setIngotWidth(ingot.getWidth());
                log.debug("Loaded ingot in gripper: {} ({})", ingot.getIngotNo(), ingot.getProductNo());
            }
        } catch (Exception e) {
            log.debug("Could not load ingot in gripper: {}", e.getMessage());
        }
    }

    private String loadStockyardNo(Long stockyardId) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT YARD_NO FROM MD_STOCKYARD WHERE ID = ?",
                String.class,
                stockyardId
            );
        } catch (Exception e) {
            log.debug("Stockyard not found for id {}", stockyardId);
            return null;
        }
    }
}
