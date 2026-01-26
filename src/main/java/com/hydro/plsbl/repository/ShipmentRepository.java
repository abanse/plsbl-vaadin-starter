package com.hydro.plsbl.repository;

import com.hydro.plsbl.entity.transdata.Shipment;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository für Shipment (Lieferschein)
 */
@Repository
public interface ShipmentRepository extends CrudRepository<Shipment, Long> {

    /**
     * Findet einen Lieferschein anhand seiner Nummer
     */
    Optional<Shipment> findByShipmentNumber(String shipmentNumber);

    /**
     * Findet alle Lieferscheine nach Auftragsnummer
     */
    @Query("SELECT * FROM TD_SHIPMENT WHERE ORDER_NO = :orderNo ORDER BY DELIVERED DESC")
    List<Shipment> findByOrderNumber(@Param("orderNo") String orderNumber);

    /**
     * Findet alle Lieferscheine nach Kundennummer
     */
    @Query("SELECT * FROM TD_SHIPMENT WHERE CUSTOMER_NO = :customerNo ORDER BY DELIVERED DESC")
    List<Shipment> findByCustomerNumber(@Param("customerNo") String customerNumber);

    /**
     * Findet alle Lieferscheine nach Lieferort
     */
    @Query("SELECT * FROM TD_SHIPMENT WHERE DESTINATION = :destination ORDER BY DELIVERED DESC")
    List<Shipment> findByDestination(@Param("destination") String destination);

    /**
     * Findet alle Lieferscheine in einem Zeitraum
     */
    @Query("SELECT * FROM TD_SHIPMENT WHERE DELIVERED BETWEEN :fromDate AND :toDate ORDER BY DELIVERED DESC")
    List<Shipment> findByDeliveredBetween(
        @Param("fromDate") LocalDateTime fromDate,
        @Param("toDate") LocalDateTime toDate
    );

    /**
     * Findet alle Lieferscheine - einfache Variante ohne Filter
     */
    @Query("SELECT * FROM TD_SHIPMENT ORDER BY DELIVERED DESC")
    List<Shipment> findAllOrderByDeliveredDesc();

    /**
     * Holt die nächste Lieferschein-Nummer (für Sequenz)
     */
    @Query("SELECT COALESCE(MAX(CAST(SHIPMENT_NO AS NUMBER)), 0) + 1 FROM TD_SHIPMENT")
    Long getNextShipmentNumber();

    /**
     * Zählt alle Lieferscheine
     */
    @Query("SELECT COUNT(*) FROM TD_SHIPMENT")
    long countAll();
}
