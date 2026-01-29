package com.hydro.plsbl.service;

import com.hydro.plsbl.entity.enums.LengthType;
import com.hydro.plsbl.entity.masterdata.IngotType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-Test fuer Barrentyp-Matching Logik.
 * Testet ohne Spring-Kontext, verwendet aber die gleiche Matching-Logik
 * wie IngotTypeService.matches() (Laenge, Breite, Dicke, Gewicht, Produkt-Regex).
 */
@DisplayName("Barrentyp Matching Tests")
class IngotTypeMatchingTest {

    private List<IngotType> barrentypen;

    @BeforeEach
    void setUp() {
        // KURZ: 3500-4299mm, intern erlaubt, sawToSwapout=false
        IngotType kurz = new IngotType();
        kurz.setId(1L);
        kurz.setName("KURZ");
        kurz.setLengthTypeCode("S");
        kurz.setMinLength(3500);
        kurz.setMaxLength(4299);
        kurz.setInternalAllowed(true);
        kurz.setExternalAllowed(true);
        kurz.setSawToSwapout(false);
        kurz.setAutoRetrieval(false);
        kurz.setPriority(0);

        // MITTEL: 4300-7400mm, nur extern, sawToSwapout=true, autoRetrieval=true
        IngotType mittel = new IngotType();
        mittel.setId(2L);
        mittel.setName("MITTEL");
        mittel.setLengthTypeCode("M");
        mittel.setMinLength(4300);
        mittel.setMaxLength(7400);
        mittel.setInternalAllowed(false);
        mittel.setExternalAllowed(true);
        mittel.setSawToSwapout(true);
        mittel.setAutoRetrieval(true);
        mittel.setPriority(0);

        // LANG: 7500-8700mm, intern erlaubt
        IngotType lang = new IngotType();
        lang.setId(3L);
        lang.setName("LANG");
        lang.setLengthTypeCode("L");
        lang.setMinLength(7500);
        lang.setMaxLength(8700);
        lang.setInternalAllowed(true);
        lang.setExternalAllowed(true);
        lang.setSawToSwapout(false);
        lang.setAutoRetrieval(false);
        lang.setPriority(0);

        barrentypen = Arrays.asList(kurz, mittel, lang);
    }

    @Test
    @DisplayName("KURZ-Barren wird bei 4000mm erkannt")
    void testKurzMatching() {
        Optional<IngotType> match = findMatchingType(4000, null, null, null, null);
        assertTrue(match.isPresent());
        assertEquals("KURZ", match.get().getName());
        assertEquals(LengthType.SHORT, match.get().getLengthType());
    }

    @Test
    @DisplayName("MITTEL-Barren wird bei 5500mm erkannt")
    void testMittelMatching() {
        Optional<IngotType> match = findMatchingType(5500, null, null, null, null);
        assertTrue(match.isPresent());
        assertEquals("MITTEL", match.get().getName());
        assertEquals(LengthType.MEDIUM, match.get().getLengthType());
    }

    @Test
    @DisplayName("LANG-Barren wird bei 8000mm erkannt")
    void testLangMatching() {
        Optional<IngotType> match = findMatchingType(8000, null, null, null, null);
        assertTrue(match.isPresent());
        assertEquals("LANG", match.get().getName());
        assertEquals(LengthType.LONG, match.get().getLengthType());
    }

    @Test
    @DisplayName("MITTEL hat sawToSwapout=true")
    void testMittelSawToSwapout() {
        Optional<IngotType> mittel = findMatchingType(5500, null, null, null, null);
        assertTrue(mittel.isPresent());
        assertTrue(mittel.get().getSawToSwapout(), "MITTEL sollte sawToSwapout=true haben");
    }

    @Test
    @DisplayName("MITTEL hat autoRetrieval=true")
    void testMittelAutoRetrieval() {
        Optional<IngotType> mittel = findMatchingType(5500, null, null, null, null);
        assertTrue(mittel.isPresent());
        assertTrue(mittel.get().getAutoRetrieval(), "MITTEL sollte autoRetrieval=true haben");
    }

