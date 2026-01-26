package com.hydro.plsbl.repository;

import com.hydro.plsbl.entity.transdata.ShipmentLine;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository für ShipmentLine (Lieferschein-Positionen)
 */
@Repository
public interface ShipmentLineRepository extends CrudRepository<ShipmentLine, Long> {

    /**
     * Findet alle Positionen eines Lieferscheins
     */
    @Query("SELECT * FROM TD_SHIPMENTLINE WHERE SHIPMENT_ID = :shipmentId ORDER BY SHIPMENT_POS")
    List<ShipmentLine> findByShipmentId(@Param("shipmentId") Long shipmentId);

    /**
     * Findet Position nach Barren-Nummer
     */
    @Query("SELECT * FROM TD_SHIPMENTLINE WHERE INGOT_NO = :ingotNo ORDER BY ID DESC")
    List<ShipmentLine> findByIngotNumber(@Param("ingotNo") String ingotNumber);

    /**
     * Löscht alle Positionen eines Lieferscheins
     */
    @Query("DELETE FROM TD_SHIPMENTLINE WHERE SHIPMENT_ID = :shipmentId")
    void deleteByShipmentId(@Param("shipmentId") Long shipmentId);

    /**
     * Zählt Positionen eines Lieferscheins
     */
    @Query("SELECT COUNT(*) FROM TD_SHIPMENTLINE WHERE SHIPMENT_ID = :shipmentId")
    int countByShipmentId(@Param("shipmentId") Long shipmentId);

    /**
     * Berechnet Gesamtgewicht eines Lieferscheins
     */
    @Query("SELECT COALESCE(SUM(WEIGHT), 0) FROM TD_SHIPMENTLINE WHERE SHIPMENT_ID = :shipmentId")
    int getTotalWeight(@Param("shipmentId") Long shipmentId);
}
