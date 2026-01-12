package com.hydro.plsbl.ui.component;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;

/**
 * Kran-Status-Anzeige
 *
 * Zeigt den Kran-Status inkl. Position, Modus und aktuellem Auftrag an.
 * Wird unten rechts im Lager-Grid angezeigt.
 */
public class CranePositionDisplay extends Div {

    // Position
    private final Span xLabel;
    private final Span yLabel;
    private final Span zLabel;

    // Status
    private final Span modeLabel;
    private final Span jobStateLabel;
    private final Span gripperLabel;

    // Auftrag
    private final Div jobSection;
    private final Span fromLabel;
    private final Span toLabel;

    // Barren im Greifer
    private final Div ingotSection;
    private final Span ingotNoLabel;
    private final Span ingotProductLabel;

    // Warnung
    private final Div warningSection;
    private final Span warningLabel;

    // Daten
    private int positionX;
    private int positionY;
    private int positionZ;
    private String craneMode;
    private String jobState;
    private String gripperState;
    private String fromStockyard;
    private String toStockyard;
    private String ingotNo;
    private String ingotProduct;
    private String incident;

    /**
     * Erstellt eine Kran-Status-Anzeige
     */
    public CranePositionDisplay() {
        addClassName("crane-position-display");
        getStyle()
            .set("display", "flex")
            .set("flex-direction", "column")
            .set("background-color", "rgba(255, 255, 255, 0.95)")
            .set("border", "1px solid #ccc")
            .set("border-radius", "4px")
            .set("padding", "10px 14px")
            .set("font-family", "monospace")
            .set("font-size", "11px")
            .set("line-height", "1.5")
            .set("box-shadow", "0 2px 4px rgba(0,0,0,0.15)")
            .set("min-width", "160px");

        // Titel
        Span title = new Span("Kran-Status");
        title.getStyle()
            .set("font-weight", "bold")
            .set("font-size", "12px")
            .set("color", "#1565C0")
            .set("margin-bottom", "6px");
        add(title);

        // Modus und Status
        modeLabel = createLabel();
        jobStateLabel = createLabel();
        gripperLabel = createLabel();
        add(modeLabel, jobStateLabel, gripperLabel);

        // Trennlinie
        Hr hr1 = new Hr();
        hr1.getStyle().set("margin", "6px 0");
        add(hr1);

        // Position
        Span posTitle = new Span("Position:");
        posTitle.getStyle()
            .set("font-weight", "bold")
            .set("color", "#666")
            .set("font-size", "10px");
        add(posTitle);

        xLabel = createLabel();
        yLabel = createLabel();
        zLabel = createLabel();
        add(xLabel, yLabel, zLabel);

        // Auftrag-Sektion (nur sichtbar wenn Auftrag vorhanden)
        jobSection = new Div();
        jobSection.getStyle().set("margin-top", "6px");
        jobSection.setVisible(false);

        Hr hr2 = new Hr();
        hr2.getStyle().set("margin", "6px 0");
        jobSection.add(hr2);

        Span jobTitle = new Span("Auftrag:");
        jobTitle.getStyle()
            .set("font-weight", "bold")
            .set("color", "#666")
            .set("font-size", "10px");
        jobSection.add(jobTitle);

        fromLabel = createLabel();
        toLabel = createLabel();
        jobSection.add(fromLabel, toLabel);
        add(jobSection);

        // Barren-Sektion (nur sichtbar wenn Barren im Greifer)
        ingotSection = new Div();
        ingotSection.getStyle().set("margin-top", "6px");
        ingotSection.setVisible(false);

        Hr hr3 = new Hr();
        hr3.getStyle().set("margin", "6px 0");
        ingotSection.add(hr3);

        Span ingotTitle = new Span("Barren:");
        ingotTitle.getStyle()
            .set("font-weight", "bold")
            .set("color", "#666")
            .set("font-size", "10px");
        ingotSection.add(ingotTitle);

        ingotNoLabel = createLabel();
        ingotNoLabel.getStyle().set("color", "#3F51B5").set("font-weight", "bold");
        ingotProductLabel = createLabel();
        ingotSection.add(ingotNoLabel, ingotProductLabel);
        add(ingotSection);

        // Warnung-Sektion (nur sichtbar bei Störung)
        warningSection = new Div();
        warningSection.getStyle()
            .set("margin-top", "8px")
            .set("padding", "6px")
            .set("background-color", "#FFEBEE")
            .set("border", "1px solid #F44336")
            .set("border-radius", "3px");
        warningSection.setVisible(false);

        warningLabel = new Span();
        warningLabel.getStyle()
            .set("color", "#C62828")
            .set("font-weight", "bold")
            .set("font-size", "10px");
        warningSection.add(warningLabel);
        add(warningSection);

        updateDisplay();
    }

    private Span createLabel() {
        Span label = new Span();
        label.getStyle()
            .set("color", "#333")
            .set("white-space", "nowrap");
        return label;
    }