    @Test
    @DisplayName("MITTEL hat internalAllowed=false")
    void testMittelInternalNotAllowed() {
        Optional<IngotType> mittel = findMatchingType(5500, null, null, null, null);
        assertTrue(mittel.isPresent());
        assertFalse(mittel.get().getInternalAllowed(), "MITTEL sollte internalAllowed=false haben");
    }

    @Test
    @DisplayName("KURZ hat sawToSwapout=false")
    void testKurzNoSwapout() {
        Optional<IngotType> kurz = findMatchingType(4000, null, null, null, null);
        assertTrue(kurz.isPresent());
        assertFalse(kurz.get().getSawToSwapout(), "KURZ sollte sawToSwapout=false haben");
        assertTrue(kurz.get().getInternalAllowed(), "KURZ sollte internalAllowed=true haben");
    }

    @Test
    @DisplayName("LANG hat sawToSwapout=false")
    void testLangNoSwapout() {
        Optional<IngotType> lang = findMatchingType(8000, null, null, null, null);
        assertTrue(lang.isPresent());
        assertFalse(lang.get().getSawToSwapout(), "LANG sollte sawToSwapout=false haben");
        assertTrue(lang.get().getInternalAllowed(), "LANG sollte internalAllowed=true haben");
    }

    @Test
    @DisplayName("Grenzwert 4300mm wird deterministisch als MITTEL erkannt")
    void testGrenzwert4300() {
        // KURZ endet bei 4299, MITTEL beginnt bei 4300 - keine Ueberlappung
        Optional<IngotType> match = findMatchingType(4300, null, null, null, null);
        assertTrue(match.isPresent());
        assertEquals("MITTEL", match.get().getName(), "4300mm sollte eindeutig MITTEL sein");
    }

    @Test
    @DisplayName("Grenzwert 4299mm wird als KURZ erkannt (obere Grenze)")
    void testGrenzwert4299() {
        Optional<IngotType> match = findMatchingType(4299, null, null, null, null);
        assertTrue(match.isPresent());
        assertEquals("KURZ", match.get().getName(), "4299mm sollte KURZ sein");
    }

    @Test
    @DisplayName("Grenzwert 7400mm wird als MITTEL erkannt (obere Grenze)")
    void testGrenzwert7400() {
        Optional<IngotType> match = findMatchingType(7400, null, null, null, null);
        assertTrue(match.isPresent());
        assertEquals("MITTEL", match.get().getName());
    }

    @Test
    @DisplayName("Grenzwert 7500mm wird als LANG erkannt")
    void testGrenzwert7500() {
        Optional<IngotType> match = findMatchingType(7500, null, null, null, null);
        assertTrue(match.isPresent());
        assertEquals("LANG", match.get().getName());
    }

    @Test
    @DisplayName("Luecke 7401-7499mm findet keinen Barrentyp")
    void testLueckeKeinMatch() {
        // Zwischen MITTEL (max 7400) und LANG (min 7500) liegt eine Luecke
        Optional<IngotType> match7401 = findMatchingType(7401, null, null, null, null);
        assertTrue(match7401.isEmpty(), "7401mm sollte keinem Barrentyp entsprechen (Luecke)");

        Optional<IngotType> match7450 = findMatchingType(7450, null, null, null, null);
        assertTrue(match7450.isEmpty(), "7450mm sollte keinem Barrentyp entsprechen (Luecke)");

        Optional<IngotType> match7499 = findMatchingType(7499, null, null, null, null);
        assertTrue(match7499.isEmpty(), "7499mm sollte keinem Barrentyp entsprechen (Luecke)");
    }

    @Test
    @DisplayName("Laenge ausserhalb aller Bereiche (3000mm) findet keinen Typ")
    void testKeinMatch() {
        Optional<IngotType> match = findMatchingType(3000, null, null, null, null);
        assertTrue(match.isEmpty(), "3000mm sollte keinem Barrentyp entsprechen");
    }

    @Test
    @DisplayName("Laenge ausserhalb aller Bereiche (9000mm) findet keinen Typ")
    void testKeinMatchOben() {
        Optional<IngotType> match = findMatchingType(9000, null, null, null, null);
        assertTrue(match.isEmpty(), "9000mm sollte keinem Barrentyp entsprechen");
    }

