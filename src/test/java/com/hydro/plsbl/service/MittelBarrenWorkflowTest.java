package com.hydro.plsbl.service;

import com.hydro.plsbl.dto.IngotTypeDTO;
import com.hydro.plsbl.entity.enums.LengthType;
import com.hydro.plsbl.entity.masterdata.Stockyard;
import com.hydro.plsbl.repository.StockyardRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration-Test fuer den MITTEL-Barren Workflow.
 *
 * Testet:
 * 1. Barrentyp-Erkennung (KURZ, MITTEL, LANG)
 * 2. SWAPOUT-Routing fuer MITTEL-Barren
 * 3. Auto-Auslagerung bei Abruf-Genehmigung
 *
 * Voraussetzungen:
 * - H2-Datenbank mit Test-Daten (data-h2.sql)
 * - MITTEL-Barren auf externen Plaetzen (16/10, 17/10, etc.)
 */
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({IngotTypeService.class})
@ActiveProfiles("h2")
@DisplayName("MITTEL-Barren Workflow Tests")
class MittelBarrenWorkflowTest {

    @Autowired
    private IngotTypeService ingotTypeService;

    @Autowired
    private StockyardRepository stockyardRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ========== 1. Barrentyp-Erkennung Tests ==========

    @Test
    @DisplayName("KURZ-Barren (3500-4300mm) wird korrekt erkannt")
    void testKurzBarrenTypErkennung() {
        // Given: Barren mit 4000mm Laenge (im KURZ-Bereich)
        int length = 4000;

        // When: Barrentyp ermitteln
        Optional<IngotTypeDTO> result = ingotTypeService.determineIngotType(length, null, null, null, null);

        // Then: KURZ-Typ erwartet
        assertTrue(result.isPresent(), "Barrentyp sollte gefunden werden");
        assertEquals("KURZ", result.get().getName());
        assertEquals(LengthType.SHORT, result.get().getLengthType());
        assertTrue(result.get().getInternalAllowed(), "KURZ sollte intern erlaubt sein");
        assertFalse(result.get().getSawToSwapout(), "KURZ sollte NICHT sawToSwapout haben");
    }

    @Test
    @DisplayName("MITTEL-Barren (4300-7400mm) wird korrekt erkannt")
    void testMittelBarrenTypErkennung() {
        // Given: Barren mit 5500mm Laenge (im MITTEL-Bereich)
        int length = 5500;

        // When: Barrentyp ermitteln
        Optional<IngotTypeDTO> result = ingotTypeService.determineIngotType(length, null, null, null, null);

        // Then: MITTEL-Typ erwartet mit speziellen Eigenschaften
        assertTrue(result.isPresent(), "Barrentyp sollte gefunden werden");
        assertEquals("MITTEL", result.get().getName());
        assertEquals(LengthType.MEDIUM, result.get().getLengthType());

        // MITTEL-spezifische Flags pruefen
        assertFalse(result.get().getInternalAllowed(), "MITTEL sollte NICHT intern erlaubt sein");
        assertTrue(result.get().getExternalAllowed(), "MITTEL sollte extern erlaubt sein");
        assertTrue(result.get().getSawToSwapout(), "MITTEL sollte sawToSwapout=true haben");
        assertTrue(result.get().getAutoRetrieval(), "MITTEL sollte autoRetrieval=true haben");
    }

    @Test
    @DisplayName("LANG-Barren (7500-8700mm) wird korrekt erkannt")
    void testLangBarrenTypErkennung() {
        // Given: Barren mit 8000mm Laenge (im LANG-Bereich)
        int length = 8000;

        // When: Barrentyp ermitteln
        Optional<IngotTypeDTO> result = ingotTypeService.determineIngotType(length, null, null, null, null);

        // Then: LANG-Typ erwartet
        assertTrue(result.isPresent(), "Barrentyp sollte gefunden werden");
        assertEquals("LANG", result.get().getName());
        assertEquals(LengthType.LONG, result.get().getLengthType());
        assertTrue(result.get().getInternalAllowed(), "LANG sollte intern erlaubt sein");
        assertFalse(result.get().getSawToSwapout(), "LANG sollte NICHT sawToSwapout haben");
    }

