package com.hydro.plsbl.ui.dialog;

import com.hydro.plsbl.dto.CraneStatusDTO;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * Dialog für detaillierte Kran-Status-Anzeige
 */
public class CraneStatusDialog extends Dialog {

    private final CraneStatusDTO status;

    public CraneStatusDialog(CraneStatusDTO status) {
        this.status = status;

        setHeaderTitle("Kran-Status");
        setWidth("550px");
        setModal(true);
        setDraggable(true);

        VerticalLayout content = new VerticalLayout();
        content.setPadding(true);
        content.setSpacing(true);

        // Modus und Status Section
        content.add(createStatusSection());
        content.add(new Hr());

        // Position Section
        content.add(createPositionSection());
        content.add(new Hr());

        // Auftrag Section (falls vorhanden)
        if (hasJob()) {
            content.add(createJobSection());
            content.add(new Hr());
        }

        // Barren Section (falls vorhanden)
        if (status.hasIngot()) {
            content.add(createIngotSection());
            content.add(new Hr());
        }

        // System Section
        content.add(createSystemSection());

        // Warnung Section (falls vorhanden)
        if (status.hasIncident()) {
            content.add(new Hr());
            content.add(createWarningSection());
        }

        add(content);

        // Footer
        Button closeButton = new Button("Schließen", VaadinIcon.CLOSE.create());
        closeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        closeButton.addClickListener(e -> close());

        HorizontalLayout footer = new HorizontalLayout(closeButton);
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        footer.setWidthFull();
        getFooter().add(footer);
    }

    private Div createStatusSection() {
        Div section = new Div();

        H4 title = new H4("Betriebszustand");
        title.getStyle().set("margin", "0 0 10px 0");
        section.add(title);

        FormLayout form = new FormLayout();
        form.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("300px", 2)
        );

        // Modus
        Span modeSpan = createBadge(status.getModeDisplay(), getModeTheme());
        form.addFormItem(modeSpan, "Kran-Modus");

        // Job-Status
        Span jobStateSpan = createBadge(status.getJobStateDisplay(), getJobStateTheme());
        form.addFormItem(jobStateSpan, "Arbeitsstatus");

        // Greifer
        Span gripperSpan = createBadge(status.getGripperStateDisplay(), getGripperTheme());
        form.addFormItem(gripperSpan, "Greifer");

