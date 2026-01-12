package com.hydro.plsbl.repository;

import com.hydro.plsbl.entity.transdata.PlsStatus;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository für PlsStatus (Säge-Einlagerung)
 *
 * Hinweis: Es gibt nur einen PLS-Status-Eintrag (ID=1)
 */
@Repository
public interface PlsStatusRepository extends CrudRepository<PlsStatus, Long> {

    /**
     * Holt den aktuellen PLS-Status (es gibt nur einen Eintrag)
     */
    @Query("SELECT * FROM TD_PLSSTATUS WHERE ID = 1")
    Optional<PlsStatus> findCurrent();

    /**
     * Holt den ersten Eintrag (Alternative)
     */
    @Query("SELECT * FROM TD_PLSSTATUS FETCH FIRST 1 ROWS ONLY")
    Optional<PlsStatus> findFirst();
}
