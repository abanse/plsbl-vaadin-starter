package com.hydro.plsbl.service;

import com.hydro.plsbl.dto.ProductDTO;
import com.hydro.plsbl.entity.masterdata.Product;
import com.hydro.plsbl.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service für Produkt/Artikel-Verwaltung
 */
@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;
    private final JdbcTemplate jdbcTemplate;

    public ProductService(ProductRepository productRepository, JdbcTemplate jdbcTemplate) {
        this.productRepository = productRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Lädt alle Produkte
     */
    public List<ProductDTO> findAll() {
        return productRepository.findAllOrderByProductNo().stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    /**
     * Findet ein Produkt anhand der ID
     */
    public Optional<ProductDTO> findById(Long id) {
        return productRepository.findById(id).map(this::toDTO);
    }

    /**
     * Findet ein Produkt anhand der Produktnummer
     */
    public Optional<ProductDTO> findByProductNo(String productNo) {
        return productRepository.findByProductNo(productNo).map(this::toDTO);
    }

    /**
     * Sucht Produkte nach Beschreibung
     */
    public List<ProductDTO> searchByDescription(String search) {
        return productRepository.findByDescriptionContaining("%" + search + "%").stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    /**
     * Speichert ein Produkt (neu oder Update)
     */
    @Transactional
    public ProductDTO save(ProductDTO dto) {
        if (dto.getId() == null) {
            return create(dto);
        } else {
            return update(dto);
        }
    }

    /**
     * Erstellt ein neues Produkt
     */
    private ProductDTO create(ProductDTO dto) {
        log.info("Erstelle neues Produkt: {}", dto.getProductNo());

        // Prüfen ob Produktnummer bereits existiert
        if (productRepository.findByProductNo(dto.getProductNo()).isPresent()) {
            throw new IllegalArgumentException("Produktnummer existiert bereits: " + dto.getProductNo());
        }

        Long newId = getNextId();
        jdbcTemplate.update(
            "INSERT INTO MD_PRODUCT (ID, SERIAL, TABLESERIAL, PRODUCT_NO, DESCRIPTION, MAX_PER_LOCATION) VALUES (?, 1, 1, ?, ?, ?)",
            newId,
            dto.getProductNo(),
            dto.getDescription(),
            dto.getMaxPerLocation() != null ? dto.getMaxPerLocation() : 8
        );

        dto.setId(newId);
        dto.setSerial(1L);
        log.info("Produkt erstellt: ID={}, ProductNo={}", newId, dto.getProductNo());
        return dto;
    }

    /**
     * Aktualisiert ein bestehendes Produkt
     */
    private ProductDTO update(ProductDTO dto) {
        log.info("Aktualisiere Produkt: ID={}, ProductNo={}", dto.getId(), dto.getProductNo());

        // Prüfen ob Produktnummer bereits von anderem Produkt verwendet wird
        Optional<Product> existing = productRepository.findByProductNo(dto.getProductNo());
        if (existing.isPresent() && !existing.get().getId().equals(dto.getId())) {
            throw new IllegalArgumentException("Produktnummer wird bereits verwendet: " + dto.getProductNo());
        }

        int updated = jdbcTemplate.update(
            "UPDATE MD_PRODUCT SET PRODUCT_NO = ?, DESCRIPTION = ?, MAX_PER_LOCATION = ?, SERIAL = SERIAL + 1 WHERE ID = ?",
            dto.getProductNo(),
            dto.getDescription(),
            dto.getMaxPerLocation() != null ? dto.getMaxPerLocation() : 8,
            dto.getId()
        );

        if (updated == 0) {
            throw new IllegalArgumentException("Produkt nicht gefunden: ID=" + dto.getId());
        }

        log.info("Produkt aktualisiert: ID={}", dto.getId());
        return dto;
    }

    /**
     * Löscht ein Produkt
     */
    @Transactional
    public void delete(Long id) {
        log.info("Lösche Produkt: ID={}", id);

        // Prüfen ob Produkt verwendet wird (Barren auf Lagerplätzen)
        Integer usageCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM TD_INGOT WHERE PRODUCT_ID = ?",
            Integer.class, id);

        if (usageCount != null && usageCount > 0) {
            throw new IllegalStateException("Produkt kann nicht gelöscht werden - " + usageCount + " Barren verwenden dieses Produkt");
        }

        productRepository.deleteById(id);
        log.info("Produkt gelöscht: ID={}", id);
    }

    /**
     * Ermittelt die nächste freie ID
     */
    private Long getNextId() {
        try {
            Long maxId = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(ID), 0) + 1 FROM MD_PRODUCT", Long.class);
            return maxId != null ? maxId : 1L;
        } catch (Exception e) {
            log.warn("Konnte nächste ID nicht ermitteln, verwende Timestamp", e);
            return System.currentTimeMillis();
        }
    }

    /**
     * Konvertiert Entity zu DTO
     */
    private ProductDTO toDTO(Product product) {
        ProductDTO dto = new ProductDTO();
        dto.setId(product.getId());
        dto.setSerial(product.getSerial());
        dto.setProductNo(product.getProductNo());
        dto.setDescription(product.getDescription());
        dto.setMaxPerLocation(product.getMaxPerLocation());
        return dto;
    }
}