        section.add(form);
        return section;
    }

    private Div createPositionSection() {
        Div section = new Div();

        H4 title = new H4("Position");
        title.getStyle().set("margin", "0 0 10px 0");
        section.add(title);

        FormLayout form = new FormLayout();
        form.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 3)
        );

        // X, Y, Z Positionen
        int x = status.getXPosition() != null ? status.getXPosition() : 0;
        int y = status.getYPosition() != null ? status.getYPosition() : 0;
        int z = status.getZPosition() != null ? status.getZPosition() : 0;

        Span xSpan = createPositionSpan(x, "mm");
        Span ySpan = createPositionSpan(y, "mm");
        Span zSpan = createPositionSpan(z, "mm");

        form.addFormItem(xSpan, "X-Position");
        form.addFormItem(ySpan, "Y-Position");
        form.addFormItem(zSpan, "Z-Position (Höhe)");

        section.add(form);
        return section;
    }

    private Div createJobSection() {
        Div section = new Div();

        H4 title = new H4("Aktueller Auftrag");
        title.getStyle().set("margin", "0 0 10px 0");
        section.add(title);

        FormLayout form = new FormLayout();
        form.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("300px", 2)
        );

        // Von Lagerplatz
        String fromText = status.getFromStockyardNo() != null ? status.getFromStockyardNo() : "-";
        Span fromSpan = createValueSpan(fromText);
        fromSpan.getStyle().set("color", "#1565C0");
        form.addFormItem(fromSpan, "Von Lagerplatz");

        // Nach Lagerplatz
        String toText = status.getToStockyardNo() != null ? status.getToStockyardNo() : "-";
        Span toSpan = createValueSpan(toText);
        toSpan.getStyle().set("color", "#2E7D32");
        form.addFormItem(toSpan, "Nach Lagerplatz");

        section.add(form);
        return section;
    }

    private Div createIngotSection() {
        Div section = new Div();

        H4 title = new H4("Barren im Greifer");
        title.getStyle().set("margin", "0 0 10px 0");
        section.add(title);

        FormLayout form = new FormLayout();
        form.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("300px", 2)
        );

        // Barren-Nummer
        Span ingotSpan = createValueSpan(status.getIngotNo());
        ingotSpan.getStyle()
            .set("color", "#3F51B5")
            .set("font-weight", "bold")
            .set("font-size", "1.1em");
        form.addFormItem(ingotSpan, "Barren-Nr");

        // Produkt
        String productText = status.getIngotProductNo() != null ? status.getIngotProductNo() : "-";
        form.addFormItem(createValueSpan(productText), "Produkt");

        // Maße (falls vorhanden)
        if (status.getIngotLength() != null && status.getIngotWidth() != null) {
            String dimensions = status.getIngotLength() + " x " + status.getIngotWidth() + " mm";
            form.addFormItem(createValueSpan(dimensions), "Maße (L x B)");
        }

        section.add(form);
        return section;
    }

    private Div createSystemSection() {
        Div section = new Div();

        H4 title = new H4("System");
        title.getStyle().set("margin", "0 0 10px 0");
        section.add(title);

        FormLayout form = new FormLayout();
        form.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("300px", 2)
        );

        // Daemon-Status
        String daemonText = status.getDaemonState() != null ? status.getDaemonState() : "-";
        form.addFormItem(createValueSpan(daemonText), "Daemon-Status");

        // Arbeitsphase
        String workPhaseText = status.getWorkPhase() != null ? status.getWorkPhase() : "-";
        form.addFormItem(createValueSpan(workPhaseText), "Arbeitsphase");

        // Türen
        Boolean doorsOpen = status.getDoorsOpen();
        Span doorsSpan = createBadge(
            doorsOpen != null && doorsOpen ? "Offen" : "Geschlossen",
            doorsOpen != null && doorsOpen ? "error" : "success"
        );
        form.addFormItem(doorsSpan, "Türen");

        // Tore
        Boolean gatesOpen = status.getGatesOpen();
        Span gatesSpan = createBadge(
            gatesOpen != null && gatesOpen ? "Offen" : "Geschlossen",
            gatesOpen != null && gatesOpen ? "error" : "success"
        );
        form.addFormItem(gatesSpan, "Tore");

        section.add(form);
        return section;
    }

    private Div createWarningSection() {
        Div section = new Div();
        section.getStyle()
            .set("background-color", "#FFEBEE")
            .set("border", "1px solid #F44336")
            .set("border-radius", "4px")
            .set("padding", "12px");

        H4 title = new H4("Störung");
        title.getStyle()
            .set("margin", "0 0 10px 0")
            .set("color", "#C62828");
        section.add(title);

        // Incident Code
        Span incidentSpan = new Span(status.getIncident());
        incidentSpan.getStyle()
            .set("font-weight", "bold")
            .set("color", "#C62828")
            .set("font-size", "1.1em");
        section.add(incidentSpan);

        // Incident Text (falls vorhanden)
        String incidentText = status.getIncidentText();
        if (incidentText != null && !incidentText.isEmpty()) {
            Span textSpan = new Span(incidentText);
            textSpan.getStyle()
                .set("display", "block")
                .set("margin-top", "8px")
                .set("color", "#B71C1C");
            section.add(textSpan);
        }

        return section;
    }

    // === Helper Methods ===

    private boolean hasJob() {
        return (status.getFromStockyardNo() != null && !status.getFromStockyardNo().isEmpty()) ||
               (status.getToStockyardNo() != null && !status.getToStockyardNo().isEmpty());
    }

    private String getModeTheme() {
        if (status.isAutomatic()) return "success";
        if (status.isManual()) return "warning";
        return "contrast";
    }

    private String getJobStateTheme() {
        String jobState = status.getJobState();
        if (jobState == null) return "contrast";
        return switch (jobState) {
            case "IDLE" -> "success";
            case "WORKING" -> "primary";
            case "WAITING" -> "warning";
            case "ERROR" -> "error";
            default -> "contrast";
        };
    }

    private String getGripperTheme() {
        String gripperState = status.getGripperState();
        if (gripperState == null) return "contrast";
        return switch (gripperState) {
            case "OPEN" -> "success";
            case "CLOSED" -> "primary";
            case "GRIPPING" -> "warning";
            default -> "contrast";
        };
    }

    private Span createValueSpan(String value) {
        Span span = new Span(value != null ? value : "-");
        span.getStyle().set("font-weight", "500");
        return span;
    }

    private Span createPositionSpan(int value, String unit) {
        Span span = new Span(String.format("%,d %s", value, unit));
        span.getStyle()
            .set("font-weight", "bold")
            .set("font-family", "monospace")
            .set("font-size", "1.1em");
        return span;
    }

    private Span createBadge(String text, String theme) {
        Span badge = new Span(text);
        badge.getElement().getThemeList().add("badge");
        badge.getElement().getThemeList().add(theme);
        return badge;
    }
}
