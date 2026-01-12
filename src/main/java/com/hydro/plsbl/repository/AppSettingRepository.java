package com.hydro.plsbl.repository;

import com.hydro.plsbl.entity.masterdata.AppSetting;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository für AppSetting (Anwendungs-Einstellungen)
 */
@Repository
public interface AppSettingRepository extends CrudRepository<AppSetting, String> {

    /**
     * Findet eine Einstellung anhand des Schlüssels
     */
    @Query("SELECT * FROM MD_APPSETTING WHERE SETTING_KEY = :key")
    Optional<AppSetting> findByKey(@Param("key") String key);

    /**
     * Findet alle Einstellungen einer Kategorie
     */
    @Query("SELECT * FROM MD_APPSETTING WHERE CATEGORY = :category ORDER BY SETTING_KEY")
    List<AppSetting> findByCategory(@Param("category") String category);

    /**
     * Findet alle Einstellungen
     */
    @Query("SELECT * FROM MD_APPSETTING ORDER BY CATEGORY, SETTING_KEY")
    List<AppSetting> findAllOrdered();

    /**
     * Prüft ob eine Einstellung existiert
     */
    @Query("SELECT COUNT(*) FROM MD_APPSETTING WHERE SETTING_KEY = :key")
    int countByKey(@Param("key") String key);
}