    /**
     * Aktualisiert die gesamte Anzeige
     */
    private void updateDisplay() {
        // Position
        xLabel.setText("  X = " + positionX);
        yLabel.setText("  Y = " + positionY);
        zLabel.setText("  Z = " + positionZ);

        // Modus
        String modeText = getModeDisplayText();
        modeLabel.setText("Modus: " + modeText);
        modeLabel.getStyle().set("color", getModeColor());

        // Job-Status
        String jobText = getJobStateDisplayText();
        jobStateLabel.setText("Status: " + jobText);
        jobStateLabel.getStyle().set("color", getJobStateColor());

        // Greifer
        String gripperText = getGripperDisplayText();
        gripperLabel.setText("Greifer: " + gripperText);

        // Auftrag
        boolean hasJob = (fromStockyard != null && !fromStockyard.isEmpty()) ||
                         (toStockyard != null && !toStockyard.isEmpty());
        jobSection.setVisible(hasJob);
        if (hasJob) {
            fromLabel.setText("  Von: " + (fromStockyard != null ? fromStockyard : "-"));
            toLabel.setText("  Nach: " + (toStockyard != null ? toStockyard : "-"));
        }

        // Barren im Greifer
        boolean hasIngot = ingotNo != null && !ingotNo.isEmpty();
        ingotSection.setVisible(hasIngot);
        if (hasIngot) {
            ingotNoLabel.setText("  " + ingotNo);
            ingotProductLabel.setText("  " + (ingotProduct != null ? ingotProduct : "-"));
        }

        // Warnung
        boolean hasIncident = incident != null && !incident.isEmpty() && !"OK".equals(incident);
        warningSection.setVisible(hasIncident);
        if (hasIncident) {
            warningLabel.setText("! " + incident);
        }
    }

    private String getModeDisplayText() {
        if (craneMode == null) return "Unbekannt";
        return switch (craneMode) {
            case "AUTOMATIC" -> "Automatik";
            case "MANUAL" -> "Handbetrieb";
            case "SEMI_AUTOMATIC" -> "Halbautomatik";
            default -> craneMode;
        };
    }

    private String getModeColor() {
        if (craneMode == null) return "#666";
        return switch (craneMode) {
            case "AUTOMATIC" -> "#2E7D32";  // Grün
            case "MANUAL" -> "#F57C00";     // Orange
            default -> "#333";
        };
    }

    private String getJobStateDisplayText() {
        if (jobState == null) return "Unbekannt";
        return switch (jobState) {
            case "IDLE" -> "Bereit";
            case "WORKING" -> "Arbeitet";
            case "WAITING" -> "Wartet";
            case "ERROR" -> "Fehler";
            default -> jobState;
        };
    }

    private String getJobStateColor() {
        if (jobState == null) return "#666";
        return switch (jobState) {
            case "IDLE" -> "#2E7D32";       // Grün
            case "WORKING" -> "#1565C0";    // Blau
            case "WAITING" -> "#F57C00";    // Orange
            case "ERROR" -> "#C62828";      // Rot
            default -> "#333";
        };
    }

    private String getGripperDisplayText() {
        if (gripperState == null) return "Unbekannt";
        return switch (gripperState) {
            case "OPEN" -> "Offen";
            case "CLOSED" -> "Geschlossen";
            case "GRIPPING" -> "Greift";
            default -> gripperState;
        };
    }

    // === Setter-Methoden ===

    /**
     * Setzt die Position
     */
    public void setPosition(int x, int y, int z) {
        this.positionX = x;
        this.positionY = y;
        this.positionZ = z;
        updateDisplay();
    }

    /**
     * Setzt den Kran-Modus
     */
    public void setCraneMode(String mode) {
        this.craneMode = mode;
        updateDisplay();
    }

    /**
     * Setzt den Job-Status
     */
    public void setJobState(String state) {
        this.jobState = state;
        updateDisplay();
    }

    /**
     * Setzt den Greifer-Status
     */
    public void setGripperState(String state) {
        this.gripperState = state;
        updateDisplay();
    }

    /**
     * Setzt den aktuellen Auftrag (von/nach)
     */
    public void setJob(String from, String to) {
        this.fromStockyard = from;
        this.toStockyard = to;
        updateDisplay();
    }

    /**
     * Setzt eine Störungsmeldung
     */
    public void setIncident(String incident) {
        this.incident = incident;
        updateDisplay();
    }

    /**
     * Setzt den Barren im Greifer
     */
    public void setIngot(String ingotNo, String productNo) {
        this.ingotNo = ingotNo;
        this.ingotProduct = productNo;
        updateDisplay();
    }

    /**
     * Setzt alle Status-Daten auf einmal
     */
    public void updateStatus(int x, int y, int z,
                             String mode, String jobState, String gripperState,
                             String from, String to, String incident) {
        updateStatus(x, y, z, mode, jobState, gripperState, from, to, null, null, incident);
    }

    /**
     * Setzt alle Status-Daten auf einmal (inkl. Barren)
     */
    public void updateStatus(int x, int y, int z,
                             String mode, String jobState, String gripperState,
                             String from, String to,
                             String ingotNo, String ingotProduct,
                             String incident) {
        this.positionX = x;
        this.positionY = y;
        this.positionZ = z;
        this.craneMode = mode;
        this.jobState = jobState;
        this.gripperState = gripperState;
        this.fromStockyard = from;
        this.toStockyard = to;
        this.ingotNo = ingotNo;
        this.ingotProduct = ingotProduct;
        this.incident = incident;
        updateDisplay();
    }

    // === Getter-Methoden ===

    public int getPositionX() {
        return positionX;
    }

    public int getPositionY() {
        return positionY;
    }

    public int getPositionZ() {
        return positionZ;
    }

    public String getCraneMode() {
        return craneMode;
    }

    public String getJobState() {
        return jobState;
    }
}
