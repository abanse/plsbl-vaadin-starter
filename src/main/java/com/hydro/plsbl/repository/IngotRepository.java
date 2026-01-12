package com.hydro.plsbl.repository;

import com.hydro.plsbl.entity.transdata.Ingot;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository für Ingot (Barren)
 */
@Repository
public interface IngotRepository extends CrudRepository<Ingot, Long> {

    /**
     * Findet einen Barren anhand der Barrennummer
     */
    Optional<Ingot> findByIngotNo(String ingotNo);

    /**
     * Findet alle Barren auf einem Lagerplatz
     */
    @Query("SELECT * FROM TD_INGOT WHERE STOCKYARD_ID = :stockyardId ORDER BY PILE_POSITION")
    List<Ingot> findByStockyardId(@Param("stockyardId") Long stockyardId);

    /**
     * Findet alle Barren eines Produkts
     */
    @Query("SELECT * FROM TD_INGOT WHERE PRODUCT_ID = :productId ORDER BY INGOT_NO")
    List<Ingot> findByProductId(@Param("productId") Long productId);

    /**
     * Findet alle Barren auf Lager (mit Lagerplatz)
     */
    @Query("SELECT * FROM TD_INGOT WHERE STOCKYARD_ID IS NOT NULL ORDER BY STOCKYARD_ID, PILE_POSITION")
    List<Ingot> findAllOnStock();

    /**
     * Findet alle freigegebenen Barren
     */
    @Query("SELECT * FROM TD_INGOT WHERE RELEASED_SINCE IS NOT NULL ORDER BY RELEASED_SINCE")
    List<Ingot> findAllReleased();

    /**
     * Zählt Barren auf einem Lagerplatz
     */
    @Query("SELECT COUNT(*) FROM TD_INGOT WHERE STOCKYARD_ID = :stockyardId")
    int countByStockyardId(@Param("stockyardId") Long stockyardId);

    /**
     * Findet den obersten Barren auf einem Stapel
     */
    @Query("SELECT * FROM TD_INGOT WHERE STOCKYARD_ID = :stockyardId ORDER BY PILE_POSITION DESC FETCH FIRST 1 ROWS ONLY")
    Optional<Ingot> findTopIngotOnStockyard(@Param("stockyardId") Long stockyardId);

    /**
     * Findet die letzten N Barren
     */
    @Query("SELECT * FROM TD_INGOT ORDER BY ID DESC FETCH FIRST :limit ROWS ONLY")
    List<Ingot> findLatest(@Param("limit") int limit);

    /**
     * Findet alle Barren sortiert nach ID
     */
    @Query("SELECT * FROM TD_INGOT ORDER BY ID DESC")
    List<Ingot> findAllOrdered();

    /**
     * Zählt alle Barren
     */
    @Query("SELECT COUNT(*) FROM TD_INGOT")
    int countAll();
}