    @Test
    @DisplayName("Grenzwerte zwischen Barrentypen werden korrekt behandelt")
    void testGrenzwerte() {
        // Untere Grenze KURZ (3500mm)
        Optional<IngotTypeDTO> kurz3500 = ingotTypeService.determineIngotType(3500, null, null, null, null);
        assertTrue(kurz3500.isPresent() && "KURZ".equals(kurz3500.get().getName()),
            "3500mm sollte KURZ sein");

        // Obere Grenze KURZ / Untere Grenze MITTEL (4300mm)
        // Hinweis: Bei Ueberlappung entscheidet die Prioritaet
        Optional<IngotTypeDTO> grenze4300 = ingotTypeService.determineIngotType(4300, null, null, null, null);
        assertTrue(grenze4300.isPresent(), "4300mm sollte einem Typ zugeordnet werden");

        // Obere Grenze MITTEL (7400mm)
        Optional<IngotTypeDTO> mittel7400 = ingotTypeService.determineIngotType(7400, null, null, null, null);
        assertTrue(mittel7400.isPresent() && "MITTEL".equals(mittel7400.get().getName()),
            "7400mm sollte MITTEL sein");

        // Untere Grenze LANG (7500mm)
        Optional<IngotTypeDTO> lang7500 = ingotTypeService.determineIngotType(7500, null, null, null, null);
        assertTrue(lang7500.isPresent() && "LANG".equals(lang7500.get().getName()),
            "7500mm sollte LANG sein");
    }

    // ========== 2. SWAPOUT-Platz Tests ==========

    @Test
    @DisplayName("SWAPOUT-Plaetze (00/01-00/08) existieren und haben korrekten Typ")
    void testSwapoutPlaetzeExistieren() {
        // Given: SWAPOUT-Plaetze in data-h2.sql definiert

        // When: Alle SWAPOUT-Plaetze laden
        List<Stockyard> swapoutYards = stockyardRepository.findByType("A");

        // Then: 8 SWAPOUT-Plaetze erwartet
        assertFalse(swapoutYards.isEmpty(), "SWAPOUT-Plaetze sollten existieren");
        assertEquals(8, swapoutYards.size(), "Es sollten 8 SWAPOUT-Plaetze existieren (00/01-00/08)");

        // Alle haben maxIngots=1
        for (Stockyard yard : swapoutYards) {
            assertEquals(1, yard.getMaxIngots(),
                "SWAPOUT-Platz " + yard.getYardNumber() + " sollte maxIngots=1 haben");
            assertTrue(yard.isToStockAllowed(),
                "SWAPOUT-Platz " + yard.getYardNumber() + " sollte toStockAllowed=true haben");
        }
    }

    @Test
    @DisplayName("Externe Lagerplaetze (YARD_TYPE=E) existieren")
    void testExternePlaetzeExistieren() {
        // When: Alle externen Plaetze laden
        List<Stockyard> externalYards = stockyardRepository.findByType("E");

        // Then: Externe Plaetze sollten existieren
        assertFalse(externalYards.isEmpty(), "Externe Lagerplaetze sollten existieren");

        // Mindestens 16/10, 17/10, 16/09, 17/09 aus Test-Daten
        assertTrue(externalYards.size() >= 4, "Mindestens 4 externe Plaetze erwartet");
    }

    // ========== 3. MITTEL-Barren Testdaten ==========

    @Test
    @DisplayName("MITTEL-Barren auf externen Plaetzen vorhanden (Test-Daten)")
    void testMittelBarrenAufExternenPlaetzen() {
        // Given: MITTEL-Barren in data-h2.sql auf externen Plaetzen

        // When: Barren auf externen Plaetzen zaehlen
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM TD_INGOT i " +
            "JOIN MD_STOCKYARD s ON i.STOCKYARD_ID = s.ID " +
            "WHERE s.YARD_TYPE = 'E' " +
            "AND i.LENGTH BETWEEN 4300 AND 7400",
            Integer.class);

