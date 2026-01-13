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
    private Consumer<StockyardDTO> clickListener;
    private SettingsService settingsService;

    // Grid-Konfiguration (basierend auf Original)
    private int columns = 17;  // X-Koordinaten 17-1
    private int rows = 10;     // Y-Koordinaten 10-1
    private static final int CELL_WIDTH = 75;   // Breite für rechteckige Barren
    private static final int CELL_HEIGHT = 38;  // Höhe für rechteckige Barren
    private static final int GAP = 3;
    private static final int FENCE_WIDTH = 6;
    private static final int FENCE_GAP = 12;  // Abstand zwischen Zaun und Plätzen

    // Kran-Komponenten
    private CraneOverlay craneOverlay;
    private CranePositionDisplay cranePositionDisplay;
    private int craneGridX = 4;  // Standard-Position X
    private int craneGridY = 9;  // Standard-Position Y
    private int craneBaseGridX = 9;  // Basis-Position für Animation (Mitte)
    private int craneBaseGridY = 5;  // Basis-Position für Animation (Mitte)
    private boolean craneVisible = true;

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
        // Spalten-Layout (von links nach rechts) - 27 Spalten:
        // 1: Linker Zaun (6px)
        // 2: Abstand (12px)
        // 3: Y-Achsen-Labels (25px)
        // 4-10: Lagerplätze X=17 bis X=11 (7 Spalten à 55px)
        // 11: Vertikaler Zaun rechts von 11/01 (6px)
        // 12-21: Lagerplätze X=10 bis X=1 (10 Spalten à 55px)
        // 22: Abstand (12px)
        // 23: Säge-Bereich / Personen-Tor (70px)
        // 24: Lagerplätze 00/01-00/08 (55px) - zwischen Säge und Zaun
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

        // Spalten-Layout ohne extra Zaun-Spalte zwischen X=11 und X=10
        // Alle Lagerplätze haben jetzt gleichmäßigen Abstand
        String colTemplate = FENCE_WIDTH + "px " +      // 1: Linker Zaun
                            FENCE_GAP + "px " +          // 2: Abstand
                            "25px " +                    // 3: Y-Labels
                            "repeat(17, " + CELL_WIDTH + "px) " +  // 4-20: Lager (X=17 bis X=1)
                            FENCE_GAP + "px " +          // 21: Abstand
                            "70px " +                    // 22: Säge / Personen-Tor
                            CELL_WIDTH + "px " +         // 23: Lagerplätze 00/01-00/08
                            FENCE_WIDTH + "px " +        // 24: Kleiner Zaun zwischen Toren
                            "20px " +                    // 25: Barren-Tor
                            FENCE_WIDTH + "px";          // 26: Rechter Zaun

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

            // Grid-Position berechnen (einheitlich für alle X-Koordinaten)
            int xCoord = yard.getXCoordinate();
            int gridCol = columns - xCoord + 4;  // X=17 → Spalte 4, X=1 → Spalte 20
            // Y=10 -> Reihe 5, Y=1 -> Reihe 14
            int gridRow = rows - yard.getYCoordinate() + 5;

            // Sonderfall: Plätze 17/01 bis 11/01 (Y=1, X>=11) werden nach Reihe 17 verschoben
            if (yard.getYCoordinate() == 1 && xCoord >= 11) {
                gridRow = 17;  // Nach unten verschieben
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
     */
    private void addSaegeausgang(StockyardDTO sawYard) {
        // Säge-Container - Reihe 1-3, Spalte 21-22 (kürzer, nicht bis zu den Lagerplätzen)
        Div saegeContainer = new Div();
        saegeContainer.addClassName("saege-container");
        saegeContainer.getStyle()
            .set("grid-column", "21 / 23")  // Säge-Bereich (Gap + Säge)
            .set("grid-row", "1 / 4")       // Nur bis zum Zaun (kürzer)
            .set("margin-left", "-20px")    // Feineinstellung nach links
            .set("display", "flex")
            .set("flex-direction", "column")
            .set("align-items", "center")
            .set("justify-content", "stretch");

        if (sawYard != null) {
            // Interaktiver Säge-Button mit Barren-Anzeige (volle Höhe bis Zaun)
            StockyardButton sawButton = new StockyardButton(sawYard, settingsService);
            sawButton.getStyle()
                .set("width", "65px")
                .set("height", "100%")
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

            // Debug-Info
            String ingotInfo = sawYard.getStatus() != null && sawYard.getStatus().getIngotNumber() != null
                ? sawYard.getStatus().getIngotNumber()
                : "kein Barren";
            int count = sawYard.getStatus() != null ? sawYard.getStatus().getIngotsCount() : 0;
            log.info("SAW position: {} - Barren: {}, Anzahl: {}", sawYard.getYardNumber(), ingotInfo, count);
        } else {
            // Fallback: Statisches Säge-Symbol (kein Stockyard definiert)
            Div saege = new Div();
            saege.getStyle()
                .set("width", "65px")
                .set("height", "100%")
                .set("background", "linear-gradient(90deg, #1565C0 0%, #1565C0 15%, #90CAF9 15%, #90CAF9 30%, #1565C0 30%, #1565C0 45%, #90CAF9 45%, #90CAF9 60%, #1565C0 60%, #1565C0 75%, #90CAF9 75%, #90CAF9 90%, #1565C0 90%)")
                .set("border-radius", "4px")
                .set("border", "2px solid #0D47A1");

            saegeContainer.add(saege);
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
        // Spalten: 1=linker Zaun, 21=Gap, 26=rechter Zaun
        // Alle Spalten nach der alten Zaun-Spalte 11 sind um 1 verschoben

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
        Div topGate1 = createGate();
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
        Div topGate2 = createGate();
        topGate2.getStyle()
            .set("grid-column", "11")
            .set("grid-row", "2");
        add(topGate2);

        // Zaun rechts bis zum Säge-Ausgang (Spalte 12-20)
        Div topFence3 = createFenceSegment();
        topFence3.getStyle()
            .set("grid-column", "12 / 21")
            .set("grid-row", "2");
        add(topFence3);

        // Säge-Ausgang (Spalte 22) - OFFEN

        // Zaun zwischen Säge-Ausgang und Tor 3
        Div topFence3c = createFenceSegment();
        topFence3c.getStyle()
            .set("grid-column", "22 / 24")
            .set("grid-row", "2")
            .set("margin-left", "50px");
        add(topFence3c);

        // Tor 3 oberhalb von Platz 00/08 (Spalte 24)
        Div topGate3 = createGate();
        topGate3.getStyle()
            .set("grid-column", "24")
            .set("grid-row", "2");
        add(topGate3);

        // Zaun rechts von Tor 3 (Spalte 25-26)
        Div topFence4 = createFenceSegment();
        topFence4.getStyle()
            .set("grid-column", "25 / 27")
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

        // ============ RECHTER ZAUN (Spalte 26) ============
        Div rightFence = createFenceSegment();
        rightFence.getStyle()
            .set("grid-column", "26")
            .set("grid-row", "2 / 17");
        add(rightFence);

        // ============ GEMEINSAMER ZAUN (Reihe 16) - beginnt bei Spalte 11 (rechts von 11/01) ============

        // Tor bei Spalte 11 (über X=10, rechts von 11/01)
        Div torSpalte11 = createGate();
        torSpalte11.getStyle()
            .set("grid-column", "11")
            .set("grid-row", "16");
        add(torSpalte11);

        // Zaun von Spalte 12 bis zu den Toren (Spalte 12-21)
        Div bottomFence2 = createFenceSegment();
        bottomFence2.getStyle()
            .set("grid-column", "12 / 22")
            .set("grid-row", "16");
        add(bottomFence2);

        // Personen-Tor (Spalte 22)
        Div personenTor = createGate();
        personenTor.getStyle()
            .set("grid-column", "22")
            .set("grid-row", "16");
        add(personenTor);

        // Zaun zwischen Personen-Tor und Barren-Tor (Spalte 23)
        Div zaunZwischenToren = createFenceSegment();
        zaunZwischenToren.getStyle()
            .set("grid-column", "23")
            .set("grid-row", "16");
        add(zaunZwischenToren);

        // Barren-Tor (Spalte 24-26)
        Div barrenTor = createGate();
        barrenTor.getStyle()
            .set("grid-column", "24 / 27")
            .set("grid-row", "16");
        add(barrenTor);

        // ============ ZAUN UNTER PLÄTZEN 17/01-11/01 (Reihe 18) ============
        // Diese Plätze sind in Reihe 17, daher Zaun in Reihe 18

        // Zaun Spalte 2 bis 4 (linker Teil, bis zum Tor unter 17/01)
        Div bottomFenceShiftedLeft = createFenceSegment();
        bottomFenceShiftedLeft.getStyle()
            .set("grid-column", "2 / 5")
            .set("grid-row", "19")
            .set("margin-right", "20px");  // Platz für das Tor lassen
        add(bottomFenceShiftedLeft);

        // Tor unter Platz 17/01 (Spalte 4) - 20px breit, rechts ausgerichtet
        Div torUnter1701 = createGate();
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

        // Einfahrt-Schranke (Reihe 17-20) - 70px hoch
        Div einfahrtTor = createGate();
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

        // Ausfahrt-Schranke (Reihe 17-20) - 70px hoch
        Div schranke = createGate();
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

        // Fahrer-Tor (links, auf Höhe der Zugkabine) - 20px breit
        Div fahrerTor = createGate();
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
     * Fügt 8 zusätzliche Lagerplätze (00/01 bis 00/08) hinzu.
     * Diese befinden sich in Spalte 23, zwischen der Säge und dem rechten Zaun.
     * Y-Koordinaten: 2 bis 9 (Reihen 13 bis 6)
     */
    private void addZusaetzlicheLagerplaetze() {
        // 00/01 bis 00/08 in Spalte 23
        // Y=2 (Reihe 13) bis Y=9 (Reihe 6)

        for (int i = 1; i <= 8; i++) {
            String label = String.format("00/%02d", i);
            int yCoord = i + 1;  // 00/01 bei Y=2, 00/08 bei Y=9
            int gridRow = rows - yCoord + 5;  // Y=2 → Reihe 13, Y=9 → Reihe 6

            Div platz = new Div();
            platz.addClassName("zusatz-lagerplatz");
            platz.getStyle()
                .set("grid-column", "23")
                .set("grid-row", String.valueOf(gridRow))
                .set("background-color", "#E0E0E0")  // Grau (leer)
                .set("border-radius", "4px")
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("cursor", "pointer")
                .set("min-width", CELL_WIDTH + "px")
                .set("min-height", CELL_HEIGHT + "px");

            // Platznummer
            Span labelSpan = new Span(label);
            labelSpan.getStyle()
                .set("font-size", "9px")
                .set("color", "#666");
            platz.add(labelSpan);

            // Anzahl (leer)
            Span countSpan = new Span("-");
            countSpan.getStyle()
                .set("font-size", "14px")
                .set("font-weight", "bold")
                .set("color", "#666");
            platz.add(countSpan);

            // Tooltip
            platz.getElement().setAttribute("title", label + " (Zusatz-Lagerplatz)");

            add(platz);
        }

        log.debug("Added 8 additional storage places (00/01 - 00/08)");
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
        Div trailerContainer = new Div();
        trailerContainer.addClassName("trailer");
        trailerContainer.getStyle()
            .set("position", "relative")
            .set("display", "flex")
            .set("flex-direction", "row")
            .set("align-items", "center")
            .set("gap", "0px");

        // Zugmaschine (Kabine) - links
        Div kabine = new Div();
        kabine.getStyle()
            .set("width", "25px")
            .set("height", "50px")
            .set("background-color", "#FF9800")  // Orange
            .set("border-radius", "4px 0 0 4px")
            .set("border", "2px solid #E65100")
            .set("border-right", "none");

        // Ladefläche (Anhänger) - rechts, länger für 2 Barren hintereinander
        Div ladeflaeche = new Div();
        ladeflaeche.getStyle()
            .set("width", "140px")
            .set("height", "60px")
            .set("background-color", "#FFB74D")  // Heller Orange
            .set("border-radius", "0 4px 4px 0")
            .set("border", "2px solid #E65100")
            .set("display", "flex")
            .set("align-items", "center")
            .set("justify-content", "space-evenly");

        // 2 Ladegut-Markierungen (für 2 Barren)
        for (int i = 0; i < 2; i++) {
            Div ladegut = new Div();
            ladegut.getStyle()
                .set("width", "55px")
                .set("height", "40px")
                .set("background-color", "#FF9800")
                .set("border-radius", "2px")
                .set("border", "1px dashed #E65100");
            ladeflaeche.add(ladegut);
        }

        trailerContainer.add(kabine, ladeflaeche);

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
    }
    
    /**
     * Registriert einen Click-Listener
     */
    public void addStockyardClickListener(Consumer<StockyardDTO> listener) {
        this.clickListener = listener;
    }

    // ========================================================================
    // Kran-Methoden
    // ========================================================================

    /**
     * Fügt den Kran zum Grid hinzu
     */
    private void addCrane() {
        if (!craneVisible) return;

        // Kran-Overlay erstellen (etwas größer als eine Zelle)
        int craneSize = Math.max(CELL_WIDTH, CELL_HEIGHT) + 20;
        craneOverlay = new CraneOverlay(craneSize);

        // Kran an Basis-Position platzieren
        int baseGridCol = getGridColumnForX(craneBaseGridX);
        int baseGridRow = getGridRowForY(craneBaseGridY);

        craneOverlay.getStyle()
            .set("grid-column", String.valueOf(baseGridCol))
            .set("grid-row", String.valueOf(baseGridRow))
            .set("z-index", "100")
            .set("justify-self", "center")
            .set("align-self", "center");

        add(craneOverlay);

        // Initiale Position mit Animation setzen
        updateCraneGridPosition();

        log.debug("Crane added at base position, moving to ({}, {})", craneGridX, craneGridY);
    }

    /**
     * Fügt die Kran-Positions-Anzeige hinzu (unterhalb des Barren-Tors)
     */
    private void addCranePositionDisplay() {
        if (!craneVisible) return;

        cranePositionDisplay = new CranePositionDisplay();
        cranePositionDisplay.getStyle()
            .set("grid-column", "24 / 27")
            .set("grid-row", "17 / 22")
            .set("justify-self", "center")
            .set("align-self", "start")
            .set("margin-top", "5px")
            .set("z-index", "10");

        // Standard-Position setzen
        cranePositionDisplay.setPosition(37620, 28340, 7020);

        add(cranePositionDisplay);
    }

    /**
     * Berechnet die Grid-Spalte für eine X-Koordinate
     * Einheitliche Berechnung für alle X-Werte
     */
    private int getGridColumnForX(int x) {
        return columns - x + 4;  // X=17 → Spalte 4, X=1 → Spalte 20
    }

    /**
     * Berechnet die Grid-Reihe für eine Y-Koordinate
     */
    private int getGridRowForY(int y) {
        return rows - y + 5;
    }

    /**
     * Aktualisiert die Grid-Position des Krans mit Animation
     */
    private void updateCraneGridPosition() {
        if (craneOverlay == null) return;

        // Ziel-Position berechnen
        int targetCol = getGridColumnForX(craneGridX);
        int baseCol = getGridColumnForX(craneBaseGridX);

        int targetRow = getGridRowForY(craneGridY);
        int baseRow = getGridRowForY(craneBaseGridY);

        // Pixel-Offset berechnen (Spalten-Differenz * (Zellenbreite + Gap))
        int colDiff = targetCol - baseCol;
        int rowDiff = targetRow - baseRow;

        int translateX = colDiff * (CELL_WIDTH + GAP);
        int translateY = rowDiff * (CELL_HEIGHT + GAP);

        // Animation starten
        craneOverlay.setTranslatePosition(translateX, translateY);

        log.debug("Crane animation: base({},{}) -> target({},{}) = translate({},{})",
                baseCol, baseRow, targetCol, targetRow, translateX, translateY);
    }

    /**
     * Setzt die Kran-Position (Grid-Koordinaten)
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
     * Setzt die Kran-Position in mm und aktualisiert die Anzeige
     * @param x X-Koordinate in mm
     * @param y Y-Koordinate in mm
     * @param z Z-Koordinate in mm
     */
    public void setCranePosition(int x, int y, int z) {
        if (cranePositionDisplay != null) {
            cranePositionDisplay.setPosition(x, y, z);
        }
        if (craneOverlay != null) {
            // Greifer-Höhe basierend auf Z berechnen (0-100)
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

            // Platznummer setzen
            yardLabel.setText(stockyard.getYardNumber());

            // Anzahl/Status setzen
            StockyardStatusDTO status = stockyard.getStatus();

            if (status == null || status.isEmpty()) {
                countLabel.setText("-");
            } else {
                // Für SAW-Plätze: Barren-Nummer anzeigen statt Anzahl
                if (stockyard.getType() == StockyardType.SAW && status.getIngotNumber() != null) {
                    countLabel.setText(status.getIngotNumber());
                    countLabel.getStyle().set("font-size", "10px");  // Kleinere Schrift für lange Nummern
                } else {
                    String countText = String.valueOf(status.getIngotsCount());
                    if (status.isRevisedOnTop()) countText += "k";
                    if (status.isScrapOnTop()) countText += "s";
                    countLabel.setText(countText);
                    countLabel.getStyle().set("font-size", "14px");  // Normale Schrift
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

            // Border für Einschränkungen (aus Einstellungen)
            String border = "none";
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
    }
}
