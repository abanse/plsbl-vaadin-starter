package com.hydro.plsbl.repository;

import com.hydro.plsbl.entity.transdata.StockyardStatus;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository für StockyardStatus
 */
@Repository
public interface StockyardStatusRepository extends CrudRepository<StockyardStatus, Long> {
    
    /**
     * Findet den Status für einen bestimmten Lagerplatz
     */
    Optional<StockyardStatus> findByStockyardId(Long stockyardId);
    
    /**
     * Findet alle Status-Einträge für eine Liste von Stockyard-IDs
     */
    @Query("SELECT * FROM TD_STOCKYARDSTATUS WHERE STOCKYARD_ID IN (:ids)")
    List<StockyardStatus> findByStockyardIdIn(@Param("ids") List<Long> stockyardIds);

    /**
     * Findet alle nicht-leeren Lagerplätze
     */
    @Query("SELECT * FROM TD_STOCKYARDSTATUS WHERE INGOTS_COUNT > 0")
    List<StockyardStatus> findAllNonEmpty();

    /**
     * Findet Status nach Produkt
     */
    @Query("SELECT * FROM TD_STOCKYARDSTATUS WHERE PRODUCT_ID = :productId AND INGOTS_COUNT > 0")
    List<StockyardStatus> findByProductId(@Param("productId") Long productId);
}