        // Then: Mindestens einige MITTEL-Barren vorhanden
        assertNotNull(count);
        assertTrue(count >= 5, "Mindestens 5 MITTEL-Barren auf externen Plaetzen erwartet, gefunden: " + count);
    }

    @Test
    @DisplayName("MITTEL-Barren haben korrekte Laengen (5000-6100mm in Testdaten)")
    void testMittelBarrenLaengen() {
        // When: MITTEL-Barren-Laengen aus DB laden
        List<Integer> lengths = jdbcTemplate.queryForList(
            "SELECT DISTINCT LENGTH FROM TD_INGOT i " +
            "JOIN MD_STOCKYARD s ON i.STOCKYARD_ID = s.ID " +
            "WHERE s.YARD_TYPE = 'E'",
            Integer.class);

        // Then: Alle Laengen sollten im MITTEL-Bereich liegen
        for (Integer length : lengths) {
            assertTrue(length >= 4300 && length <= 7400,
                "Laenge " + length + " sollte im MITTEL-Bereich (4300-7400) liegen");
        }
    }

    // ========== 4. Workflow-Zusammenspiel Tests ==========

    @Test
    @DisplayName("IngotTypeService gibt korrekten LengthType zurueck")
    void testDetermineLengthType() {
        // KURZ
        assertEquals(LengthType.SHORT, ingotTypeService.determineLengthType(4000, null, null, null, null));

        // MITTEL
        assertEquals(LengthType.MEDIUM, ingotTypeService.determineLengthType(5500, null, null, null, null));

        // LANG
        assertEquals(LengthType.LONG, ingotTypeService.determineLengthType(8000, null, null, null, null));
    }

    @Test
    @DisplayName("Alle drei Barrentypen sind korrekt konfiguriert")
    void testBarrentypenKonfiguration() {
        List<IngotTypeDTO> allTypes = ingotTypeService.findAll();

        // Mindestens 3 Typen erwartet
        assertTrue(allTypes.size() >= 3, "Mindestens 3 Barrentypen erwartet");

        // KURZ pruefen
        IngotTypeDTO kurz = findByName(allTypes, "KURZ");
        assertNotNull(kurz, "KURZ-Typ sollte existieren");
        assertEquals(3500, kurz.getMinLength());
        assertEquals(4300, kurz.getMaxLength());

        // MITTEL pruefen
        IngotTypeDTO mittel = findByName(allTypes, "MITTEL");
        assertNotNull(mittel, "MITTEL-Typ sollte existieren");
        assertEquals(4300, mittel.getMinLength());
        assertEquals(7400, mittel.getMaxLength());
        assertTrue(mittel.getSawToSwapout(), "MITTEL.sawToSwapout sollte true sein");
        assertTrue(mittel.getAutoRetrieval(), "MITTEL.autoRetrieval sollte true sein");
        assertFalse(mittel.getInternalAllowed(), "MITTEL.internalAllowed sollte false sein");

        // LANG pruefen
        IngotTypeDTO lang = findByName(allTypes, "LANG");
        assertNotNull(lang, "LANG-Typ sollte existieren");
        assertEquals(7500, lang.getMinLength());
        assertEquals(8700, lang.getMaxLength());
    }

    private IngotTypeDTO findByName(List<IngotTypeDTO> types, String name) {
        return types.stream()
            .filter(t -> name.equals(t.getName()))
            .findFirst()
            .orElse(null);
    }

    // ========== 5. SWAPIN-Plaetze Tests ==========

    @Test
    @DisplayName("SWAPIN-Plaetze (YARD_TYPE=N) fuer Auto-Auslagerung existieren")
    void testSwapinPlaetzeExistieren() {
        // When: Alle SWAPIN-Plaetze laden
        List<Stockyard> swapinYards = stockyardRepository.findByType("N");

        // Then: SWAPIN-Plaetze sollten existieren (fuer Auto-Retrieval Ziel)
        assertFalse(swapinYards.isEmpty(), "SWAPIN-Plaetze sollten existieren");

        for (Stockyard yard : swapinYards) {
            assertTrue(yard.isToStockAllowed(),
                "SWAPIN-Platz " + yard.getYardNumber() + " sollte toStockAllowed=true haben");
        }
    }
}
