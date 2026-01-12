package com.hydro.plsbl.repository;

import com.hydro.plsbl.entity.transdata.CraneCommand;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository für CraneCommand (Kran-Kommandos)
 */
@Repository
public interface CraneCommandRepository extends CrudRepository<CraneCommand, Long> {

    /**
     * Findet alle Kommandos
     */
    @Query("SELECT * FROM TD_CRANECOMMAND ORDER BY ID DESC")
    List<CraneCommand> findAllOrdered();

    /**
     * Findet die letzten N Kommandos
     */
    @Query("SELECT * FROM TD_CRANECOMMAND ORDER BY ID DESC FETCH FIRST :limit ROWS ONLY")
    List<CraneCommand> findLatest(@Param("limit") int limit);

    /**
     * Findet Kommandos nach Typ
     */
    @Query("SELECT * FROM TD_CRANECOMMAND WHERE CMD_TYPE = :cmdType ORDER BY ID DESC")
    List<CraneCommand> findByCmdType(@Param("cmdType") String cmdType);

    /**
     * Findet Kommandos nach Kran-Modus
     */
    @Query("SELECT * FROM TD_CRANECOMMAND WHERE CRANE_MODE = :craneMode ORDER BY ID DESC")
    List<CraneCommand> findByCraneMode(@Param("craneMode") String craneMode);

    /**
     * Findet Kommandos für einen Lagerplatz (als Quelle)
     */
    @Query("SELECT * FROM TD_CRANECOMMAND WHERE FROM_YARD_ID = :yardId ORDER BY ID DESC")
    List<CraneCommand> findByFromYardId(@Param("yardId") Long yardId);

    /**
     * Findet Kommandos für einen Lagerplatz (als Ziel)
     */
    @Query("SELECT * FROM TD_CRANECOMMAND WHERE TO_YARD_ID = :yardId ORDER BY ID DESC")
    List<CraneCommand> findByToYardId(@Param("yardId") Long yardId);

    /**
     * Findet Kommandos für einen Barren
     */
    @Query("SELECT * FROM TD_CRANECOMMAND WHERE INGOT_ID = :ingotId ORDER BY ID DESC")
    List<CraneCommand> findByIngotId(@Param("ingotId") Long ingotId);

    /**
     * Zählt alle Kommandos
     */
    @Query("SELECT COUNT(*) FROM TD_CRANECOMMAND")
    int countAll();
}
