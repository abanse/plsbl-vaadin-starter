package com.hydro.plsbl.ui.dialog;

import com.hydro.plsbl.dto.IngotDTO;
import com.hydro.plsbl.dto.StockyardDTO;
import com.hydro.plsbl.dto.StockyardStatusDTO;
import com.hydro.plsbl.entity.enums.StockyardUsage;
import com.hydro.plsbl.service.IngotService;
import com.hydro.plsbl.service.StockyardService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import com.hydro.plsbl.plc.PlcService;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

/**
 * Dialog zur Anzeige von Stockyard-Informationen inkl. Barren
 */
public class StockyardInfoDialog extends Dialog {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final StockyardDTO stockyard;
    private final IngotService ingotService;
    private final StockyardService stockyardService;
    private final PlcService plcService;
    private Consumer<StockyardDTO> onEdit;
    private Consumer<StockyardDTO> onDelete;
    private Consumer<StockyardDTO> onForceDelete;
    private Consumer<IngotDTO> onIngotEdit;
    private Consumer<Void> onRelocated;
    private Consumer<StockyardDTO> onMerge;
    private Consumer<StockyardDTO> onSplit;
    private Grid<IngotDTO> ingotGrid;

    public StockyardInfoDialog(StockyardDTO stockyard, IngotService ingotService,
                               StockyardService stockyardService, PlcService plcService) {
        this.stockyard = stockyard;
        this.ingotService = ingotService;
        this.stockyardService = stockyardService;
        this.plcService = plcService;

        setHeaderTitle("Lagerplatz " + stockyard.getYardNumber());
        setWidth("750px");
        setHeight("650px");

        createContent();
        createFooter();
    }

    // Backwards compatibility constructors
    public StockyardInfoDialog(StockyardDTO stockyard, IngotService ingotService, StockyardService stockyardService) {
        this(stockyard, ingotService, stockyardService, null);
    }

    public StockyardInfoDialog(StockyardDTO stockyard, IngotService ingotService) {
        this(stockyard, ingotService, null, null);
    }

    private void createContent() {
        VerticalLayout content = new VerticalLayout();
        content.setPadding(true);
        content.setSpacing(true);

        // Stammdaten
        content.add(new H3("Stammdaten"));
        content.add(createInfoForm());

        // Status
        content.add(new Hr());
        content.add(new H3("Aktueller Status"));
        content.add(createStatusInfo());

        // Barren-Liste
        content.add(new Hr());
        content.add(new H3("Barren auf diesem Platz"));
        content.add(createIngotGrid());

        // Einschränkungen
        if (!stockyard.isToStockAllowed() || !stockyard.isFromStockAllowed()) {
            content.add(new Hr());
            content.add(new H3("Einschränkungen"));
            content.add(createRestrictionInfo());
        }

        add(content);
    }

