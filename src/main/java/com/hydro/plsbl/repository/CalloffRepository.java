package com.hydro.plsbl.repository;

import com.hydro.plsbl.entity.transdata.Calloff;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository für Calloff (Abrufe)
 */
@Repository
public interface CalloffRepository extends CrudRepository<Calloff, Long> {

    /**
     * Findet einen Abruf anhand der Abrufnummer
     */
    @Query("SELECT * FROM TD_CALLOFF WHERE CALLOFF_NUMBER = :calloffNumber")
    Optional<Calloff> findByCalloffNumber(@Param("calloffNumber") String calloffNumber);

    /**
     * Findet alle Abrufe sortiert nach ID
     */
    @Query("SELECT * FROM TD_CALLOFF ORDER BY ID DESC")
    List<Calloff> findAllOrdered();

    /**
     * Findet alle offenen (nicht abgeschlossenen) Abrufe
     */
    @Query("SELECT * FROM TD_CALLOFF WHERE COMPLETED = FALSE OR COMPLETED IS NULL ORDER BY DELIVERY_DATE ASC NULLS LAST, ID ASC")
    List<Calloff> findIncomplete();

    /**
     * Findet alle genehmigten, nicht abgeschlossenen Abrufe (lieferbar)
     */
    @Query("SELECT * FROM TD_CALLOFF WHERE APPROVED = TRUE AND (COMPLETED = FALSE OR COMPLETED IS NULL) " +
           "ORDER BY DELIVERY_DATE ASC NULLS LAST, ID ASC")
    List<Calloff> findDeliverable();

    /**
     * Findet alle nicht genehmigten Abrufe
     */
    @Query("SELECT * FROM TD_CALLOFF WHERE APPROVED = FALSE OR APPROVED IS NULL ORDER BY DELIVERY_DATE ASC NULLS LAST, ID ASC")
    List<Calloff> findNotApproved();

    /**
     * Findet Abrufe nach Ziel/Destination
     */
    @Query("SELECT * FROM TD_CALLOFF WHERE DESTINATION = :destination ORDER BY DELIVERY_DATE ASC NULLS LAST, ID ASC")
    List<Calloff> findByDestination(@Param("destination") String destination);

    /**
     * Findet Abrufe nach Kundennummer
     */
    @Query("SELECT * FROM TD_CALLOFF WHERE CUSTOMER_NUMBER = :customerNumber ORDER BY DELIVERY_DATE ASC NULLS LAST, ID ASC")
    List<Calloff> findByCustomerNumber(@Param("customerNumber") String customerNumber);

    /**
     * Findet Abrufe nach Auftragsnummer
     */
    @Query("SELECT * FROM TD_CALLOFF WHERE ORDER_NUMBER = :orderNumber ORDER BY ORDER_POSITION ASC, ID ASC")
    List<Calloff> findByOrderNumber(@Param("orderNumber") String orderNumber);

    /**
     * Findet Abrufe nach SAP-Produktnummer
     */
    @Query("SELECT * FROM TD_CALLOFF WHERE SAP_PRODUCT_NUMBER = :sapProductNumber ORDER BY DELIVERY_DATE ASC NULLS LAST, ID ASC")
    List<Calloff> findBySapProductNumber(@Param("sapProductNumber") String sapProductNumber);

    /**
     * Findet Abrufe mit Liefertermin in einem Zeitraum
     */
    @Query("SELECT * FROM TD_CALLOFF WHERE DELIVERY_DATE BETWEEN :fromDate AND :toDate ORDER BY DELIVERY_DATE ASC, ID ASC")
    List<Calloff> findByDeliveryDateBetween(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    /**
     * Findet Abrufe mit Liefertermin bis zu einem Datum
     */
    @Query("SELECT * FROM TD_CALLOFF WHERE DELIVERY_DATE <= :toDate AND (COMPLETED = 0 OR COMPLETED IS NULL) " +
           "ORDER BY DELIVERY_DATE ASC, ID ASC")
    List<Calloff> findDueUntil(@Param("toDate") LocalDate toDate);

    /**
     * Zählt alle Abrufe
     */
    @Query("SELECT COUNT(*) FROM TD_CALLOFF")
    int countAll();

    /**
     * Zählt offene Abrufe
     */
    @Query("SELECT COUNT(*) FROM TD_CALLOFF WHERE COMPLETED = 0 OR COMPLETED IS NULL")
    int countIncomplete();

    /**
     * Suche nach verschiedenen Kriterien (vereinfacht)
     */
    @Query("SELECT * FROM TD_CALLOFF WHERE " +
           "(CALLOFF_NUMBER LIKE :pattern OR ORDER_NUMBER LIKE :pattern OR CUSTOMER_NUMBER LIKE :pattern) " +
           "ORDER BY DELIVERY_DATE ASC NULLS LAST, ID ASC")
    List<Calloff> searchByPattern(@Param("pattern") String pattern);
}
