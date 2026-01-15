package com.hydro.plsbl.ui.component;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

/**
 * Kran-Overlay Komponente
 *
 * Zeigt den Kran als orangen Rahmen über dem Lager-Grid.
 * Kann einen Barren anzeigen wenn der Kran einen transportiert.
 */
public class CraneOverlay extends Div {

    private static final String CRANE_COLOR = "rgba(255, 152, 0, 0.6)";  // Orange, halbtransparent
    private static final String GRIPPER_COLOR = "rgba(139, 69, 19, 0.7)"; // Braun

    private final int size;
    private Div ingotDisplay;
    private Div craneFrame;
    private Span gripperIndicator;

    private String ingotNumber;
    private String productNumber;
    private int gripperHeight;

    /**
     * Erstellt ein Kran-Overlay
     * @param size Größe in Pixel (quadratisch)
     */
    public CraneOverlay(int size) {
        this.size = size;

        addClassName("crane-overlay");
        getStyle()
            .set("width", size + "px")
            .set("height", size + "px")
            .set("position", "relative")
            .set("pointer-events", "none")  // Klicks durchlassen
            // CSS Transitions für sanfte Bewegungen (200ms = Update-Intervall)
            .set("transition", "transform 0.2s linear")
            .set("will-change", "transform");

        createCraneFrame();
        createGripperIndicator();
    }

    /**
     * Setzt die Kran-Position mit Animation
     * @param translateX X-Verschiebung in Pixel
     * @param translateY Y-Verschiebung in Pixel
     */
    public void setTranslatePosition(int translateX, int translateY) {
        getStyle().set("transform", "translate(" + translateX + "px, " + translateY + "px)");
    }

    /**
     * Erstellt den Kran-Rahmen (oranger Rand)
     */
    private void createCraneFrame() {
        craneFrame = new Div();
        craneFrame.getStyle()
            .set("position", "absolute")
            .set("top", "0")
            .set("left", "0")
            .set("width", "100%")
            .set("height", "100%")
            .set("pointer-events", "none");

        int margin = size / 6;
        int thickness = size / 8;
        int innerSize = size - margin * 2;

        // Oberer Balken
        Div top = new Div();
        top.getStyle()
            .set("position", "absolute")
            .set("left", margin + "px")
            .set("top", margin + "px")
            .set("width", innerSize + "px")
            .set("height", thickness + "px")
            .set("background-color", CRANE_COLOR);
        craneFrame.add(top);

        // Linker Balken
        Div left = new Div();
        left.getStyle()
            .set("position", "absolute")
            .set("left", margin + "px")
            .set("top", (margin + thickness) + "px")
            .set("width", thickness + "px")
            .set("height", (innerSize - thickness) + "px")
            .set("background-color", CRANE_COLOR);
        craneFrame.add(left);

        // Unterer Balken
        Div bottom = new Div();
        bottom.getStyle()
            .set("position", "absolute")
            .set("left", (margin + thickness) + "px")
            .set("top", (margin + innerSize - thickness) + "px")
            .set("width", (innerSize - thickness) + "px")
            .set("height", thickness + "px")
            .set("background-color", CRANE_COLOR);
        craneFrame.add(bottom);

        // Rechter Balken
        Div right = new Div();
        right.getStyle()
            .set("position", "absolute")
            .set("left", (margin + innerSize - thickness) + "px")
            .set("top", (margin + thickness) + "px")
            .set("width", thickness + "px")
            .set("height", (innerSize - thickness * 2) + "px")
            .set("background-color", CRANE_COLOR);
        craneFrame.add(right);

        add(craneFrame);
    }

    /**
     * Erstellt den Greifer-Höhen-Indikator (Dreieck rechts)
     */
    private void createGripperIndicator() {
        gripperIndicator = new Span();
        gripperIndicator.getStyle()
            .set("position", "absolute")
            .set("right", "0")
            .set("width", "0")
            .set("height", "0")
            .set("border-top", "8px solid transparent")
            .set("border-bottom", "8px solid transparent")
            .set("border-right", "12px solid " + GRIPPER_COLOR)
            // Animation für Greifer-Höhe (200ms = Update-Intervall)
            .set("transition", "top 0.2s linear");
        add(gripperIndicator);

        updateGripperPosition();
    }

    /**
     * Aktualisiert die Position des Greifer-Indikators basierend auf der Höhe
     */
    private void updateGripperPosition() {
        int margin = size / 6;
        int thickness = size / 8;
        double yOffset = margin + thickness / 2.0;
        double zh = (size - gripperHeight) * 6.0 / 10.0;
        int top = (int) (yOffset + zh - 8);  // -8 für die halbe Dreieckshöhe

        gripperIndicator.getStyle().set("top", top + "px");
    }

    /**
     * Setzt die Greifer-Höhe
     * @param height Höhe in Einheiten (0-100)
     */
    public void setGripperHeight(int height) {
        if (this.gripperHeight != height) {
            this.gripperHeight = height;
            updateGripperPosition();
        }
    }

    /**
     * Zeigt einen Barren im Greifer an
     * @param ingotNumber Barren-Nummer
     * @param productNumber Produkt-Nummer
     * @param length Länge in Pixel
     * @param width Breite in Pixel
     */
    public void setIngot(String ingotNumber, String productNumber, int length, int width) {
        this.ingotNumber = ingotNumber;
        this.productNumber = productNumber;

        // Alte Anzeige entfernen
        if (ingotDisplay != null) {
            remove(ingotDisplay);
            ingotDisplay = null;
        }

        if (ingotNumber != null && length > 0 && width > 0) {
            // Barren zentriert im Kran anzeigen
            ingotDisplay = new Div();
            ingotDisplay.addClassName("crane-ingot");

            // Größe begrenzen
            int maxLen = Math.min(length, size - 20);
            int maxWidth = Math.min(width, size - 20);

            ingotDisplay.getStyle()
                .set("position", "absolute")
                .set("left", ((size - maxLen) / 2) + "px")
                .set("top", ((size - maxWidth) / 2) + "px")
                .set("width", maxLen + "px")
                .set("height", maxWidth + "px")
                .set("background-color", "#7986CB")
                .set("border", "2px solid #3F51B5")
                .set("border-radius", "2px")
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("font-size", "8px")
                .set("color", "white")
                .set("overflow", "hidden")
                // Fade-In Animation
                .set("opacity", "0")
                .set("transition", "opacity 0.3s ease-out");

            // Opacity nach kurzer Verzögerung setzen für Animation
            ingotDisplay.getElement().executeJs(
                "setTimeout(() => this.style.opacity = '1', 10);"
            );

            // Barren-Nummer anzeigen
            Span numberSpan = new Span(ingotNumber);
            numberSpan.getStyle().set("font-weight", "bold");
            ingotDisplay.add(numberSpan);

            // Produkt-Nummer anzeigen (falls vorhanden)
            if (productNumber != null && !productNumber.isEmpty()) {
                Span productSpan = new Span(productNumber);
                productSpan.getStyle().set("font-size", "7px");
                ingotDisplay.add(productSpan);
            }

            add(ingotDisplay);
        }
    }

    /**
     * Entfernt den Barren aus dem Greifer
     */
    public void clearIngot() {
        setIngot(null, null, 0, 0);
    }

    public String getIngotNumber() {
        return ingotNumber;
    }

    public String getProductNumber() {
        return productNumber;
    }

    public int getGripperHeight() {
        return gripperHeight;
    }
}
