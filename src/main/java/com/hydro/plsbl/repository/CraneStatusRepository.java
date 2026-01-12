package com.hydro.plsbl.repository;

import com.hydro.plsbl.entity.transdata.CraneStatus;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository für CraneStatus
 *
 * Hinweis: Es gibt nur einen Kran-Status-Eintrag (ID=1)
 */
@Repository
public interface CraneStatusRepository extends CrudRepository<CraneStatus, Long> {

    /**
     * Holt den aktuellen Kran-Status (es gibt nur einen Eintrag)
     */
    @Query("SELECT * FROM TD_CRANESTATUS WHERE ID = 1")
    Optional<CraneStatus> findCurrent();

    /**
     * Holt den ersten Eintrag (Alternative)
     */
    @Query("SELECT * FROM TD_CRANESTATUS FETCH FIRST 1 ROWS ONLY")
    Optional<CraneStatus> findFirst();

    /**
     * Aktualisiert nur die Simulator-relevanten Spalten (für Oracle-Kompatibilität).
     * Nur X/Y-Position, Crane-Mode, Gripper-State und Job-State werden aktualisiert.
     */
    @Modifying
    @Query("UPDATE TD_CRANESTATUS SET " +
           "X_POSITION = :xPos, " +
           "Y_POSITION = :yPos, " +
           "SERIAL = SERIAL + 1 " +
           "WHERE ID = 1")
    int updateSimulatorPosition(@Param("xPos") int xPosition,
                                @Param("yPos") int yPosition);

    /**
     * Aktualisiert die Stockyard-IDs für den aktuellen Auftrag
     */
    @Modifying
    @Query("UPDATE TD_CRANESTATUS SET " +
           "FROM_STOCKYARD_ID = :fromId, " +
           "TO_STOCKYARD_ID = :toId " +
           "WHERE ID = 1")
    int updateStockyardIds(@Param("fromId") Long fromStockyardId,
                           @Param("toId") Long toStockyardId);
}
