package com.hydro.plsbl.service;

import com.hydro.plsbl.dto.IngotDTO;
import com.hydro.plsbl.dto.SawStatusDTO;
import com.hydro.plsbl.entity.transdata.PlsStatus;
import com.hydro.plsbl.repository.PlsStatusRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Service für Säge-Status-Operationen
 */
@Service
@Transactional(readOnly = true)
public class SawStatusService {

    private static final Logger log = LoggerFactory.getLogger(SawStatusService.class);

    private final PlsStatusRepository plsStatusRepository;
    private final IngotService ingotService;
    private final JdbcTemplate jdbcTemplate;

    public SawStatusService(PlsStatusRepository plsStatusRepository,
                            IngotService ingotService,
                            JdbcTemplate jdbcTemplate) {
        this.plsStatusRepository = plsStatusRepository;
        this.ingotService = ingotService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Holt den aktuellen Säge-Status
     */
    public Optional<SawStatusDTO> getCurrentStatus() {
        log.debug("Loading current saw status");

        return plsStatusRepository.findCurrent()
            .or(() -> plsStatusRepository.findFirst())
            .map(this::toDTO);
    }

    /**
     * Aktualisiert den Einlagerungsmodus
     */
    @Transactional
    public void updatePickupMode(SawStatusDTO.PickupMode mode) {
        plsStatusRepository.findCurrent().ifPresent(status -> {
            status.setPickupMode(mode.name());
            plsStatusRepository.save(status);
            log.info("Pickup mode updated to: {}", mode);
        });
    }

    /**
     * Aktualisiert die Drehen-Einstellung
     */
    @Transactional
    public void updateRotate(boolean rotate) {
        plsStatusRepository.findCurrent().ifPresent(status -> {
            status.setRotate(rotate);
            plsStatusRepository.save(status);
            log.info("Rotate updated to: {}", rotate);
        });
    }

    /**
     * Setzt eine Fehlermeldung im Säge-Status
     * Verwendet REQUIRES_NEW damit der Fehler auch bei Rollback der Haupt-Transaktion gespeichert wird.
     *
     * @param errorType Fehlertyp (z.B. "DUPLIKAT", "LAGERPLATZ")
     * @param errorMessage Die Fehlermeldung
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void setError(String errorType, String errorMessage) {
        plsStatusRepository.findCurrent().ifPresent(status -> {
            status.setErrorType(errorType);
            status.setErrorMessage(errorMessage);
            plsStatusRepository.save(status);
            log.warn("Saw error set: {} - {}", errorType, errorMessage);
        });
    }

    /**
     * Löscht die Fehlermeldung im Säge-Status
     */
    @Transactional
    public void clearError() {
        plsStatusRepository.findCurrent().ifPresent(status -> {
            status.setErrorType(null);
            status.setErrorMessage(null);
            plsStatusRepository.save(status);
            log.info("Saw error cleared");
        });
    }

    // === Mapping ===

    private SawStatusDTO toDTO(PlsStatus entity) {
        SawStatusDTO dto = new SawStatusDTO();

        // Pickup Mode
        String mode = entity.getPickupMode();
        if (mode != null) {
            try {
                dto.setPickupMode(SawStatusDTO.PickupMode.valueOf(mode));
            } catch (IllegalArgumentException e) {
                dto.setPickupMode(SawStatusDTO.PickupMode.NO_PICKUP);
            }
        }

        dto.setRotate(Boolean.TRUE.equals(entity.getRotate()));
        dto.setPickupInProgress(Boolean.TRUE.equals(entity.getPickupInProgress()));
        dto.setPickupNumber(entity.getPickupNumber());
        dto.setReceived(entity.getReceivedTime());

        // Positionen
        dto.setPositionX(entity.getPositionX());
        dto.setPositionY(entity.getPositionY());
        dto.setPositionZ(entity.getPositionZ());
        dto.setComputedX(entity.getComputedX());
        dto.setComputedY(entity.getComputedY());
        dto.setComputedZ(entity.getComputedZ());

        // Fehler
        dto.setErrorType(entity.getErrorType());
        dto.setErrorMessage(entity.getErrorMessage());

        // Bestätigungen
        dto.setReturnConfirmed(Boolean.TRUE.equals(entity.getReturnConfirmed()));
        dto.setRecycleConfirmed(Boolean.TRUE.equals(entity.getRecycleConfirmed()));
        dto.setSawnConfirmed(Boolean.TRUE.equals(entity.getSawnConfirmed()));

        // Barren-Daten laden (falls Pickup-Nummer vorhanden)
        if (entity.getPickupNumber() != null && !entity.getPickupNumber().isEmpty()) {
            loadIngotData(dto, entity.getPickupNumber());
        }

        return dto;
    }

    /**
     * Lädt die Barren-Daten anhand der Pickup-Nummer
     */
    private void loadIngotData(SawStatusDTO dto, String pickupNumber) {
        try {
            // Versuche Barren per Nummer zu laden
            Optional<IngotDTO> ingotOpt = ingotService.findByIngotNo(pickupNumber);
            if (ingotOpt.isPresent()) {
                IngotDTO ingot = ingotOpt.get();
                dto.setIngotNo(ingot.getIngotNo());
                dto.setProductNo(ingot.getProductNo());
                dto.setProductSuffix(ingot.getProductSuffix());
                dto.setLength(ingot.getLength());
                dto.setWidth(ingot.getWidth());
                dto.setThickness(ingot.getThickness());
                dto.setWeight(ingot.getWeight());
                dto.setHeadSawn(Boolean.TRUE.equals(ingot.getHeadSawn()));
                dto.setFootSawn(Boolean.TRUE.equals(ingot.getFootSawn()));
                dto.setScrap(Boolean.TRUE.equals(ingot.getScrap()));
                dto.setRevised(Boolean.TRUE.equals(ingot.getRevised()));
                log.debug("Loaded ingot data for pickup: {}", pickupNumber);
            }
        } catch (Exception e) {
            log.debug("Could not load ingot data for pickup {}: {}", pickupNumber, e.getMessage());
        }
    }
}
