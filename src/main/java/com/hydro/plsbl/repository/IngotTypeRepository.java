package com.hydro.plsbl.repository;

import com.hydro.plsbl.entity.masterdata.IngotType;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository für IngotType (Barrentypen)
 */
@Repository
public interface IngotTypeRepository extends CrudRepository<IngotType, Long> {

    /**
     * Findet einen Barrentyp anhand des Namens
     */
    Optional<IngotType> findByName(String name);

    /**
     * Findet alle Barrentypen sortiert nach Priorität
     */
    @Query("SELECT * FROM MD_INGOTTYPE ORDER BY PRIORITY, NAME")
    List<IngotType> findAllOrderByPriority();

    /**
     * Findet Barrentypen nach Längentyp
     */
    @Query("SELECT * FROM MD_INGOTTYPE WHERE LENGTH_TYPE = :lengthType ORDER BY PRIORITY")
    List<IngotType> findByLengthType(@Param("lengthType") String lengthType);

    /**
     * Findet Barrentypen die für interne Lagerung erlaubt sind
     */
    @Query("SELECT * FROM MD_INGOTTYPE WHERE INTERNAL_ALLOWED = 1 ORDER BY PRIORITY")
    List<IngotType> findInternalAllowed();

    /**
     * Findet Barrentypen die für externe Lagerung erlaubt sind
     */
    @Query("SELECT * FROM MD_INGOTTYPE WHERE EXTERNAL_ALLOWED = 1 ORDER BY PRIORITY")
    List<IngotType> findExternalAllowed();
}
