package com.hydro.plsbl.ui.component;

import com.hydro.plsbl.dto.StockyardDTO;
import com.hydro.plsbl.dto.StockyardStatusDTO;
import com.hydro.plsbl.entity.enums.StockyardType;
import com.hydro.plsbl.entity.enums.StockyardUsage;
import com.hydro.plsbl.service.SettingsService;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;

/**
 * Lager-Grid Komponente
 *
 * Zeigt alle Lagerplätze als interaktives Grid an.
 * Basiert auf dem Original-Layout aus Lager.png und Auslagerplätze.png.
 *
 * Layout-Struktur:
 * - Säge: oben rechts, außerhalb des Zauns
 * - Hauptlager: 17x10 Grid (X=17 links bis X=1 rechts, Y=10 oben bis Y=1 unten)
 * - Auslagerplätze: A1-A8 rechts vom Zaun
 * - Zaun: Rechteckig mit L-förmigem Ausschnitt unten rechts
 * - Beladungsfläche: Im L-Ausschnitt mit Trailer und Schranken
 */
public class LagerGrid extends Div {

    private static final Logger log = LoggerFactory.getLogger(LagerGrid.class);

    private final Map<Long, StockyardButton> buttonMap = new HashMap<>();
    private Map<Long, StockyardDTO> allStockyardsMap = new HashMap<>();  // Alle Stockyards inkl. versteckter
    private Consumer<StockyardDTO> clickListener;
    private Runnable trailerClickListener;
    private SettingsService settingsService;

    // Trailer-Referenzen für dynamische Updates
    private Div trailerContainer;
    private Div trailerLadeflaeche;
    private Span trailerCountLabel;
    private int currentLoadedCount = 0;

    // Grid-Konfiguration (basierend auf Original)
    private int columns = 17;  // X-Koordinaten 17-1
    private int rows = 10;     // Y-Koordinaten 10-1
    private static final int CELL_WIDTH = 75;   // Breite für rechteckige Barren
    private static final int CELL_HEIGHT = 38;  // Höhe für rechteckige Barren
    private static final int GAP = 3;
    private static final int FENCE_WIDTH = 6;
    private static final int FENCE_GAP = 12;  // Abstand zwischen Zaun und Plätzen

    // Säge-Lichtschranke für dynamische Updates
    private Div sawLichtschranke;
    private Long sawStockyardId;

    // Ziel-Lagerplatz für Einlagerung (rote Umrandung)
    private Long targetStockyardId;

    // Kran-Komponenten
    private CraneOverlay craneOverlay;
    private CranePositionDisplay cranePositionDisplay;
    private int craneGridX = 4;  // Standard-Position X
    private int craneGridY = 9;  // Standard-Position Y
    private int craneBaseGridX = 9;  // Basis-Position für Animation (Mitte)
    private int craneBaseGridY = 5;  // Basis-Position für Animation (Mitte)
    private boolean craneVisible = true;

    // Lager-Grenzen für mm-zu-Pixel Umrechnung (dynamisch aus Stockyards berechnet)
    private int stockMinX = 9000;    // Default, wird überschrieben
    private int stockMaxX = 97000;
    private int stockMinY = 11000;
    private int stockMaxY = 37000;

    // Aktuelle Kran-Position in mm (für direkte Positionierung)
    private int craneMmX = 27000;
    private int craneMmY = 18000;

    /**
     * Berechnet die Lager-Grenzen dynamisch aus INTERNAL und SAW Stockyards
     *
     * Koordinatensystem (aus Oracle):
     * - X: 96740 (17/xx, links im UI) bis 9300 (Säge, rechts im UI)
     * - Y: 6270 (xx/01, unten) bis 35630 (xx/10, oben)
     *
     * AUSGANG-Plätze (00/01-00/08) werden NICHT einbezogen, da sie
     * dieselben X-Koordinaten wie die Säge haben aber separat dargestellt werden.
     */
    private void calculateStockBounds(Collection<StockyardDTO> stockyards) {
        if (stockyards == null || stockyards.isEmpty()) {
            log.warn("Keine Stockyards für Grenzen-Berechnung vorhanden, nutze Defaults");
            return;
        }

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int count = 0;

        for (StockyardDTO yard : stockyards) {
            // INTERNAL und SAW Plätze für die Grenzen-Berechnung verwenden
            // AUSGANG wird ausgeschlossen (hat gleiche X wie SAW, wird separat dargestellt)
            StockyardType type = yard.getType();
            if (type != StockyardType.INTERNAL && type != StockyardType.SAW) {
                continue;
            }

            Integer x = yard.getXPosition();  // BOTTOM_CENTER_X
            Integer y = yard.getYPosition();  // BOTTOM_CENTER_Y

            if (x != null && x > 0) {
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
                count++;
            }
            if (y != null && y > 0) {
                if (y < minY) minY = y;
                if (y > maxY) maxY = y;
            }
        }

        // Nur aktualisieren wenn gültige Werte gefunden wurden
        if (minX != Integer.MAX_VALUE && maxX != Integer.MIN_VALUE) {
            // Etwas Rand hinzufügen für bessere Darstellung
            this.stockMinX = minX - 1000;
            this.stockMaxX = maxX + 1000;
            log.info("Stock bounds X: {} - {} (aus {} Stockyards, inkl. SAW)", stockMinX, stockMaxX, count);
        }

        if (minY != Integer.MAX_VALUE && maxY != Integer.MIN_VALUE) {
            // Etwas Rand hinzufügen für bessere Darstellung
            this.stockMinY = minY - 1000;
            this.stockMaxY = maxY + 1000;
            log.info("Stock bounds Y: {} - {} (aus {} Stockyards, inkl. SAW)", stockMinY, stockMaxY, count);
        }
    }

    public LagerGrid() {
        this(null);
    }

    public LagerGrid(SettingsService settingsService) {
        this.settingsService = settingsService;
        addClassName("lager-grid");
        getStyle()
            .set("display", "grid")
            .set("gap", GAP + "px")
            .set("padding", "15px")
            .set("width", "fit-content")
            .set("background-color", "#f5f5f5");

        updateGridTemplate();
    }

