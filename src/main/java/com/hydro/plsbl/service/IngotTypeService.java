package com.hydro.plsbl.service;

import com.hydro.plsbl.dto.IngotTypeDTO;
import com.hydro.plsbl.entity.enums.LengthType;
import com.hydro.plsbl.entity.masterdata.IngotType;
import com.hydro.plsbl.repository.IngotTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service für Barrentypen-Verwaltung
 */
@Service
public class IngotTypeService {

    private static final Logger log = LoggerFactory.getLogger(IngotTypeService.class);

    private final IngotTypeRepository ingotTypeRepository;
    private final JdbcTemplate jdbcTemplate;

    public IngotTypeService(IngotTypeRepository ingotTypeRepository, JdbcTemplate jdbcTemplate) {
        this.ingotTypeRepository = ingotTypeRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Lädt alle Barrentypen
     */
    public List<IngotTypeDTO> findAll() {
        return ingotTypeRepository.findAllOrderByPriority().stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    /**
     * Findet einen Barrentyp anhand der ID
     */
    public Optional<IngotTypeDTO> findById(Long id) {
        return ingotTypeRepository.findById(id).map(this::toDTO);
    }

    /**
     * Findet einen Barrentyp anhand des Namens
     */
    public Optional<IngotTypeDTO> findByName(String name) {
        return ingotTypeRepository.findByName(name).map(this::toDTO);
    }

    /**
     * Ermittelt den passenden Barrentyp für einen Barren basierend auf seinen Eigenschaften
     *
     * @param length Länge in mm
     * @param width Breite in mm (optional)
     * @param thickness Dicke in mm (optional)
     * @param weight Gewicht in kg (optional)
     * @param productNo Produktnummer für Regex-Matching (optional)
     * @return Der passende Barrentyp oder empty wenn keiner passt
     */
    public Optional<IngotTypeDTO> determineIngotType(Integer length, Integer width, Integer thickness,
                                                      Integer weight, String productNo) {
        log.info("Ermittle Barrentyp für: Länge={}, Breite={}, Dicke={}, Gewicht={}, Produkt={}",
            length, width, thickness, weight, productNo);

        List<IngotType> allTypes = ingotTypeRepository.findAllOrderByPriority();

        for (IngotType type : allTypes) {
            if (matches(type, length, width, thickness, weight, productNo)) {
                log.info("Barrentyp gefunden: {} ({})", type.getName(), type.getLengthType());
                return Optional.of(toDTO(type));
            }
        }

        log.warn("Kein passender Barrentyp gefunden für Länge={}", length);
        return Optional.empty();
    }

    /**
     * Prüft ob ein Barren zu einem Barrentyp passt
     */
    private boolean matches(IngotType type, Integer length, Integer width, Integer thickness,
                            Integer weight, String productNo) {
        // Länge prüfen
        if (length != null) {
            if (type.getMinLength() != null && length < type.getMinLength()) {
                return false;
            }
            if (type.getMaxLength() != null && length > type.getMaxLength()) {
                return false;
            }
        }

        // Breite prüfen
        if (width != null) {
            if (type.getMinWidth() != null && width < type.getMinWidth()) {
                return false;
            }
            if (type.getMaxWidth() != null && width > type.getMaxWidth()) {
                return false;
            }
        }

        // Dicke prüfen
        if (thickness != null) {
            if (type.getMinThickness() != null && thickness < type.getMinThickness()) {
                return false;
            }
            if (type.getMaxThickness() != null && thickness > type.getMaxThickness()) {
                return false;
            }
        }

        // Gewicht prüfen
        if (weight != null) {
            if (type.getMinWeight() != null && weight < type.getMinWeight()) {
                return false;
            }
            if (type.getMaxWeight() != null && weight > type.getMaxWeight()) {
                return false;
            }
        }

        // Produkt-Regex prüfen
        if (productNo != null && type.getProductRegex() != null && !type.getProductRegex().isEmpty()) {
            try {
                Pattern pattern = Pattern.compile(type.getProductRegex());
                if (!pattern.matcher(productNo).matches()) {
                    return false;
                }
            } catch (Exception e) {
                log.warn("Ungültiger Regex für Barrentyp {}: {}", type.getName(), type.getProductRegex());
            }
        }

        return true;
    }

    /**
     * Ermittelt den Längentyp (StockyardUsage) für einen Barren
     * Rückwärtskompatibel zu SHORT/LONG
     */
    public LengthType determineLengthType(Integer length, Integer width, Integer thickness,
                                          Integer weight, String productNo) {
        Optional<IngotTypeDTO> type = determineIngotType(length, width, thickness, weight, productNo);

        if (type.isPresent() && type.get().getLengthType() != null) {
            return type.get().getLengthType();
        }

        // Fallback: Einfache Längenprüfung (alte Logik)
        if (length != null && length > 6000) {
            return LengthType.LONG;
        }
        return LengthType.SHORT;
    }

    /**
     * Speichert einen Barrentyp (neu oder Update)
     */
    @Transactional
    public IngotTypeDTO save(IngotTypeDTO dto) {
        if (dto.getId() == null) {
            return create(dto);
        } else {
            return update(dto);
        }
    }

    private IngotTypeDTO create(IngotTypeDTO dto) {
        log.info("Erstelle neuen Barrentyp: {}", dto.getName());

        // Prüfen ob Name bereits existiert
        if (ingotTypeRepository.findByName(dto.getName()).isPresent()) {
            throw new IllegalArgumentException("Barrentyp mit diesem Namen existiert bereits: " + dto.getName());
        }

        Long newId = getNextId();
        jdbcTemplate.update(
            "INSERT INTO MD_INGOTTYPE (ID, SERIAL, TABLESERIAL, NAME, DESCRIPTION, LENGTH_TYPE, " +
            "INTERNAL_ALLOWED, EXTERNAL_ALLOWED, RETRIEVAL_ALLOWED, AUTO_RETRIEVAL, " +
            "MIN_LENGTH, MAX_LENGTH, MIN_WIDTH, MAX_WIDTH, MIN_THICKNESS, MAX_THICKNESS, " +
            "MIN_WEIGHT, MAX_WEIGHT, PRODUCT_REGEX, PRIORITY) " +
            "VALUES (?, 1, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            newId,
            dto.getName(),
            dto.getDescription(),
            dto.getLengthType() != null ? String.valueOf(dto.getLengthType().getCode()) : null,
            boolToInt(dto.getInternalAllowed()),
            boolToInt(dto.getExternalAllowed()),
            boolToInt(dto.getRetrievalAllowed()),
            boolToInt(dto.getAutoRetrieval()),
            dto.getMinLength(),
            dto.getMaxLength(),
            dto.getMinWidth(),
            dto.getMaxWidth(),
            dto.getMinThickness(),
            dto.getMaxThickness(),
            dto.getMinWeight(),
            dto.getMaxWeight(),
            dto.getProductRegex(),
            dto.getPriority() != null ? dto.getPriority() : 0
        );

        dto.setId(newId);
        dto.setSerial(1L);
        log.info("Barrentyp erstellt: ID={}, Name={}", newId, dto.getName());
        return dto;
    }

    private IngotTypeDTO update(IngotTypeDTO dto) {
        log.info("Aktualisiere Barrentyp: ID={}, Name={}", dto.getId(), dto.getName());

        // Prüfen ob Name bereits von anderem Barrentyp verwendet wird
        Optional<IngotType> existing = ingotTypeRepository.findByName(dto.getName());
        if (existing.isPresent() && !existing.get().getId().equals(dto.getId())) {
            throw new IllegalArgumentException("Barrentyp mit diesem Namen existiert bereits: " + dto.getName());
        }

        int updated = jdbcTemplate.update(
            "UPDATE MD_INGOTTYPE SET NAME = ?, DESCRIPTION = ?, LENGTH_TYPE = ?, " +
            "INTERNAL_ALLOWED = ?, EXTERNAL_ALLOWED = ?, RETRIEVAL_ALLOWED = ?, AUTO_RETRIEVAL = ?, " +
            "MIN_LENGTH = ?, MAX_LENGTH = ?, MIN_WIDTH = ?, MAX_WIDTH = ?, " +
            "MIN_THICKNESS = ?, MAX_THICKNESS = ?, MIN_WEIGHT = ?, MAX_WEIGHT = ?, " +
            "PRODUCT_REGEX = ?, PRIORITY = ?, SERIAL = SERIAL + 1 WHERE ID = ?",
            dto.getName(),
            dto.getDescription(),
            dto.getLengthType() != null ? String.valueOf(dto.getLengthType().getCode()) : null,
            boolToInt(dto.getInternalAllowed()),
            boolToInt(dto.getExternalAllowed()),
            boolToInt(dto.getRetrievalAllowed()),
            boolToInt(dto.getAutoRetrieval()),
            dto.getMinLength(),
            dto.getMaxLength(),
            dto.getMinWidth(),
            dto.getMaxWidth(),
            dto.getMinThickness(),
            dto.getMaxThickness(),
            dto.getMinWeight(),
            dto.getMaxWeight(),
            dto.getProductRegex(),
            dto.getPriority() != null ? dto.getPriority() : 0,
            dto.getId()
        );

        if (updated == 0) {
            throw new IllegalArgumentException("Barrentyp nicht gefunden: ID=" + dto.getId());
        }

        log.info("Barrentyp aktualisiert: ID={}", dto.getId());
        return dto;
    }

    /**
     * Löscht einen Barrentyp
     */
    @Transactional
    public void delete(Long id) {
        log.info("Lösche Barrentyp: ID={}", id);
        ingotTypeRepository.deleteById(id);
        log.info("Barrentyp gelöscht: ID={}", id);
    }

    private Long getNextId() {
        try {
            Long maxId = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(ID), 0) + 1 FROM MD_INGOTTYPE", Long.class);
            return maxId != null ? maxId : 1L;
        } catch (Exception e) {
            log.warn("Konnte nächste ID nicht ermitteln, verwende Timestamp", e);
            return System.currentTimeMillis();
        }
    }

    private Integer boolToInt(Boolean value) {
        return value != null && value ? 1 : 0;
    }

    private IngotTypeDTO toDTO(IngotType entity) {
        IngotTypeDTO dto = new IngotTypeDTO();
        dto.setId(entity.getId());
        dto.setSerial(entity.getSerial());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setLengthType(entity.getLengthType());
        dto.setInternalAllowed(entity.getInternalAllowed());
        dto.setExternalAllowed(entity.getExternalAllowed());
        dto.setRetrievalAllowed(entity.getRetrievalAllowed());
        dto.setAutoRetrieval(entity.getAutoRetrieval());
        dto.setMinLength(entity.getMinLength());
        dto.setMaxLength(entity.getMaxLength());
        dto.setMinWidth(entity.getMinWidth());
        dto.setMaxWidth(entity.getMaxWidth());
        dto.setMinThickness(entity.getMinThickness());
        dto.setMaxThickness(entity.getMaxThickness());
        dto.setMinWeight(entity.getMinWeight());
        dto.setMaxWeight(entity.getMaxWeight());
        dto.setProductRegex(entity.getProductRegex());
        dto.setPriority(entity.getPriority());
        return dto;
    }
}