    private FormLayout createInfoForm() {
        FormLayout form = new FormLayout();
        form.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 2)
        );

        form.addFormItem(new Span(stockyard.getYardNumber()), "Platznummer");
        form.addFormItem(new Span(stockyard.getType().getDisplayName()), "Typ");
        form.addFormItem(new Span(stockyard.getUsage().getDisplayName()), "Verwendung");
        form.addFormItem(new Span("(" + stockyard.getXCoordinate() + ", " + stockyard.getYCoordinate() + ")"), "Koordinaten");
        form.addFormItem(new Span(String.valueOf(stockyard.getMaxIngots())), "Max. Barren");

        // Position in mm
        form.addFormItem(
            new Span(String.format("X=%d, Y=%d, Z=%d mm",
                stockyard.getXPosition(),
                stockyard.getYPosition(),
                stockyard.getZPosition())),
            "Position"
        );

        // Abmessungen
        form.addFormItem(
            new Span(String.format("%d x %d x %d mm",
                stockyard.getLength(),
                stockyard.getWidth(),
                stockyard.getHeight())),
            "Abmessungen (L×B×H)"
        );

        return form;
    }

    private VerticalLayout createStatusInfo() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(false);

        StockyardStatusDTO status = stockyard.getStatus();

        if (status == null || status.isEmpty()) {
            Span empty = new Span("Lagerplatz ist leer");
            empty.getStyle().set("color", "gray");
            layout.add(empty);
        } else {
            // Belegung
            int count = status.getIngotsCount();
            int max = stockyard.getMaxIngots();

            Span countSpan = new Span(String.format("Barren: %d / %d", count, max));
            countSpan.getStyle().set("font-weight", "bold");

            // Fortschrittsbalken
            HorizontalLayout progressBar = createProgressBar(count, max);

            layout.add(countSpan, progressBar);

            // Produkt
            if (status.getProductNumber() != null) {
                layout.add(new Span("Produkt: " + status.getProductNumber()));
            }

            // Zusätzliche Flags
            if (status.isRevisedOnTop()) {
                Span revised = new Span("! Korrigierter Barren oben");
                revised.getStyle().set("color", "#FF9800");
                layout.add(revised);
            }
            if (status.isScrapOnTop()) {
                Span scrap = new Span("! Schrott-Barren oben");
                scrap.getStyle().set("color", "#F44336");
                layout.add(scrap);
            }
        }

        return layout;
    }

    private Grid<IngotDTO> createIngotGrid() {
        ingotGrid = new Grid<>();
        ingotGrid.setHeight("220px");
        ingotGrid.setWidthFull();

        // Header-Styling - Hellblauer Hintergrund
        ingotGrid.addClassName("styled-header-grid");
        ingotGrid.getElement().executeJs(
            "this.shadowRoot.querySelector('thead').style.backgroundColor = '#bbdefb';" +
            "this.shadowRoot.querySelector('thead').style.color = '#1565c0';"
        );

        // Spalten
        ingotGrid.addColumn(IngotDTO::getPilePosition)
            .setHeader("Pos")
            .setWidth("45px")
            .setFlexGrow(0);

        ingotGrid.addColumn(IngotDTO::getIngotNo)
            .setHeader("Barren-Nr")
            .setAutoWidth(true)
            .setFlexGrow(1);

        ingotGrid.addColumn(IngotDTO::getProductNo)
            .setHeader("Produkt")
            .setAutoWidth(true)
            .setFlexGrow(0);

        ingotGrid.addColumn(ingot -> ingot.getWeight() != null ? ingot.getWeight() + " kg" : "-")
            .setHeader("Gewicht")
            .setAutoWidth(true)
            .setFlexGrow(0);

        // Schrott-Spalte
        ingotGrid.addComponentColumn(ingot -> {
            if (Boolean.TRUE.equals(ingot.getScrap())) {
                Span scrapLabel = new Span("Schrott");
                scrapLabel.getStyle()
                    .set("color", "white")
                    .set("background-color", "#F44336")
                    .set("padding", "2px 6px")
                    .set("border-radius", "4px")
                    .set("font-size", "11px");
                return scrapLabel;
            }
            return new Span("");
        }).setHeader("Schrott")
          .setWidth("70px")
          .setFlexGrow(0);

        // Korrigiert-Spalte
        ingotGrid.addComponentColumn(ingot -> {
            if (Boolean.TRUE.equals(ingot.getRevised())) {
                Span revisedLabel = new Span("Korr.");
                revisedLabel.getStyle()
                    .set("color", "white")
                    .set("background-color", "#FF9800")
                    .set("padding", "2px 6px")
                    .set("border-radius", "4px")
                    .set("font-size", "11px");
                return revisedLabel;
            }
            return new Span("");
        }).setHeader("Korr.")
          .setWidth("60px")
          .setFlexGrow(0);

        // Bearbeiten-Spalte mit Button
        ingotGrid.addComponentColumn(ingot -> {
            Button editBtn = new Button(VaadinIcon.EDIT.create());
            editBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            editBtn.getElement().setAttribute("title", "Barren bearbeiten");
            editBtn.addClickListener(e -> openIngotEditDialog(ingot));
            return editBtn;
        }).setHeader("")
          .setWidth("45px")
          .setFlexGrow(0);

        // Daten laden
        loadIngotData();

        return ingotGrid;
    }

    private void loadIngotData() {
        if (ingotService != null && ingotGrid != null) {
            List<IngotDTO> ingots = ingotService.findByStockyardId(stockyard.getId());
            ingotGrid.setItems(ingots);
            ingotGrid.setVisible(!ingots.isEmpty());
        }
    }

    private void openIngotEditDialog(IngotDTO ingot) {
        try {
            IngotEditDialog editDialog = new IngotEditDialog(ingot, false, stockyardService);
            editDialog.setOnSave(dto -> {
                if (ingotService != null) {
                    ingotService.save(dto);
                    loadIngotData(); // Grid aktualisieren
                }
            });
            editDialog.open();
        } catch (Exception e) {
            Notification.show("Fehler beim Öffnen: " + e.getMessage(), 5000, Notification.Position.MIDDLE);
        }
    }

    private String formatDimensions(IngotDTO ingot) {
        if (ingot.getLength() == null) return "-";
        return String.format("%d×%d×%d",
            ingot.getLength(),
            ingot.getWidth() != null ? ingot.getWidth() : 0,
            ingot.getThickness() != null ? ingot.getThickness() : 0);
    }

    private String formatFlags(IngotDTO ingot) {
        StringBuilder sb = new StringBuilder();
        if (Boolean.TRUE.equals(ingot.getScrap())) sb.append("S");
        if (Boolean.TRUE.equals(ingot.getRevised())) sb.append("K");
        if (Boolean.TRUE.equals(ingot.getHeadSawn())) sb.append("H");
        if (Boolean.TRUE.equals(ingot.getFootSawn())) sb.append("F");
        if (Boolean.TRUE.equals(ingot.getRotated())) sb.append("R");
        if (ingot.isReleased()) sb.append("*");
        return sb.length() > 0 ? sb.toString() : "-";
    }

    private HorizontalLayout createProgressBar(int current, int max) {
        HorizontalLayout bar = new HorizontalLayout();
        bar.setSpacing(false);
        bar.setWidthFull();
        bar.getStyle()
            .set("height", "20px")
            .set("border-radius", "4px")
            .set("overflow", "hidden")
            .set("background-color", "#E0E0E0");

        // Gefüllter Teil
        double percentage = max > 0 ? (double) current / max * 100 : 0;
        Span filled = new Span();
        filled.getStyle()
            .set("width", percentage + "%")
            .set("height", "100%")
            .set("background-color", percentage >= 100 ? "#3F51B5" : "#7986CB");

        bar.add(filled);
        return bar;
    }

    private VerticalLayout createRestrictionInfo() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(false);

        if (!stockyard.isToStockAllowed()) {
            Span span = new Span("X Einlagern nicht erlaubt");
            span.getStyle().set("color", "#FF9800");
            layout.add(span);
        }

        if (!stockyard.isFromStockAllowed()) {
            Span span = new Span("X Auslagern nicht erlaubt");
            span.getStyle().set("color", "#E91E63");
            layout.add(span);
        }

        return layout;
    }

    private void createFooter() {
        // Löschen-Button (links)
        Button deleteButton = new Button("Löschen", VaadinIcon.TRASH.create());
        deleteButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        deleteButton.addClickListener(e -> confirmDelete());

        // Spacer für Abstand zwischen Löschen und anderen Buttons
        Span spacer = new Span();
        spacer.getStyle().set("flex-grow", "1");

        // Merge/Split Buttons
        Button mergeButton = new Button("Zusammenfügen", VaadinIcon.CONNECT.create());
        mergeButton.setEnabled(canMerge());
        mergeButton.getElement().setAttribute("title",
            "Zwei benachbarte kurze Plätze zu einem langen zusammenfügen");
        mergeButton.addClickListener(e -> {
            if (onMerge != null) {
                onMerge.accept(stockyard);
            }
            close();
        });

        Button splitButton = new Button("Teilen", VaadinIcon.SPLIT.create());
        splitButton.setEnabled(canSplit());
        splitButton.getElement().setAttribute("title",
            "Langen Platz in zwei kurze Plätze teilen");
        splitButton.addClickListener(e -> confirmSplit());

        // Aktions-Buttons (rechts)
        Button relocateButton = new Button("Umlagern", VaadinIcon.EXCHANGE.create());
        relocateButton.setEnabled(stockyard.getStatus() != null && !stockyard.getStatus().isEmpty());
        relocateButton.addClickListener(e -> openUmlagernDialog());

        Button editButton = new Button("Bearbeiten", VaadinIcon.EDIT.create());
        editButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        editButton.addClickListener(e -> {
            if (onEdit != null) {
                onEdit.accept(stockyard);
            }
            close();
        });

        Button closeButton = new Button("Schließen", VaadinIcon.CLOSE.create());
        closeButton.addClickListener(e -> close());

        HorizontalLayout footer = new HorizontalLayout(
            deleteButton, spacer, mergeButton, splitButton, relocateButton, editButton, closeButton);
        footer.setWidthFull();

        getFooter().add(footer);
    }

    private boolean canMerge() {
        // Kann zusammengefügt werden wenn: SHORT und leer
        return stockyard.getUsage() == StockyardUsage.SHORT
            && (stockyard.getStatus() == null || stockyard.getStatus().isEmpty());
    }

    private boolean canSplit() {
        // Kann geteilt werden wenn: LONG, leer, und mindestens eine Nachbarposition frei
        // Nutze Service-Methode für vollständige Prüfung inkl. Nachbarpositionen
        if (stockyardService != null) {
            return stockyardService.canSplit(stockyard.getId());
        }
        // Fallback: nur lokale Prüfung
        return stockyard.getUsage() == StockyardUsage.LONG
            && (stockyard.getStatus() == null || stockyard.getStatus().isEmpty());
    }

    private void confirmSplit() {
        ConfirmDialog confirm = new ConfirmDialog();
        confirm.setHeader("Lagerplatz teilen?");
        confirm.setText("Der lange Lagerplatz " + stockyard.getYardNumber() +
            " wird in zwei kurze Lagerplätze geteilt. " +
            "Ein neuer Platz wird rechts (X+1) erstellt.");
        confirm.setCancelable(true);
        confirm.setCancelText("Abbrechen");
        confirm.setConfirmText("Teilen");
        confirm.setConfirmButtonTheme("primary");
        confirm.addConfirmListener(e -> {
            if (onSplit != null) {
                onSplit.accept(stockyard);
            }
            close();
        });
        confirm.open();
    }

    private void confirmDelete() {
        // Prüfen ob Barren vorhanden
        boolean hasIngots = stockyard.getStatus() != null && stockyard.getStatus().getIngotsCount() > 0;

        if (hasIngots) {
            // Force-Delete anbieten
            ConfirmDialog confirm = new ConfirmDialog();
            confirm.setHeader("Lagerplatz mit Barren löschen?");
            confirm.setText("Auf diesem Lagerplatz befinden sich noch " +
                stockyard.getStatus().getIngotsCount() + " Barren. " +
                "Möchten Sie den Lagerplatz FORCIERT löschen? " +
                "Die Barren werden vom Platz entfernt (nicht gelöscht).");
            confirm.setCancelable(true);
            confirm.setCancelText("Abbrechen");
            confirm.setConfirmText("Forciert Löschen");
            confirm.setConfirmButtonTheme("error primary");
            confirm.addConfirmListener(e -> {
                if (onForceDelete != null) {
                    onForceDelete.accept(stockyard);
                }
                close();
            });
            confirm.open();
            return;
        }

        // Normaler Bestätigungsdialog
        ConfirmDialog confirm = new ConfirmDialog();
        confirm.setHeader("Lagerplatz löschen?");
        confirm.setText("Möchten Sie den Lagerplatz " + stockyard.getYardNumber() + " wirklich löschen? " +
            "Diese Aktion kann nicht rückgängig gemacht werden.");
        confirm.setCancelable(true);
        confirm.setCancelText("Abbrechen");
        confirm.setConfirmText("Löschen");
        confirm.setConfirmButtonTheme("error primary");
        confirm.addConfirmListener(e -> {
            if (onDelete != null) {
                onDelete.accept(stockyard);
            }
            close();
        });
        confirm.open();
    }

    private void openUmlagernDialog() {
        try {
            UmlagernDialog dialog = new UmlagernDialog(stockyard, ingotService, stockyardService, plcService);
            dialog.setOnRelocated(v -> {
                // Grid aktualisieren
                loadIngotData();
                // Parent benachrichtigen
                if (onRelocated != null) {
                    onRelocated.accept(null);
                }
            });
            dialog.open();
        } catch (Exception e) {
            Notification.show("Fehler beim Öffnen des Umlagern-Dialogs: " + e.getMessage(),
                5000, Notification.Position.MIDDLE);
            e.printStackTrace();
        }
    }

    public void setOnEdit(Consumer<StockyardDTO> onEdit) {
        this.onEdit = onEdit;
    }

    public void setOnDelete(Consumer<StockyardDTO> onDelete) {
        this.onDelete = onDelete;
    }

    public void setOnForceDelete(Consumer<StockyardDTO> onForceDelete) {
        this.onForceDelete = onForceDelete;
    }

    public void setOnIngotEdit(Consumer<IngotDTO> onIngotEdit) {
        this.onIngotEdit = onIngotEdit;
    }

    public void setOnRelocated(Consumer<Void> onRelocated) {
        this.onRelocated = onRelocated;
    }

    public void setOnMerge(Consumer<StockyardDTO> onMerge) {
        this.onMerge = onMerge;
    }

    public void setOnSplit(Consumer<StockyardDTO> onSplit) {
        this.onSplit = onSplit;
    }
}