    /**
     * Setzt den SettingsService (für Farb-Konfiguration)
     */
    public void setSettingsService(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    private void updateGridTemplate() {
        // Spalten-Layout (von links nach rechts) - 28 Spalten:
        // 1: Linker Zaun (6px)
        // 2: Abstand (12px)
        // 3: Y-Achsen-Labels (25px)
        // 4-20: Lagerplätze X=17 bis X=1 (17 Spalten à 75px)
        // 21: Schrott-Plätze 00/01-00/08 (75px) - rechts neben X=1
        // 22: Abstand (12px)
        // 23: Säge-Bereich / Personen-Tor (70px)
        // 24: Abstand (6px)
        // 25: Kleiner Zaun zwischen Toren (6px)
        // 26: Barren-Tor (20px)
        // 27: Rechter Zaun (6px)

        // Reihen-Layout (von oben nach unten):
        // 1: Säge-Bereich oben (55px)
        // 2: Oberer Zaun (6px)
        // 3: Abstand (12px)
        // 4: X-Achsen-Labels (20px)
        // 5-14: Lagerplätze Y=10 bis Y=1 (10 Reihen à 45px)
        // 15: Abstand (12px)
        // 16: Gemeinsamer Zaun ab Spalte 12 (6px)
        // 17: Plätze 17/01-11/01 + Beladung oben (45px)
        // 18: Zaun unter 17/01-11/01 (6px)
        // 19: Beladung unten (45px)
        // 20: Unterer Zaun Beladung (6px)

        // Spalten-Layout: Ausgangs-Plätze (00/01-00/08) sind Spalte 22, mit Abstand zu X=1
        String colTemplate = FENCE_WIDTH + "px " +      // 1: Linker Zaun
                            FENCE_GAP + "px " +          // 2: Abstand
                            "25px " +                    // 3: Y-Labels
                            "repeat(17, " + CELL_WIDTH + "px) " +  // 4-20: Lager (X=17 bis X=1)
                            FENCE_GAP + "px " +          // 21: Abstand nach X=1
                            CELL_WIDTH + "px " +         // 22: Ausgangs-Plätze 00/01-00/08
                            FENCE_GAP + "px " +          // 23: Abstand
                            "70px " +                    // 24: Säge / Personen-Tor
                            FENCE_GAP + "px " +          // 25: Abstand
                            FENCE_WIDTH + "px " +        // 26: Kleiner Zaun zwischen Toren
                            "20px " +                    // 27: Barren-Tor
                            FENCE_WIDTH + "px";          // 28: Rechter Zaun

        // Reihen-Layout (von oben nach unten):
        // 1: Säge-Bereich oben (55px)
        // 2: Oberer Zaun (6px)
        // 3: Abstand (12px)
        // 4: X-Achsen-Labels (20px)
        // 5-14: Lagerplätze Y=10 bis Y=2 (10 Reihen à 45px) - Y=1 normal nur für X=10-1
        // 15: Abstand (12px)
        // 16: Gemeinsamer Zaun ab Spalte 11 (6px)
        // 17: Plätze 17/01-11/01 + Beladung oben (45px)
        // 18: Abstand unter 17/01-11/01 (12px)
        // 19: Zaun unter 17/01-11/01 (6px)
        // 20: Beladung unten (45px)
        // 21: Unterer Zaun Beladung (6px)

        String rowTemplate = "55px " +                   // 1: Säge
                            FENCE_WIDTH + "px " +        // 2: Oberer Zaun
                            FENCE_GAP + "px " +          // 3: Abstand
                            "20px " +                    // 4: X-Labels
                            "repeat(" + rows + ", " + CELL_HEIGHT + "px) " +  // 5-14: Lager
                            FENCE_GAP + "px " +          // 15: Abstand
                            FENCE_WIDTH + "px " +        // 16: Gemeinsamer Zaun (ab Spalte 11)
                            CELL_HEIGHT + "px " +        // 17: Plätze 17/01-11/01 + Beladung oben
                            FENCE_GAP + "px " +          // 18: Abstand unter 17/01-11/01
                            FENCE_WIDTH + "px " +        // 19: Zaun unter 17/01-11/01
                            CELL_HEIGHT + "px " +        // 20: Beladung unten
                            FENCE_WIDTH + "px";          // 21: Unterer Zaun Beladung

        getStyle()
            .set("grid-template-columns", colTemplate)
            .set("grid-template-rows", rowTemplate);
    }
    
    /**
     * Setzt die Stockyards und baut das Grid auf
     */
    public void setStockyards(Map<Long, StockyardDTO> stockyards) {
        removeAll();
        buttonMap.clear();

        if (stockyards.isEmpty()) {
            add(new Span("Keine Lagerplätze gefunden"));
            return;
        }

        // Alle Stockyards speichern (für LONG-Suche bei Markierungen)
        allStockyardsMap = new HashMap<>(stockyards);

        // Lager-Grenzen dynamisch aus Stockyards berechnen (wie im Original)
        calculateStockBounds(stockyards.values());

        // Grid-Dimensionen ermitteln (SAW-Plätze ausschließen, da außerhalb des Haupt-Grids)
        int maxX = stockyards.values().stream()
            .filter(y -> y.getType() != StockyardType.SAW)
            .mapToInt(StockyardDTO::getXCoordinate)
            .max().orElse(17);
        int maxY = stockyards.values().stream()
            .filter(y -> y.getType() != StockyardType.SAW)
            .mapToInt(StockyardDTO::getYCoordinate)
            .max().orElse(10);

        this.columns = maxX;
        this.rows = maxY;
        updateGridTemplate();

        log.debug("Building grid {}x{}", columns, rows);

        // X-Achsen-Labels (oben, Reihe 4) - X=17 links bis X=1 rechts
        // Einheitliche Spaltenberechnung: gridCol = columns - x + 4
        for (int x = columns; x >= 1; x--) {
            Span label = new Span(String.valueOf(x));
            int gridCol = columns - x + 4;  // X=17 → Spalte 4, X=1 → Spalte 20
            label.getStyle()
                .set("grid-column", String.valueOf(gridCol))
                .set("grid-row", "4")
                .set("text-align", "center")
                .set("font-weight", "bold")
                .set("font-size", "11px")
                .set("color", "#555")
                .set("align-self", "center");
            add(label);
        }

        // Y-Achsen-Labels (links, Spalte 3) - Y=10 oben bis Y=1 unten
        for (int y = rows; y >= 1; y--) {
            // Label "11" ausblenden (nicht benötigt)
            if (y == 11) {
                continue;
            }

            Span label = new Span(String.format("%02d", y));
            int gridRow = rows - y + 5;  // Reihe 5-14

            // Sonderfall: Label "01" wird nach Reihe 17 verschoben (zusammen mit Plätzen 17/01-11/01)
            if (y == 1) {
                gridRow = 17;
            }

            label.getStyle()
                .set("grid-column", "3")
                .set("grid-row", String.valueOf(gridRow))
                .set("text-align", "right")
                .set("padding-right", "5px")
                .set("font-weight", "bold")
                .set("font-size", "11px")
                .set("color", "#555")
                .set("line-height", CELL_HEIGHT + "px");
            add(label);
        }

        // Stockyard-Buttons platzieren
        StockyardDTO sawYard = null;  // Säge-Position separat behandeln

        for (StockyardDTO yard : stockyards.values()) {
            // Säge-Position separat speichern
            if (yard.getType() == StockyardType.SAW) {
                sawYard = yard;
                continue;  // Nicht im normalen Grid platzieren
            }

            StockyardButton button = new StockyardButton(yard, settingsService);

            // Click-Handler
            button.addClickListener(e -> {
                if (clickListener != null) {
                    clickListener.accept(yard);
                }
            });

            // Grid-Position berechnen
            int xCoord = yard.getXCoordinate();
            int gridCol;
            int gridRow;

            // Sonderfall: Ausgangs-Lagerplätze 00/01 bis 00/08 (X=0) -> Spalte 24 (unter der Säge)
            // Direkt unter der Säge, auf Höhe von Y+1 (00/01 auf Höhe von 01/02, etc.)
            if (xCoord == 0) {
                gridCol = 24;  // Spalte 24: direkt unter der Säge
                gridRow = rows - yard.getYCoordinate() + 4;  // 1 Reihe höher (Y+1)
            } else {
                // Normale Berechnung für X=1 bis X=17
                gridCol = columns - xCoord + 4;  // X=17 → Spalte 4, X=1 → Spalte 20
                gridRow = rows - yard.getYCoordinate() + 5;  // Y=10 -> Reihe 5, Y=1 -> Reihe 14

                // Sonderfall: Plätze 17/01 bis 11/01 (Y=1, X>=11) werden nach Reihe 17 verschoben
                if (yard.getYCoordinate() == 1 && xCoord >= 11) {
                    gridRow = 17;  // Nach unten verschieben
                }
            }

            // LONG Lagerplätze über 2 Spalten spannen
            boolean isLong = yard.getUsage() == StockyardUsage.LONG;
            if (isLong) {
                // LONG: Span über 2 Spalten (nach rechts, also niedrigere X-Koordinaten)
                button.getStyle()
                    .set("grid-column", gridCol + " / " + (gridCol + 2))
                    .set("grid-row", String.valueOf(gridRow));
            } else {
                button.getStyle()
                    .set("grid-column", String.valueOf(gridCol))
                    .set("grid-row", String.valueOf(gridRow));
            }

            add(button);
            buttonMap.put(yard.getId(), button);
        }

        log.info("Grid built with {} buttons", buttonMap.size());

        // Säge-Position als spezieller Button behandeln
        final StockyardDTO finalSawYard = sawYard;

        // Zusätzliche visuelle Elemente hinzufügen
        addZaun();
        addSaegeausgang(finalSawYard);
        addAussenplaetze();
        addZusaetzlicheLagerplaetze();  // 00/01 bis 00/08
        addBeladungsflaeche();
        addCrane();
        addCranePositionDisplay();
    }

    /**
     * Fügt die Säge oben rechts hinzu, bis zur Zaun-Lücke reichend
     * Wenn ein Säge-Lagerplatz vorhanden ist, wird er als interaktiver Button angezeigt
     * Unten wird eine Lichtschranke angezeigt (grün = Einlagerung erlaubt)
     */
    private void addSaegeausgang(StockyardDTO sawYard) {
        // Säge-Container - Reihe 1-3, Spalte 23-25 (Gap + Säge + Abstand)
        Div saegeContainer = new Div();
        saegeContainer.addClassName("saege-container");
        saegeContainer.getStyle()
            .set("grid-column", "23 / 25")  // Säge-Bereich (Gap + Säge)
            .set("grid-row", "1 / 4")       // Nur bis zum Zaun (kürzer)
            .set("margin-left", "-20px")    // Feineinstellung nach links
            .set("display", "flex")
            .set("flex-direction", "column")
            .set("align-items", "center")
            .set("justify-content", "flex-start")
            .set("gap", "4px");

        if (sawYard != null) {
            // Interaktiver Säge-Button mit Barren-Anzeige
            StockyardButton sawButton = new StockyardButton(sawYard, settingsService);
            sawButton.getStyle()
                .set("width", "65px")
                .set("flex", "1")
                .set("border", "2px solid #0D47A1")
                .set("background", "linear-gradient(135deg, #1565C0 0%, #42A5F5 100%)")
                .set("color", "white");

            // Click-Handler
            sawButton.addClickListener(e -> {
                if (clickListener != null) {
                    clickListener.accept(sawYard);
                }
            });

            saegeContainer.add(sawButton);
            buttonMap.put(sawYard.getId(), sawButton);

            // Lichtschranke unten - grün wenn Barren auf der Säge liegt (bereit zur Einlagerung)
            sawLichtschranke = new Div();
            sawStockyardId = sawYard.getId();
            boolean hasIngot = sawYard.getStatus() != null && sawYard.getStatus().getIngotsCount() > 0;
            updateLichtschrankeStyle(hasIngot);
            saegeContainer.add(sawLichtschranke);

            // Debug-Info
            String ingotInfo = sawYard.getStatus() != null && sawYard.getStatus().getIngotNumber() != null
                ? sawYard.getStatus().getIngotNumber()
                : "kein Barren";
            int count = sawYard.getStatus() != null ? sawYard.getStatus().getIngotsCount() : 0;
            log.info("SAW position: {} - Barren: {}, Anzahl: {}, Lichtschranke: {}",
                sawYard.getYardNumber(), ingotInfo, count, hasIngot ? "GRÜN" : "ROT");
        } else {
            // Fallback: Statisches Säge-Symbol (kein Stockyard definiert)
            Div saege = new Div();
            saege.getStyle()
                .set("width", "65px")
                .set("flex", "1")
                .set("background", "linear-gradient(90deg, #1565C0 0%, #1565C0 15%, #90CAF9 15%, #90CAF9 30%, #1565C0 30%, #1565C0 45%, #90CAF9 45%, #90CAF9 60%, #1565C0 60%, #1565C0 75%, #90CAF9 75%, #90CAF9 90%, #1565C0 90%)")
                .set("border-radius", "4px")
                .set("border", "2px solid #0D47A1");
            saegeContainer.add(saege);

            // Lichtschranke (Standard: rot)
            Div lichtschranke = new Div();
            lichtschranke.getStyle()
                .set("width", "65px")
                .set("height", "8px")
                .set("border-radius", "4px")
                .set("background-color", "#F44336")
                .set("box-shadow", "0 0 8px #F44336, 0 0 12px #F44336");
            saegeContainer.add(lichtschranke);
        }

        add(saegeContainer);
    }

    /**
     * Fügt den blauen Zaun mit grünen Toren hinzu (basierend auf Lager.png)
     *
     * Zaun-Layout:
     * - Oberer Zaun: Reihe 2, von Spalte 1-22, mit Toren
     * - Linker Zaun: Spalte 1, von Reihe 2-16
     * - Rechter Zaun: Spalte 22, von Reihe 2-20 (HINTER den A-Plätzen), mit Tor zur Säge
     * - Unterer Zaun: L-förmig für Beladungsfläche (ab Platz 03/02)
     */
    private void addZaun() {
        // Spalten: 1=linker Zaun, 21=Gap, 22=Ausgangs-Plätze, 23=Gap, 24=Säge, 25-26=Abstand/Zaun, 27=Barren-Tor, 28=rechter Zaun

        // Beladungsfläche geht von X=7 (links) bis X=3 (rechts)
        // Einheitliche Spaltenberechnung: gridCol = columns - x + 4
        int beladungLeftCol = columns - 7 + 4;   // Spalte 14 (X=7, linker Rand Beladung)
        int lFormStartCol = columns - 3 + 4;     // Spalte 18 (X=3, vertikaler Zaun mit Toren)

        // ============ OBERER ZAUN (Reihe 2) mit Toren ============

        // Zaun links (Spalte 1-3)
        Div topFence1 = createFenceSegment();
        topFence1.getStyle()
            .set("grid-column", "1 / 4")
            .set("grid-row", "2");
        add(topFence1);

        // Tor 1 oben (Spalte 4) - oberhalb von Platz 17/10
        Div topGate1 = createNumberedGate(1, "Oben bei 17/10");
        topGate1.getStyle()
            .set("grid-column", "4")
            .set("grid-row", "2");
        add(topGate1);

        // Zaun Mitte (Spalte 5-10, über X=16 bis X=11)
        Div topFence2 = createFenceSegment();
        topFence2.getStyle()
            .set("grid-column", "5 / 11")
            .set("grid-row", "2");
        add(topFence2);

        // Tor 2 oben (Spalte 11) - oberhalb von Platz 10/10
        Div topGate2 = createNumberedGate(2, "Oben bei 10/10");
        topGate2.getStyle()
            .set("grid-column", "11")
            .set("grid-row", "2");
        add(topGate2);

        // Zaun rechts bis zu den Schrott-Plätzen (Spalte 12-20)
        Div topFence3 = createFenceSegment();
        topFence3.getStyle()
            .set("grid-column", "12 / 21")
            .set("grid-row", "2");
        add(topFence3);

        // Zaun vor Säge-Ausgang (Spalte 21-23)
        Div topFence3b = createFenceSegment();
        topFence3b.getStyle()
            .set("grid-column", "21 / 24")
            .set("grid-row", "2");
        add(topFence3b);

        // Säge-Ausgang und Ausgangs-Plätze (Spalte 24) - OFFEN für Säge/Kran

        // Zaun zwischen Säge-Ausgang und rechtem Zaun (Spalte 25-28)
        Div topFence4 = createFenceSegment();
        topFence4.getStyle()
            .set("grid-column", "25 / 29")
            .set("grid-row", "2");
        add(topFence4);

        // ============ LINKER ZAUN (Spalte 1) ============
        Div leftFence = createFenceSegment();
        leftFence.getStyle()
            .set("grid-column", "1")
            .set("grid-row", "2 / 20");
        add(leftFence);

        // Ecke unten-links
        Div cornerBottomLeft = createFenceSegment();
        cornerBottomLeft.getStyle()
            .set("grid-column", "1 / 3")
            .set("grid-row", "19");
        add(cornerBottomLeft);

        // ============ RECHTER ZAUN (Spalte 28) ============
        Div rightFence = createFenceSegment();
        rightFence.getStyle()
            .set("grid-column", "28")
            .set("grid-row", "2 / 17");
        add(rightFence);

        // ============ GEMEINSAMER ZAUN (Reihe 16) - beginnt bei Spalte 11 (rechts von 11/01) ============

        // Tor 4 bei Spalte 11 (über X=10, rechts von 11/01)
        Div torSpalte11 = createNumberedGate(4, "Mitte rechts von 11/01");
        torSpalte11.getStyle()
            .set("grid-column", "11")
            .set("grid-row", "16");
        add(torSpalte11);

        // Zaun von Spalte 12 bis zu den Schrott-Plätzen (Spalte 12-21)
        Div bottomFence2 = createFenceSegment();
        bottomFence2.getStyle()
            .set("grid-column", "12 / 21")
            .set("grid-row", "16");
        add(bottomFence2);

        // Zaun vor Säge (Spalte 22-23)
        Div bottomFence2b = createFenceSegment();
        bottomFence2b.getStyle()
            .set("grid-column", "22 / 24")
            .set("grid-row", "16");
        add(bottomFence2b);

        // Tor 5: Personen-Tor (Spalte 24 - bei Säge)
        Div personenTor = createNumberedGate(5, "Personen-Tor");
        personenTor.getStyle()
            .set("grid-column", "24")
            .set("grid-row", "16");
        add(personenTor);

        // Zaun zwischen Personen-Tor und Barren-Tor (Spalte 25-26)
        Div zaunZwischenToren = createFenceSegment();
        zaunZwischenToren.getStyle()
            .set("grid-column", "25 / 27")
            .set("grid-row", "16");
        add(zaunZwischenToren);

        // Tor 6: Barren-Tor (Spalte 27)
        Div barrenTor = createNumberedGate(6, "Barren-Tor");
        barrenTor.getStyle()
            .set("grid-column", "27")
            .set("grid-row", "16");
        add(barrenTor);

        // Rechter Zaun unten (Spalte 28)
        Div rightFenceBottom = createFenceSegment();
        rightFenceBottom.getStyle()
            .set("grid-column", "28")
            .set("grid-row", "16");
        add(rightFenceBottom);

        // ============ ZAUN UNTER PLÄTZEN 17/01-11/01 (Reihe 18) ============
        // Diese Plätze sind in Reihe 17, daher Zaun in Reihe 18

        // Zaun Spalte 2 bis 4 (linker Teil, bis zum Tor unter 17/01)
        Div bottomFenceShiftedLeft = createFenceSegment();
        bottomFenceShiftedLeft.getStyle()
            .set("grid-column", "2 / 5")
            .set("grid-row", "19")
            .set("margin-right", "20px");  // Platz für das Tor lassen
        add(bottomFenceShiftedLeft);

        // Tor 7 unter Platz 17/01 (Spalte 4) - 20px breit, rechts ausgerichtet
        Div torUnter1701 = createNumberedGate(7, "Unten bei 17/01");
        torUnter1701.getStyle()
            .set("grid-column", "4")
            .set("grid-row", "19")
            .set("width", "20px")
            .set("justify-self", "end");
        add(torUnter1701);

        // Zaun Spalte 5 bis 10 (rechter Teil, über X=16 bis X=11)
        Div bottomFenceShiftedRight = createFenceSegment();
        bottomFenceShiftedRight.getStyle()
            .set("grid-column", "5 / 11")
            .set("grid-row", "19");
        add(bottomFenceShiftedRight);

        // ============ VERTIKALER ZAUN RECHTS VON 11/01 (am Rand von Spalte 10/11, Reihe 17-19) ============
        // Verbindet den Zaun bei Reihe 16 mit dem Zaun bei Reihe 19
        Div verticalFenceRight1101 = createFenceSegment();
        verticalFenceRight1101.getStyle()
            .set("grid-column", "11")
            .set("grid-row", "17 / 20")
            .set("width", FENCE_WIDTH + "px")
            .set("justify-self", "start")
            .set("margin-top", "-4px")
            .set("height", "calc(100% + 4px)");
        add(verticalFenceRight1101);

        // ============ VERTIKALER ZAUN RECHTS DER BELADUNG (Spalte 19) mit Einfahrt ============
        // Diese Elemente sind in einer 55px Spalte, daher setzen wir die Breite explizit auf 6px

        // Zaun oben (Reihe 17) - 20px
        Div lVerticalTop = createFenceSegment();
        lVerticalTop.getStyle()
            .set("grid-column", String.valueOf(lFormStartCol))
            .set("grid-row", "17")
            .set("width", FENCE_WIDTH + "px")
            .set("height", "20px")
            .set("justify-self", "start")
            .set("align-self", "start");
        add(lVerticalTop);

        // Tor 8: Einfahrt-Schranke (Reihe 17-20) - 70px hoch
        Div einfahrtTor = createNumberedGate(8, "Einfahrt Beladung");
        einfahrtTor.getStyle()
            .set("grid-column", String.valueOf(lFormStartCol))
            .set("grid-row", "17 / 21")
            .set("width", FENCE_WIDTH + "px")
            .set("height", "70px")
            .set("justify-self", "start")
            .set("align-self", "center");
        add(einfahrtTor);

        // Unterer Teil des vertikalen Zauns (Reihe 20-21) - 20px
        Div lVerticalBottom = createFenceSegment();
        lVerticalBottom.getStyle()
            .set("grid-column", String.valueOf(lFormStartCol))
            .set("grid-row", "20 / 22")
            .set("width", FENCE_WIDTH + "px")
            .set("height", "20px")
            .set("justify-self", "start")
            .set("align-self", "end");
        add(lVerticalBottom);

        // ============ LINKER ZAUN DER BELADUNGSFLÄCHE (Spalte 14) mit Schranke zum Rausfahren ============
        int beladungFenceCol = beladungLeftCol - 1;  // Spalte 14 (links von der Beladungsfläche)

        // Zaun oben (Reihe 17) - 20px
        Div leftBeladungTop = createFenceSegment();
        leftBeladungTop.getStyle()
            .set("grid-column", String.valueOf(beladungFenceCol))
            .set("grid-row", "17")
            .set("width", FENCE_WIDTH + "px")
            .set("height", "20px")
            .set("justify-self", "end")
            .set("align-self", "start");
        add(leftBeladungTop);

        // Tor 9: Ausfahrt-Schranke (Reihe 17-20) - 70px hoch
        Div schranke = createNumberedGate(9, "Ausfahrt Beladung");
        schranke.getStyle()
            .set("grid-column", String.valueOf(beladungFenceCol))
            .set("grid-row", "17 / 21")
            .set("width", FENCE_WIDTH + "px")
            .set("height", "70px")
            .set("justify-self", "end")
            .set("align-self", "center");
        add(schranke);

        // Zaun unten (Reihe 20-21) - 20px
        Div leftBeladungBottom = createFenceSegment();
        leftBeladungBottom.getStyle()
            .set("grid-column", String.valueOf(beladungFenceCol))
            .set("grid-row", "20 / 22")
            .set("width", FENCE_WIDTH + "px")
            .set("height", "20px")
            .set("justify-self", "end")
            .set("align-self", "end");
        add(leftBeladungBottom);

        // ============ UNTERER ZAUN DER BELADUNGSFLÄCHE (Reihe 21) ============

        // Tor 10: Fahrer-Tor (links, auf Höhe der Zugkabine) - 20px breit
        Div fahrerTor = createNumberedGate(10, "Fahrer-Tor");
        fahrerTor.getStyle()
            .set("grid-column", String.valueOf(beladungLeftCol))
            .set("grid-row", "21")
            .set("width", "20px")
            .set("justify-self", "start");
        add(fahrerTor);

        // Zaun rechts vom Fahrer-Tor bis zum Ende der Beladungsfläche
        Div bottomFenceBeladung1 = createFenceSegment();
        bottomFenceBeladung1.getStyle()
            .set("grid-column", beladungLeftCol + " / " + lFormStartCol)
            .set("grid-row", "21")
            .set("margin-left", "20px");  // Platz für das Tor lassen
        add(bottomFenceBeladung1);

    }

    private Div createFenceSegment() {
        Div fence = new Div();
        fence.getStyle()
            .set("background-color", "#1565C0") // Blau
            .set("border-radius", "2px");
        return fence;
    }

    private Div createGate() {
        Div gate = new Div();
        gate.getStyle()
            .set("background-color", "#4CAF50") // Grün
            .set("border-radius", "2px");
        return gate;
    }

    /**
     * Erstellt ein nummeriertes Tor mit Label
     */
    private Div createNumberedGate(int number, String beschreibung) {
        Div gate = new Div();
        gate.getStyle()
            .set("background-color", "#4CAF50") // Grün
            .set("border-radius", "2px")
            .set("display", "flex")
            .set("align-items", "center")
            .set("justify-content", "center")
            .set("position", "relative");

        Span label = new Span("T" + number);
        label.getStyle()
            .set("font-size", "9px")
            .set("font-weight", "bold")
            .set("color", "white")
            .set("text-shadow", "0 0 2px black");
        gate.add(label);

        gate.getElement().setAttribute("title", "Tor " + number + ": " + beschreibung);
        return gate;
    }

    /**
     * Auslagerplätze (A1-A8) werden nicht separat hinzugefügt.
     * Sie sind normale Lagerplätze mit X-Koordinaten 1-3 und werden aus der Datenbank geladen.
     * Diese Methode ist ein Platzhalter und fügt keine Elemente hinzu.
     */
    private void addAussenplaetze() {
        // A1-A8 sind normale Stockyards mit X=1, X=2, X=3
        // Sie werden durch setStockyards() aus der Datenbank geladen
        // Keine zusätzlichen visuellen Elemente nötig
    }

    /**
     * Schrott-Lagerplätze (00/01 bis 00/08) werden jetzt direkt aus der Datenbank geladen.
     * Sie haben X_COORDINATE=0 und werden in Spalte 23 platziert (siehe setStockyards).
     * Diese Methode ist nur noch ein Platzhalter.
     */
    private void addZusaetzlicheLagerplaetze() {
        // 00/01 bis 00/08 werden jetzt aus der Datenbank geladen
        // und als echte StockyardButtons in Spalte 23 platziert
        log.debug("Schrott-Lagerplätze (00/01 - 00/08) werden aus Datenbank geladen");
    }

    /**
     * Fügt die Beladungsfläche mit Trailer hinzu
     * Die Beladungsfläche ist im L-Ausschnitt rechts unten
     * Von Platz 07/02 (links, Spalte 14) bis Platz 04/02 (rechts, Spalte 17)
     * Spalten 14-17, Reihen 17-20 (unterhalb des gemeinsamen Zauns bei Reihe 16)
     */
    private void addBeladungsflaeche() {
        // Beladungsfläche: von X=7 (Spalte 14) bis X=4 (Spalte 17)
        // Der Zaun mit Einfahrt/Ausfahrt ist bei X=3 (Spalte 18)
        // Einheitliche Spaltenberechnung: gridCol = columns - x + 4
        int beladungLeftCol = columns - 7 + 4;   // Spalte 14 (X=7)
        int beladungRightCol = columns - 4 + 4;  // Spalte 17 (X=4)

        // Beladungsfläche (grauer Bereich)
        // Reihe 17 (45px) + Reihe 18 (12px Abstand) + Reihe 19 (6px Zaun) + Reihe 20 (45px) = optisch zusammenhängend
        Div beladung = new Div();
        beladung.addClassName("beladungsflaeche");
        beladung.getStyle()
            .set("grid-column", beladungLeftCol + " / " + (beladungRightCol + 1))  // Spalten 14-17
            .set("grid-row", "17 / 21")  // Reihen 17-20
            .set("background-color", "#ECEFF1")
            .set("border-radius", "4px")
            .set("display", "flex")
            .set("flex-direction", "column")
            .set("align-items", "center")
            .set("justify-content", "center")
            .set("padding", "8px")
            .set("gap", "5px");

        // Trailer (Orange) - 20px tiefer gesetzt
        Div trailer = createTrailer();
        trailer.getStyle().set("margin-top", "20px");
        beladung.add(trailer);

        add(beladung);
    }

    private Div createTrailer() {
        // Trailer aus Vogelperspektive (wie die anderen Lagerplätze)
        // Kabine links, Ladefläche rechts (Trailer fährt nach links raus)
        // Mit 4 schwarzen Rädern
        trailerContainer = new Div();
        trailerContainer.addClassName("trailer");
        trailerContainer.getStyle()
            .set("position", "relative")
            .set("display", "flex")
            .set("flex-direction", "row")
            .set("align-items", "center")
            .set("gap", "0px")
            .set("cursor", "pointer");

        // Click-Handler für Trailer -> öffnet BeladungView
        trailerContainer.getElement().addEventListener("click", e -> {
            log.info("Trailer clicked");
            if (trailerClickListener != null) {
                trailerClickListener.run();
            }
        });

        // Zugmaschine (Kabine) - links
        Div kabine = new Div();
        kabine.getStyle()
            .set("width", "25px")
            .set("height", "50px")
            .set("background-color", "#FF9800")  // Orange
            .set("border-radius", "4px 0 0 4px")
            .set("border", "2px solid #E65100")
            .set("border-right", "none");

        // Ladefläche (Anhänger) - rechts, länger für Barren-Anzeige
        trailerLadeflaeche = new Div();
        trailerLadeflaeche.getStyle()
            .set("width", "140px")
            .set("height", "60px")
            .set("background-color", "#FFB74D")  // Heller Orange
            .set("border-radius", "0 4px 4px 0")
            .set("border", "2px solid #E65100")
            .set("display", "flex")
            .set("flex-direction", "column")
            .set("align-items", "center")
            .set("justify-content", "center")
            .set("gap", "2px");

        // Anzahl-Label für geladene Barren
        trailerCountLabel = new Span("Leer");
        trailerCountLabel.getStyle()
            .set("font-size", "11px")
            .set("font-weight", "bold")
            .set("color", "#E65100");
        trailerLadeflaeche.add(trailerCountLabel);

        trailerContainer.add(kabine, trailerLadeflaeche);

        // 4 schwarze Räder hinzufügen (gut verteilt an der Ladefläche)
        // Hintere Achse (am Ende der Ladefläche) - oben
        Div wheel1 = createWheel();
        wheel1.getStyle()
            .set("position", "absolute")
            .set("right", "20px")
            .set("top", "-8px");
        trailerContainer.add(wheel1);

        // Hintere Achse - unten
        Div wheel2 = createWheel();
        wheel2.getStyle()
            .set("position", "absolute")
            .set("right", "20px")
            .set("bottom", "-8px");
        trailerContainer.add(wheel2);

        // Vordere Achse (am Anfang der Ladefläche, nahe Kabine) - oben
        Div wheel3 = createWheel();
        wheel3.getStyle()
            .set("position", "absolute")
            .set("right", "115px")
            .set("top", "-8px");
        trailerContainer.add(wheel3);

        // Vordere Achse - unten
        Div wheel4 = createWheel();
        wheel4.getStyle()
            .set("position", "absolute")
            .set("right", "115px")
            .set("bottom", "-8px");
        trailerContainer.add(wheel4);

        return trailerContainer;
    }

    /**
     * Aktualisiert die Trailer-Anzeige mit der Anzahl geladener Barren
     * @param loadedCount Anzahl der geladenen Barren
     * @param totalCount Gesamtanzahl (geplant + geladen)
     * @param isLoading true wenn Beladung aktiv läuft
     */
    public void updateTrailerLoad(int loadedCount, int totalCount, boolean isLoading) {
        if (trailerLadeflaeche == null || trailerCountLabel == null) {
            return;
        }

        this.currentLoadedCount = loadedCount;

        // Ladefläche leeren (außer Label)
        trailerLadeflaeche.removeAll();
        trailerLadeflaeche.add(trailerCountLabel);

        if (loadedCount == 0) {
            trailerCountLabel.setText("Leer");
            trailerCountLabel.getStyle().set("color", "#E65100");
            trailerLadeflaeche.getStyle().set("background-color", "#FFB74D");
        } else {
            // Anzahl anzeigen
            String text = loadedCount + " Barren";
            if (isLoading && totalCount > loadedCount) {
                text += " (" + (totalCount - loadedCount) + " offen)";
            }
            trailerCountLabel.setText(text);
            trailerCountLabel.getStyle().set("color", "white");

            // Barren-Visualisierung (gelbe Blöcke)
            Div barrenContainer = new Div();
            barrenContainer.getStyle()
                .set("display", "flex")
                .set("flex-wrap", "wrap")
                .set("gap", "2px")
                .set("justify-content", "center")
                .set("max-width", "130px");

            // Zeige bis zu 6 kleine Barren-Symbole
            int displayCount = Math.min(loadedCount, 6);
            for (int i = 0; i < displayCount; i++) {
                Div barren = new Div();
                barren.getStyle()
                    .set("width", "18px")
                    .set("height", "12px")
                    .set("background", "linear-gradient(180deg, #FFD54F 0%, #FFC107 100%)")
                    .set("border", "1px solid #FF8F00")
                    .set("border-radius", "2px");
                barrenContainer.add(barren);
            }

            if (loadedCount > 6) {
                Span mehr = new Span("+" + (loadedCount - 6));
                mehr.getStyle()
                    .set("font-size", "9px")
                    .set("color", "white");
                barrenContainer.add(mehr);
            }

            trailerLadeflaeche.add(barrenContainer);

            // Hintergrundfarbe ändern wenn beladen
            if (isLoading) {
                trailerLadeflaeche.getStyle().set("background-color", "#FF9800");  // Dunkler Orange
            } else {
                trailerLadeflaeche.getStyle().set("background-color", "#FFA726");  // Medium Orange
            }
        }

        log.debug("Trailer updated: {} loaded, {} total, loading={}", loadedCount, totalCount, isLoading);
    }

    /**
     * Gibt die aktuelle Anzahl geladener Barren zurück
     */
    public int getCurrentLoadedCount() {
        return currentLoadedCount;
    }

    private Div createWheel() {
        Div wheel = new Div();
        wheel.getStyle()
            .set("width", "12px")
            .set("height", "20px")
            .set("background-color", "#212121")  // Schwarz
            .set("border-radius", "3px")
            .set("border", "1px solid #000");
        return wheel;
    }
    
    /**
     * Aktualisiert einen einzelnen Stockyard
     */
    public void updateStockyard(StockyardDTO yard) {
        StockyardButton button = buttonMap.get(yard.getId());
        if (button != null) {
            button.update(yard);
        }

        // Säge-Lichtschranke aktualisieren wenn es der Säge-Platz ist
        if (sawStockyardId != null && sawStockyardId.equals(yard.getId()) && sawLichtschranke != null) {
            boolean hasIngot = yard.getStatus() != null && yard.getStatus().getIngotsCount() > 0;
            updateLichtschrankeStyle(hasIngot);
            log.debug("Lichtschranke aktualisiert: {}", hasIngot ? "GRÜN" : "ROT");
        }
    }

    /**
     * Aktualisiert das Styling der Säge-Lichtschranke
     */
    private void updateLichtschrankeStyle(boolean hasIngot) {
        if (sawLichtschranke == null) return;

        sawLichtschranke.getStyle()
            .set("width", "65px")
            .set("height", "8px")
            .set("border-radius", "4px")
            .set("background-color", hasIngot ? "#4CAF50" : "#F44336")
            .set("box-shadow", hasIngot
                ? "0 0 8px #4CAF50, 0 0 12px #4CAF50"
                : "0 0 8px #F44336, 0 0 12px #F44336");
        sawLichtschranke.getElement().setAttribute("title",
            hasIngot ? "Barren bereit zur Einlagerung" : "Kein Barren auf der Säge");
    }

    /**
     * Setzt den Ziel-Lagerplatz für die Einlagerung (rote Umrandung)
     * @param stockyardId ID des Ziel-Lagerplatzes (null zum Löschen)
     */
    public void setTargetStockyard(Long stockyardId) {
        // Alten Ziel-Platz zurücksetzen
        if (targetStockyardId != null && !targetStockyardId.equals(stockyardId)) {
            StockyardButton oldButton = buttonMap.get(targetStockyardId);
            if (oldButton != null) {
                oldButton.setTargetHighlight(false);
            }
        }

        // Neuen Ziel-Platz setzen
        targetStockyardId = stockyardId;
        if (stockyardId != null) {
            StockyardButton newButton = buttonMap.get(stockyardId);

            // Wenn kein Button gefunden, prüfen ob es von einem LONG-Lagerplatz abgedeckt wird
            if (newButton == null) {
                newButton = findLongStockyardButtonCovering(stockyardId);
                if (newButton != null) {
                    log.info("Ziel {} wird von LONG-Lagerplatz {} abgedeckt",
                        stockyardId, newButton.getStockyard().getYardNumber());
                }
            }

            if (newButton != null) {
                newButton.setTargetHighlight(true);
                log.info("Ziel-Lagerplatz markiert: {}", newButton.getStockyard().getYardNumber());
            } else {
                log.warn("Ziel-Lagerplatz nicht gefunden: ID={}", stockyardId);
            }
        }
    }

    /**
     * Findet einen LONG-Lagerplatz-Button, der die angegebene Stockyard-ID abdeckt.
     * Wird verwendet wenn ein Ziel-Lagerplatz nicht direkt im buttonMap gefunden wird,
     * weil er von einem LONG-Lagerplatz "geschluckt" wurde (z.B. 12/07 -> 13/07 LONG).
     */
    private StockyardButton findLongStockyardButtonCovering(Long targetStockyardId) {
        // Zuerst die Koordinaten des Ziel-Lagerplatzes aus allStockyardsMap holen
        StockyardDTO targetYard = allStockyardsMap.get(targetStockyardId);
        if (targetYard == null) {
            log.debug("Target Stockyard ID={} nicht in allStockyardsMap gefunden", targetStockyardId);
            return null;
        }

        int targetX = targetYard.getXCoordinate();
        int targetY = targetYard.getYCoordinate();
        log.debug("Suche LONG-Lagerplatz für Position {}/{} (ID={})", targetX, targetY, targetStockyardId);

        // Durchsuche alle LONG-Lagerplätze
        for (StockyardButton button : buttonMap.values()) {
            StockyardDTO yard = button.getStockyard();

            // Nur LONG-Lagerplätze prüfen
            if (yard.getUsage() != StockyardUsage.LONG) {
                continue;
            }

            // Nur gleiche Y-Koordinate
            if (yard.getYCoordinate() != targetY) {
                continue;
            }

            int longX = yard.getXCoordinate();

            // LONG-Lagerplatz bei X deckt X und X-1 ab
            // Wenn targetX == longX oder targetX == longX-1, dann ist es abgedeckt
            if (targetX == longX || targetX == longX - 1) {
                log.info("LONG {} (X={}) deckt Ziel {}/{} (ID={}) ab",
                    yard.getYardNumber(), longX, targetX, targetY, targetStockyardId);
                return button;
            }
        }

        log.debug("Kein LONG-Lagerplatz gefunden, der Position {}/{} abdeckt", targetX, targetY);
        return null;
    }

    /**
     * Löscht die Ziel-Markierung
     */
    public void clearTargetStockyard() {
        setTargetStockyard(null);
        log.debug("Ziel-Markierung gelöscht");
    }

    /**
     * Gibt die aktuelle Ziel-Lagerplatz-ID zurück
     */
    public Long getTargetStockyardId() {
        return targetStockyardId;
    }

    /**
     * Registriert einen Click-Listener für Stockyards
     */
    public void addStockyardClickListener(Consumer<StockyardDTO> listener) {
        this.clickListener = listener;
    }

    /**
     * Registriert einen Click-Listener für den Trailer (Beladungsfläche)
     */
    public void addTrailerClickListener(Runnable listener) {
        this.trailerClickListener = listener;
    }

    // ========================================================================
    // Kran-Methoden
    // ========================================================================

    /**
     * Fügt den Kran zum Grid hinzu - mit absoluter Pixel-Positionierung
     */
    private void addCrane() {
        if (!craneVisible) return;

        // Kran-Overlay erstellen (etwas größer als eine Zelle)
        int craneSize = Math.max(CELL_WIDTH, CELL_HEIGHT) + 20;
        craneOverlay = new CraneOverlay(craneSize);

        // Absolut positionieren innerhalb des Grid-Containers
        craneOverlay.getStyle()
            .set("position", "absolute")
            .set("z-index", "100")
            .set("pointer-events", "none")
            .set("transition", "left 0.5s ease-out, top 0.5s ease-out");

        add(craneOverlay);

        // Container muss position: relative haben für absolute Kinder
        getStyle().set("position", "relative");

        // Initiale Position setzen
        updateCranePixelPosition();

        log.debug("Crane added with absolute positioning");
    }

    /**
     * Fügt die Kran-Positions-Anzeige hinzu (unterhalb des Barren-Tors)
     */
    private void addCranePositionDisplay() {
        if (!craneVisible) return;

        cranePositionDisplay = new CranePositionDisplay();
        cranePositionDisplay.getStyle()
            .set("grid-column", "26 / 29")
            .set("grid-row", "17 / 22")
            .set("justify-self", "center")
            .set("align-self", "start")
            .set("margin-top", "5px")
            .set("z-index", "10");

        // Standard-Position setzen
        cranePositionDisplay.setPosition(27000, 18000, 5000);

        add(cranePositionDisplay);
    }

    /**
     * Berechnet die Grid-Spalte für eine X-Koordinate
     * Einheitliche Berechnung für alle X-Werte
     */
    private int getGridColumnForX(int x) {
        // Begrenze auf gültigen Bereich (1-17)
        int clampedX = Math.max(1, Math.min(17, x));
        return columns - clampedX + 4;  // X=17 → Spalte 4, X=1 → Spalte 20
    }

    /**
     * Berechnet die Grid-Reihe für eine Y-Koordinate
     */
    private int getGridRowForY(int y) {
        // Begrenze auf gültigen Bereich (1-10)
        int clampedY = Math.max(1, Math.min(10, y));
        return rows - clampedY + 5;
    }

    /**
     * Aktualisiert die Pixel-Position des Krans basierend auf mm-Koordinaten
     *
     * Referenzpunkte aus Oracle-Datenbank:
     * - 17/10: X=96740, Y=35630 (links oben im UI)
     * - 01/10: X=16740, Y=35630 (rechts im Haupt-Grid)
     * - 17/01: X=96740, Y=6270  (links unten im UI)
     * - SÄGE:  X=9300,  Y=36020 (ganz rechts oben im UI)
     *
     * Pixel-Berechnung basierend auf CSS-Grid-Layout:
     * - Padding: 15px
     * - Spalte 1 (Zaun): 6px, Spalte 2 (Gap): 12px, Spalte 3 (Labels): 25px
     * - Spalten 4-20 (X=17 bis X=1): je 75px = 1275px total
     * - Weitere Spalten für Ausgangs-Plätze und Säge
     *
     * - Reihe 1 (Säge): 55px, Reihe 2 (Zaun): 6px, Reihe 3 (Gap): 12px, Reihe 4 (Labels): 20px
     * - Reihen 5-14 (Y=10 bis Y=1): je 38px = 380px total
     */
    private void updateCranePixelPosition() {
        if (craneOverlay == null) return;

        // === MM-KOORDINATEN DER REFERENZPUNKTE (aus Datenbank) ===
        final int MM_X_17 = 96740;     // X=17 (links im UI)
        final int MM_X_01 = 16740;     // X=1 (rechts im Haupt-Grid)
        final int MM_SAW_X = 9300;     // Säge X-Position
        final int MM_Y_10 = 35630;     // Y=10 (oben im UI)
        final int MM_Y_01 = 6270;      // Y=1 (unten im UI)
        final int MM_SAW_Y = 36020;    // Säge Y-Position

        // === PIXEL-KOORDINATEN basierend auf CSS-Grid ===
        // CSS-Grid Layout:
        // - Padding: 15px links
        // - Spalte 1 (Zaun): 6px, Spalte 2 (Gap): 12px, Spalte 3 (Labels): 25px
        // - Spalten 4-20: Stockyards X=17 bis X=1, je 75px mit 3px Gap
        // Start von Spalte 4: 15 + 6 + 3 + 12 + 3 + 25 + 3 = 67px
        // Mitte Spalte 4 (X=17): 67 + 37 = 104px (gerundet 105)
        // Mitte Spalte 20 (X=1): 105 + 16*78 = 1353px
        final int PIXEL_X_17 = 105;    // Spalte 4 Mitte (X=17, mm=96740)
        final int PIXEL_X_01 = 1353;   // Spalte 20 Mitte (X=1, mm=16740)
        final int PIXEL_Y_10 = 140;    // Reihe 5 Mitte (Y=10, mm=35630)
        final int PIXEL_Y_01 = 509;    // Reihe 14 Mitte (Y=1, mm=6270)

        // Y-Offset: Kran etwas höher positionieren (negative Werte = nach oben)
        // Zellen sind 38px hoch, Kran ist 95px hoch -> Kran ist viel größer
        final int PIXEL_Y_OFFSET = -12;  // 12 Pixel nach oben verschieben (negativ = höher)

        // === SÄGE-POSITION (visuell in Reihe 1-3, Spalte 24) ===
        // Die Säge ist visuell oberhalb des Lager-Grids platziert
        // Empirisch kalibriert auf die visuelle Säge-Position
        final int PIXEL_SAW_X = 1520;  // Säge X-Pixel-Position (empirisch angepasst)
        final int PIXEL_SAW_Y = 45;    // Säge Y-Pixel-Position (Mitte von Reihe 1-3)

        int pixelX, pixelY;

        // === SONDERFALL: SÄGE-BEREICH ===
        // Wenn Kran im Säge-Bereich (X < 12000 und Y > 35500), direkte Säge-Position verwenden
        if (craneMmX < 12000 && craneMmY > 35500) {
            pixelX = PIXEL_SAW_X;
            pixelY = PIXEL_SAW_Y;
            log.info("Crane at SAW position: mm({},{}) -> pixel({},{})", craneMmX, craneMmY, pixelX, pixelY);
        } else {
            // === NORMALE LINEARE INTERPOLATION ===
            // X: Hohe mm-Werte (96740) -> niedrige Pixel, niedrige mm-Werte (16740) -> hohe Pixel
            double ratioX = (double)(MM_X_17 - craneMmX) / (MM_X_17 - MM_X_01);
            pixelX = PIXEL_X_17 + (int)(ratioX * (PIXEL_X_01 - PIXEL_X_17));

            // Y: Hohe mm-Werte (35630) -> niedrige Pixel, niedrige mm-Werte (6270) -> hohe Pixel
            double ratioY = (double)(MM_Y_10 - craneMmY) / (MM_Y_10 - MM_Y_01);
            pixelY = PIXEL_Y_10 + (int)(ratioY * (PIXEL_Y_01 - PIXEL_Y_10)) + PIXEL_Y_OFFSET;

            // Erwartete Stockyard-Position berechnen (zur Diagnose)
            // X: Hohe mm = hohe X-Koordinate (links), niedrige mm = niedrige X-Koordinate (rechts)
            // X = 17 - (MM_X_17 - craneMmX) / 5000
            int columnsFromX17 = (int) Math.round((double)(MM_X_17 - craneMmX) / 5000.0);
            int expectedXCoord = 17 - columnsFromX17;
            if (expectedXCoord < 1) expectedXCoord = 1;
            if (expectedXCoord > 17) expectedXCoord = 17;

            // Y: Hohe mm = hohe Y-Koordinate (oben), niedrige mm = niedrige Y-Koordinate (unten)
            int rowsFromY1 = (int) Math.round((double)(craneMmY - MM_Y_01) / ((MM_Y_10 - MM_Y_01) / 9.0));
            int expectedYCoord = 1 + rowsFromY1;
            if (expectedYCoord < 1) expectedYCoord = 1;
            if (expectedYCoord > 10) expectedYCoord = 10;

            log.info("=== CRANE PIXEL DEBUG ===");
            log.info("Crane mm: X={}, Y={}", craneMmX, craneMmY);
            log.info("Ratio: X={}, Y={}", String.format("%.4f", ratioX), String.format("%.4f", ratioY));
            log.info("Pixel center (vor Offset): X={}, Y={}", pixelX, pixelY);
            log.info("Erwarteter Lagerplatz: {}/{} (berechnet aus mm)",
                String.format("%02d", expectedXCoord), String.format("%02d", expectedYCoord));

            // === LONG STOCKYARD KORREKTUR ===
            // Prüfen ob die Zielposition von einem LONG-Lagerplatz abgedeckt wird
            // LONG-Lagerplätze spannen 2 Spalten: X und X-1
            // Wenn Ziel z.B. X=12 ist und 13/08 ein LONG ist, dann deckt 13/08 auch 12/08 ab
            StockyardButton longStockyard = findLongStockyardCovering(expectedXCoord, expectedYCoord);
            if (longStockyard != null) {
                StockyardDTO longYard = longStockyard.getStockyard();
                int longX = longYard.getXCoordinate();
                int longY = longYard.getYCoordinate();

                // LONG-Lagerplatz deckt X und X-1 ab
                // Mitte ist zwischen diesen beiden Spalten
                // Pixel für X: 105 + (17 - X) * 78
                // Pixel für X-1: 105 + (17 - (X-1)) * 78 = Pixel für X + 78
                // Mitte: Pixel für X + 39
                int longCenterPixelX = PIXEL_X_17 + (17 - longX) * 78 + 39;  // +39 = halbe Spalte nach rechts

                // Y-Position auch auf Zellenmitte korrigieren
                // Zeilen-Pixel: PIXEL_Y_10 für Y=10, PIXEL_Y_01 für Y=1
                // Jede Zeile: (PIXEL_Y_01 - PIXEL_Y_10) / 9 = ca. 41 Pixel
                int pixelPerRow = (PIXEL_Y_01 - PIXEL_Y_10) / 9;
                int longCenterPixelY = PIXEL_Y_10 + (10 - longY) * pixelPerRow;

                log.info("LONG STOCKYARD KORREKTUR: {} deckt Position {}/{} ab",
                    longYard.getYardNumber(),
                    String.format("%02d", expectedXCoord), String.format("%02d", expectedYCoord));
                log.info("Pixel X angepasst: {} -> {} (Mitte von LONG)", pixelX, longCenterPixelX);
                log.info("Pixel Y angepasst: {} -> {} (Zellenmitte Y={})", pixelY, longCenterPixelY, longY);

                pixelX = longCenterPixelX;
                pixelY = longCenterPixelY;
            }
            log.info("=========================");
        }

        // Kran-Größe berücksichtigen (zentrieren)
        // craneSize/2 wird abgezogen um die LEFT/TOP CSS-Position zu setzen,
        // sodass der Kran ZENTRIERT über dem Zielpunkt erscheint
        int craneSize = Math.max(CELL_WIDTH, CELL_HEIGHT) + 20;
        int centerPixelX = pixelX;  // Für Logging
        int centerPixelY = pixelY;
        pixelX -= craneSize / 2;
        pixelY -= craneSize / 2;

        log.info("Crane position: center=({},{}) -> left/top=({},{}) [craneSize={}]",
            centerPixelX, centerPixelY, pixelX, pixelY, craneSize);

        // Position setzen
        craneOverlay.getStyle()
            .set("left", pixelX + "px")
            .set("top", pixelY + "px");
    }

    /**
     * Findet einen LONG-Lagerplatz, der die angegebene Position abdeckt.
     * LONG-Lagerplätze spannen 2 Spalten: ihre eigene X-Koordinate und X-1.
     * Beispiel: LONG 13/08 deckt 13/08 und 12/08 ab.
     *
     * @param targetX X-Koordinate der Zielposition
     * @param targetY Y-Koordinate der Zielposition
     * @return StockyardButton des LONG-Lagerplatzes, oder null wenn keiner gefunden
     */
    private StockyardButton findLongStockyardCovering(int targetX, int targetY) {
        for (StockyardButton button : buttonMap.values()) {
            StockyardDTO yard = button.getStockyard();

            // Nur LONG-Lagerplätze prüfen
            if (yard.getUsage() != StockyardUsage.LONG) {
                continue;
            }

            // Nur gleiche Y-Koordinate
            if (yard.getYCoordinate() != targetY) {
                continue;
            }

            int longX = yard.getXCoordinate();

            // LONG-Lagerplatz deckt X und X-1 ab
            // Also: longX und longX-1
            if (targetX == longX || targetX == longX - 1) {
                log.debug("LONG {} deckt Position {}/{} ab (longX={}, targetX={})",
                    yard.getYardNumber(), targetX, targetY, longX, targetX);
                return button;
            }
        }
        return null;
    }

    /**
     * Alte Grid-basierte Positionierung (für Abwärtskompatibilität)
     */
    private void updateCraneGridPosition() {
        // Konvertiere Grid-Koordinaten zu mm und verwende die neue Methode
        // Grid X=1 entspricht stockMinX, Grid X=17 entspricht stockMaxX
        int rangeX = stockMaxX - stockMinX;
        int rangeY = stockMaxY - stockMinY;
        int mmX = stockMinX + (craneGridX - 1) * (rangeX / 16);  // mm pro Spalte
        int mmY = stockMinY + (craneGridY - 1) * (rangeY / 9);   // mm pro Reihe
        setCranePositionMm(mmX, mmY);
    }

    /**
     * Setzt die Kran-Position (Grid-Koordinaten) - veraltet, nutze setCranePositionMm()
     * @param x X-Koordinate (1-17)
     * @param y Y-Koordinate (1-10)
     */
    public void setCraneGridPosition(int x, int y) {
        this.craneGridX = x;
        this.craneGridY = y;
        updateCraneGridPosition();
        log.debug("Crane moved to grid position ({}, {})", x, y);
    }

    /**
     * Setzt die Kran-Position direkt in mm-Koordinaten
     * Dies ist die bevorzugte Methode für die exakte Positionierung
     * @param mmX X-Koordinate in mm
     * @param mmY Y-Koordinate in mm
     */
    public void setCranePositionMm(int mmX, int mmY) {
        this.craneMmX = mmX;
        this.craneMmY = mmY;
        updateCranePixelPosition();
        log.debug("Crane moved to mm position ({}, {})", mmX, mmY);
    }

    /**
     * Setzt die Kran-Position in mm und aktualisiert die Anzeige (inkl. Z für Display)
     * @param x X-Koordinate in mm
     * @param y Y-Koordinate in mm
     * @param z Z-Koordinate in mm
     */
    public void setCranePosition(int x, int y, int z) {
        // Position im Grid aktualisieren (X, Y)
        setCranePositionMm(x, y);

        // Positions-Display aktualisieren (X, Y, Z)
        if (cranePositionDisplay != null) {
            cranePositionDisplay.setPosition(x, y, z);
        }
        // Greifer-Höhe basierend auf Z berechnen (0-100)
        if (craneOverlay != null) {
            int gripperHeight = Math.max(0, Math.min(100, z / 100));
            craneOverlay.setGripperHeight(gripperHeight);
        }
    }

    /**
     * Setzt den Barren im Kran-Greifer
     * @param ingotNumber Barren-Nummer (null = kein Barren)
     * @param productNumber Produkt-Nummer
     */
    public void setCraneIngot(String ingotNumber, String productNumber) {
        if (craneOverlay != null) {
            if (ingotNumber != null) {
                craneOverlay.setIngot(ingotNumber, productNumber, 40, 30);
            } else {
                craneOverlay.clearIngot();
            }
        }
    }

    /**
     * Zeigt oder versteckt den Kran
     * @param visible true = Kran anzeigen
     */
    public void setCraneVisible(boolean visible) {
        this.craneVisible = visible;
        if (craneOverlay != null) {
            craneOverlay.setVisible(visible);
        }
        if (cranePositionDisplay != null) {
            cranePositionDisplay.setVisible(visible);
        }
    }

    /**
     * Gibt das Kran-Overlay zurück
     */
    public CraneOverlay getCraneOverlay() {
        return craneOverlay;
    }

    /**
     * Gibt die Kran-Positions-Anzeige zurück
     */
    public CranePositionDisplay getCranePositionDisplay() {
        return cranePositionDisplay;
    }

    // ========================================================================
    // Innere Klasse: StockyardButton
    // ========================================================================

    /**
     * Button für einen einzelnen Lagerplatz
     * Zeigt Platznummer oben und Barren-Anzahl unten
     */
    public static class StockyardButton extends Div {

        private StockyardDTO stockyard;
        private SettingsService settingsService;
        private Span yardLabel;
        private Span countLabel;
        private boolean isTargetHighlighted = false;

        public StockyardButton(StockyardDTO stockyard, SettingsService settingsService) {
            this.settingsService = settingsService;

            // LONG Lagerplätze doppelt so breit (2 * CELL_WIDTH + GAP)
            boolean isLong = stockyard.getUsage() == StockyardUsage.LONG;
            int buttonWidth = isLong ? (2 * CELL_WIDTH + GAP) : CELL_WIDTH;

            setWidth(buttonWidth + "px");
            setHeight(CELL_HEIGHT + "px");
            getStyle()
                .set("min-width", buttonWidth + "px")
                .set("padding", "2px")
                .set("border-radius", "4px")
                .set("cursor", "pointer")
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("box-sizing", "border-box");

            // Platznummer oben (klein)
            yardLabel = new Span();
            yardLabel.getStyle()
                .set("font-size", "9px")
                .set("line-height", "1");
            add(yardLabel);

            // Barren-Anzahl unten (groß)
            countLabel = new Span();
            countLabel.getStyle()
                .set("font-size", "14px")
                .set("font-weight", "bold")
                .set("line-height", "1");
            add(countLabel);

            update(stockyard);
        }

        /**
         * Aktualisiert den Button mit neuen Daten
         */
        public void update(StockyardDTO stockyard) {
            this.stockyard = stockyard;

            // Anzahl/Status setzen
            StockyardStatusDTO status = stockyard.getStatus();

            // Für SAW-Plätze: Immer die Barren-Nummer des nächsten Barrens anzeigen
            if (stockyard.getType() == StockyardType.SAW) {
                yardLabel.setVisible(false);  // Platznummer ausblenden
                if (status != null && status.getIngotsCount() > 0) {
                    // Immer die Barren-Nummer anzeigen (erster in der Warteschlange)
                    String displayText = status.getIngotNumber() != null ? status.getIngotNumber() : "-";
                    countLabel.setText(displayText);
                    countLabel.getStyle()
                        .set("font-size", "9px")
                        .set("text-align", "center")
                        .set("word-break", "break-all");
                } else {
                    countLabel.setText("-");
                    countLabel.getStyle().set("font-size", "14px");
                }
            } else {
                // Normale Lagerplätze: Platznummer + Anzahl
                yardLabel.setVisible(true);
                yardLabel.setText(stockyard.getYardNumber());

                if (status == null || status.isEmpty()) {
                    countLabel.setText("-");
                } else {
                    String countText = String.valueOf(status.getIngotsCount());
                    if (status.isRevisedOnTop()) countText += "k";
                    if (status.isScrapOnTop()) countText += "s";
                    countLabel.setText(countText);
                    countLabel.getStyle().set("font-size", "14px");
                }
            }

            // Farben basierend auf Status
            applyColors();

            // Tooltip
            setTooltip(stockyard);
        }

        private void applyColors() {
            StockyardStatusDTO status = stockyard.getStatus();

            // Farben aus SettingsService holen (mit Fallback)
            String colorEmpty = settingsService != null ? settingsService.getColorYardEmpty() : "#4CAF50";
            String colorInUse = settingsService != null ? settingsService.getColorYardInUse() : "#FFEB3B";
            String colorFull = settingsService != null ? settingsService.getColorYardFull() : "#F44336";
            String colorLocked = settingsService != null ? settingsService.getColorYardLocked() : "#9E9E9E";
            String colorNoSwapIn = settingsService != null ? settingsService.getColorYardNoSwapIn() : "#FF9800";
            String colorNoSwapOut = settingsService != null ? settingsService.getColorYardNoSwapOut() : "#2196F3";
            String colorNoSwapInOut = settingsService != null ? settingsService.getColorYardNoSwapInOut() : "#9C27B0";

            // Basis-Farbe basierend auf Belegung
            String bgColor;
            String textColor = "white";

            if (status == null || status.isEmpty()) {
                // Leer - aus Einstellungen
                bgColor = colorEmpty;
                textColor = isLightColor(bgColor) ? "#333" : "white";
            } else if (stockyard.isFull()) {
                // Voll - aus Einstellungen
                bgColor = colorFull;
                textColor = isLightColor(bgColor) ? "#333" : "white";
            } else {
                // Teilweise belegt - aus Einstellungen
                bgColor = colorInUse;
                textColor = isLightColor(bgColor) ? "#333" : "white";
            }

            // Externe Plätze leicht anders (etwas dunkler)
            if (stockyard.getType() == StockyardType.EXTERNAL) {
                if (status != null && !status.isEmpty()) {
                    bgColor = stockyard.isFull() ? "#5E35B1" : "#9575CD";
                    textColor = "white";
                }
            }

            // Verladezone
            if (stockyard.getType() == StockyardType.LOADING) {
                if (status != null && !status.isEmpty()) {
                    bgColor = stockyard.isFull() ? "#00796B" : "#4DB6AC";
                    textColor = "white";
                }
            }

            // Gesperrter Lagerplatz
            if (stockyard.isLocked()) {
                bgColor = colorLocked;
                textColor = isLightColor(bgColor) ? "#333" : "white";
            }

            getStyle()
                .set("background-color", bgColor)
                .set("color", textColor);

            // Border für Ziel-Markierung oder Einschränkungen
            String border = "none";
            if (isTargetHighlighted) {
                // Ziel-Lagerplatz für Einlagerung - rote Umrandung mit Leucht-Effekt
                // LONG-Lagerplätze brauchen höhere z-index damit die Umrandung nicht abgeschnitten wird
                border = "3px solid #F44336";
                getStyle()
                    .set("box-shadow", "0 0 10px #F44336, 0 0 20px #F44336")
                    .set("position", "relative")
                    .set("z-index", "50");
            } else {
                // Normale Border-Logik - z-index und position zurücksetzen
                getStyle()
                    .remove("box-shadow")
                    .remove("z-index")
                    .remove("position");
                if (!stockyard.isToStockAllowed() && !stockyard.isFromStockAllowed()) {
                    // Komplett gesperrt
                    border = "3px solid " + colorNoSwapInOut;
                } else if (!stockyard.isToStockAllowed()) {
                    // Einlagern gesperrt
                    border = "3px solid " + colorNoSwapIn;
                } else if (!stockyard.isFromStockAllowed()) {
                    // Auslagern gesperrt
                    border = "3px solid " + colorNoSwapOut;
                }
            }
            getStyle().set("border", border);
        }

        /**
         * Prüft ob eine Farbe hell ist (für Textfarben-Kontrast)
         */
        private boolean isLightColor(String hexColor) {
            if (hexColor == null || !hexColor.startsWith("#") || hexColor.length() < 7) {
                return false;
            }
            try {
                int r = Integer.parseInt(hexColor.substring(1, 3), 16);
                int g = Integer.parseInt(hexColor.substring(3, 5), 16);
                int b = Integer.parseInt(hexColor.substring(5, 7), 16);
                // Luminanz-Formel
                double luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
                return luminance > 0.5;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        private void setTooltip(StockyardDTO yard) {
            StringBuilder tooltip = new StringBuilder();
            tooltip.append(yard.getYardNumber());
            tooltip.append(" (").append(yard.getType().getDisplayName()).append(")");

            StockyardStatusDTO status = yard.getStatus();
            if (status != null && !status.isEmpty()) {
                tooltip.append("\nBarren: ").append(status.getIngotsCount())
                       .append("/").append(yard.getMaxIngots());
                if (status.getProductNumber() != null) {
                    tooltip.append("\nProdukt: ").append(status.getProductNumber());
                }
            }

            if (!yard.isToStockAllowed()) {
                tooltip.append("\n! Einlagern gesperrt");
            }
            if (!yard.isFromStockAllowed()) {
                tooltip.append("\n! Auslagern gesperrt");
            }

            getElement().setAttribute("title", tooltip.toString());
        }

        public StockyardDTO getStockyard() {
            return stockyard;
        }

        /**
         * Setzt oder entfernt die Ziel-Hervorhebung (rote Umrandung)
         */
        public void setTargetHighlight(boolean highlight) {
            this.isTargetHighlighted = highlight;
            applyColors();  // Farben neu anwenden inkl. Border
        }

        /**
         * Prüft ob dieser Button als Ziel markiert ist
         */
        public boolean isTargetHighlighted() {
            return isTargetHighlighted;
        }
    }
}