    @Test
    @DisplayName("Workflow: MITTEL-Barren von Saege muss zu SWAPOUT")
    void testMittelWorkflowSwapout() {
        Optional<IngotType> typeOpt = findMatchingType(5500, null, null, null, null);

        assertTrue(typeOpt.isPresent(), "Barrentyp sollte gefunden werden");
        IngotType type = typeOpt.get();
        assertEquals("MITTEL", type.getName());

        boolean goToSwapout = type.getSawToSwapout() != null && type.getSawToSwapout();
        boolean canGoInternal = type.getInternalAllowed() != null && type.getInternalAllowed();

        assertTrue(goToSwapout, "MITTEL-Barren sollte zu SWAPOUT gehen");
        assertFalse(canGoInternal, "MITTEL-Barren darf NICHT intern gelagert werden");
    }

    @Test
    @DisplayName("Workflow: KURZ-Barren von Saege kann intern gelagert werden")
    void testKurzWorkflowIntern() {
        Optional<IngotType> typeOpt = findMatchingType(4000, null, null, null, null);

        assertTrue(typeOpt.isPresent(), "Barrentyp sollte gefunden werden");
        IngotType type = typeOpt.get();
        assertEquals("KURZ", type.getName());

        boolean goToSwapout = type.getSawToSwapout() != null && type.getSawToSwapout();
        boolean canGoInternal = type.getInternalAllowed() != null && type.getInternalAllowed();

        assertFalse(goToSwapout, "KURZ-Barren sollte NICHT zu SWAPOUT gehen");
        assertTrue(canGoInternal, "KURZ-Barren darf intern gelagert werden");
    }

    @Test
    @DisplayName("Workflow: LANG-Barren von Saege kann intern gelagert werden")
    void testLangWorkflowIntern() {
        Optional<IngotType> typeOpt = findMatchingType(8000, null, null, null, null);

        assertTrue(typeOpt.isPresent(), "Barrentyp sollte gefunden werden");
        IngotType type = typeOpt.get();
        assertEquals("LANG", type.getName());

        boolean goToSwapout = type.getSawToSwapout() != null && type.getSawToSwapout();
        boolean canGoInternal = type.getInternalAllowed() != null && type.getInternalAllowed();

        assertFalse(goToSwapout, "LANG-Barren sollte NICHT zu SWAPOUT gehen");
        assertTrue(canGoInternal, "LANG-Barren darf intern gelagert werden");
    }

    /**
     * Matching-Logik analog zu IngotTypeService.matches().
     * Prueft Laenge, Breite, Dicke, Gewicht und Produkt-Regex.
     */
    private Optional<IngotType> findMatchingType(Integer length, Integer width, Integer thickness,
                                                  Integer weight, String productNo) {
        for (IngotType type : barrentypen) {
            if (matches(type, length, width, thickness, weight, productNo)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    private boolean matches(IngotType type, Integer length, Integer width, Integer thickness,
                            Integer weight, String productNo) {
        if (length != null) {
            if (type.getMinLength() != null && length < type.getMinLength()) return false;
            if (type.getMaxLength() != null && length > type.getMaxLength()) return false;
        }
        if (width != null) {
            if (type.getMinWidth() != null && width < type.getMinWidth()) return false;
            if (type.getMaxWidth() != null && width > type.getMaxWidth()) return false;
        }
        if (thickness != null) {
            if (type.getMinThickness() != null && thickness < type.getMinThickness()) return false;
            if (type.getMaxThickness() != null && thickness > type.getMaxThickness()) return false;
        }
        if (weight != null) {
            if (type.getMinWeight() != null && weight < type.getMinWeight()) return false;
            if (type.getMaxWeight() != null && weight > type.getMaxWeight()) return false;
        }
        if (productNo != null && type.getProductRegex() != null && !type.getProductRegex().isEmpty()) {
            try {
                if (!Pattern.compile(type.getProductRegex()).matcher(productNo).matches()) return false;
            } catch (Exception e) {
                // invalid regex - skip check
            }
        }
        return true;
    }
}
