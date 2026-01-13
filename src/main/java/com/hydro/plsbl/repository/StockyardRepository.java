package com.hydro.plsbl.repository;

import com.hydro.plsbl.entity.masterdata.Stockyard;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository für Stockyard (Lagerplatz)
 */
@Repository
public interface StockyardRepository extends CrudRepository<Stockyard, Long> {
    
    /**
     * Findet einen Lagerplatz anhand seiner Nummer
     */
    Optional<Stockyard> findByYardNumber(String yardNumber);
    
    /**
     * Findet alle Lagerplätze eines bestimmten Typs
     */
    @Query("SELECT * FROM MD_STOCKYARD WHERE YARD_TYPE = :type ORDER BY Y_COORDINATE DESC, X_COORDINATE")
    List<Stockyard> findByType(@Param("type") String type);
    
    /**
     * Findet alle Lagerplätze die in der StockView angezeigt werden sollen
     * (Intern, Extern, Verladung) im Koordinatenbereich (1-17, 1-10)
     * PLUS Säge-Plätze (beliebige Koordinaten, da außerhalb des Grids)
     */
    @Query("SELECT * FROM MD_STOCKYARD WHERE (YARD_TYPE IN ('I', 'E', 'L') AND X_COORDINATE BETWEEN 1 AND 17 AND Y_COORDINATE BETWEEN 1 AND 10) OR YARD_TYPE = 'S' ORDER BY Y_COORDINATE DESC, X_COORDINATE")
    List<Stockyard> findAllForStockView();
    
    /**
     * Findet einen Lagerplatz anhand seiner Koordinaten
     */
    @Query("SELECT * FROM MD_STOCKYARD WHERE X_COORDINATE = :x AND Y_COORDINATE = :y")
    Optional<Stockyard> findByCoordinates(@Param("x") int x, @Param("y") int y);
    
    /**
     * Findet alle Lagerplätze in einem Koordinatenbereich
     */
    @Query("""
        SELECT * FROM MD_STOCKYARD 
        WHERE X_COORDINATE BETWEEN :minX AND :maxX 
        AND Y_COORDINATE BETWEEN :minY AND :maxY
        ORDER BY Y_COORDINATE DESC, X_COORDINATE
    """)
    List<Stockyard> findInRange(
        @Param("minX") int minX, 
        @Param("maxX") int maxX,
        @Param("minY") int minY, 
        @Param("maxY") int maxY
    );
    
    /**
     * Zählt alle Lagerplätze eines Typs
     */
    @Query("SELECT COUNT(*) FROM MD_STOCKYARD WHERE YARD_TYPE = :type")
    int countByType(@Param("type") String type);

    /**
     * Findet alle Lagerplätze die als Ziel verfügbar sind (Einlagern erlaubt)
     */
    @Query("SELECT * FROM MD_STOCKYARD WHERE TO_STOCK_ALLOWED = 1 ORDER BY YARD_NO")
    List<Stockyard> findAvailableDestinations();

    /**
     * Findet benachbarte kurze Lagerplätze für Merge-Operation.
     * Benachbart bedeutet: gleiche Y-Koordinate, X-Koordinate +1 oder -1.
     */
    @Query("""
        SELECT * FROM MD_STOCKYARD
        WHERE Y_COORDINATE = :y
        AND X_COORDINATE IN (:xMinus1, :xPlus1)
        AND YARD_USAGE = 'S'
    """)
    List<Stockyard> findAdjacentShortStockyards(
        @Param("y") int y,
        @Param("xMinus1") int xMinus1,
        @Param("xPlus1") int xPlus1
    );

    /**
     * Zählt Lagerplätze an einer bestimmten Koordinate (für Split-Validierung)
     */
    @Query("SELECT COUNT(*) FROM MD_STOCKYARD WHERE X_COORDINATE = :x AND Y_COORDINATE = :y")
    int countByCoordinates(@Param("x") int x, @Param("y") int y);
}
