package com.hydro.plsbl.repository;

import com.hydro.plsbl.entity.transdata.TransportOrder;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository für TransportOrder (Transportaufträge)
 */
@Repository
public interface TransportOrderRepository extends CrudRepository<TransportOrder, Long> {

    /**
     * Findet einen Auftrag anhand der Transportnummer
     */
    @Query("SELECT * FROM TD_TRANSPORTORDER WHERE TRANSPORT_NO = :transportNo")
    Optional<TransportOrder> findByTransportNo(@Param("transportNo") String transportNo);

    /**
     * Findet alle Aufträge sortiert nach ID
     */
    @Query("SELECT * FROM TD_TRANSPORTORDER ORDER BY ID DESC")
    List<TransportOrder> findAllOrdered();

    /**
     * Findet die letzten N Aufträge
     */
    @Query("SELECT * FROM TD_TRANSPORTORDER ORDER BY ID DESC FETCH FIRST :limit ROWS ONLY")
    List<TransportOrder> findLatest(@Param("limit") int limit);

    /**
     * Findet Aufträge für einen Barren
     */
    @Query("SELECT * FROM TD_TRANSPORTORDER WHERE INGOT_ID = :ingotId ORDER BY ID DESC")
    List<TransportOrder> findByIngotId(@Param("ingotId") Long ingotId);

    /**
     * Findet Aufträge für einen Lagerplatz (als Quelle)
     */
    @Query("SELECT * FROM TD_TRANSPORTORDER WHERE FROM_YARD_ID = :yardId ORDER BY ID DESC")
    List<TransportOrder> findByFromYardId(@Param("yardId") Long yardId);

    /**
     * Findet Aufträge für einen Lagerplatz (als Ziel)
     */
    @Query("SELECT * FROM TD_TRANSPORTORDER WHERE TO_YARD_ID = :yardId ORDER BY ID DESC")
    List<TransportOrder> findByToYardId(@Param("yardId") Long yardId);

    /**
     * Findet Aufträge für einen Calloff
     */
    @Query("SELECT * FROM TD_TRANSPORTORDER WHERE CALLOFF_ID = :calloffId ORDER BY ID DESC")
    List<TransportOrder> findByCalloffId(@Param("calloffId") Long calloffId);

    /**
     * Zählt alle Aufträge
     */
    @Query("SELECT COUNT(*) FROM TD_TRANSPORTORDER")
    int countAll();

    /**
     * Findet Aufträge nach Status
     */
    @Query("SELECT * FROM TD_TRANSPORTORDER WHERE STATUS = :status ORDER BY PRIORITY DESC NULLS LAST, ID ASC")
    List<TransportOrder> findByStatus(@Param("status") String status);

    /**
     * Findet alle aktiven Aufträge (nicht abgeschlossen)
     */
    @Query("SELECT * FROM TD_TRANSPORTORDER WHERE STATUS IN ('P', 'I', 'U', 'H') ORDER BY PRIORITY DESC NULLS LAST, ID ASC")
    List<TransportOrder> findActiveOrders();
}
