package com.hydro.plsbl.repository;

import com.hydro.plsbl.entity.masterdata.Product;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository für Product
 */
@Repository
public interface ProductRepository extends CrudRepository<Product, Long> {

    /**
     * Findet ein Produkt anhand der Produktnummer
     */
    Optional<Product> findByProductNo(String productNo);

    /**
     * Findet alle Produkte sortiert nach Produktnummer
     */
    @Query("SELECT * FROM MD_PRODUCT ORDER BY PRODUCT_NO")
    List<Product> findAllOrderByProductNo();

    /**
     * Sucht Produkte nach Beschreibung (LIKE)
     */
    @Query("SELECT * FROM MD_PRODUCT WHERE UPPER(DESCRIPTION) LIKE UPPER(:search) ORDER BY PRODUCT_NO")
    List<Product> findByDescriptionContaining(@Param("search") String search);
}
