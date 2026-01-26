package com.hydro.plsbl.ui.dialog;

import com.hydro.plsbl.dto.IngotDTO;
import com.hydro.plsbl.dto.StockyardDTO;
import com.hydro.plsbl.dto.StockyardStatusDTO;
import com.hydro.plsbl.dto.TransportOrderDTO;
import com.hydro.plsbl.entity.enums.StockyardUsage;
import com.hydro.plsbl.entity.transdata.Shipment;
import com.hydro.plsbl.service.IngotService;
import com.hydro.plsbl.service.LieferscheinPdfService;
import com.hydro.plsbl.service.ShipmentService;
import com.hydro.plsbl.service.StockyardService;
import com.hydro.plsbl.service.TransportOrderService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.server.StreamResource;

import com.hydro.plsbl.plc.PlcService;

import java.io.ByteArrayInputStream;

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
    private final TransportOrderService transportOrderService;
    private final ShipmentService shipmentService;
    private final LieferscheinPdfService pdfService;
    private Consumer<StockyardDTO> onEdit;
    private Consumer<IngotDTO> onIngotEdit;
    private Consumer<Void> onRelocated;
    private Consumer<StockyardDTO> onMerge;
    private Consumer<StockyardDTO> onSplit;
    private Grid<IngotDTO> ingotGrid;
    private Grid<TransportOrderDTO> orderGrid;

    public StockyardInfoDialog(StockyardDTO stockyard, IngotService ingotService,
                               StockyardService stockyardService, PlcService plcService,
                               TransportOrderService transportOrderService,
                               ShipmentService shipmentService, LieferscheinPdfService pdfService) {
        this.stockyard = stockyard;
        this.ingotService = ingotService;
        this.stockyardService = stockyardService;
        this.plcService = plcService;
        this.transportOrderService = transportOrderService;
        this.shipmentService = shipmentService;
        this.pdfService = pdfService;

        setHeaderTitle("Lagerplatz " + stockyard.getYardNumber());
        setWidth("750px");
        setHeight("750px");  // Erhöht für Transportaufträge

        createContent();
        createFooter();
    }

    // Backwards compatibility constructors
    public StockyardInfoDialog(StockyardDTO stockyard, IngotService ingotService,
                               StockyardService stockyardService, PlcService plcService,
                               TransportOrderService transportOrderService) {
        this(stockyard, ingotService, stockyardService, plcService, transportOrderService, null, null);
    }

    public StockyardInfoDialog(StockyardDTO stockyard, IngotService ingotService,
                               StockyardService stockyardService, PlcService plcService) {
        this(stockyard, ingotService, stockyardService, plcService, null, null, null);
    }

    public StockyardInfoDialog(StockyardDTO stockyard, IngotService ingotService, StockyardService stockyardService) {
        this(stockyard, ingotService, stockyardService, null, null, null, null);
    }

    public StockyardInfoDialog(StockyardDTO stockyard, IngotService ingotService) {
        this(stockyard, ingotService, null, null, null, null, null);
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

        // Transportaufträge (wenn Service verfügbar)
        if (transportOrderService != null) {
            content.add(new Hr());
            content.add(new H3("Transportaufträge"));
            content.add(createOrderGrid());
        }

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

        // Löschen-Spalte mit Button
        ingotGrid.addComponentColumn(ingot -> {
            Button deleteBtn = new Button(VaadinIcon.TRASH.create());
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            deleteBtn.getElement().setAttribute("title", "Barren löschen");
            deleteBtn.addClickListener(e -> confirmDeleteIngot(ingot));
            return deleteBtn;
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

    private void confirmDeleteIngot(IngotDTO ingot) {
        ConfirmDialog confirm = new ConfirmDialog();
        confirm.setHeader("Barren löschen?");
        confirm.setText("Möchten Sie den Barren " + ingot.getIngotNo() +
            " wirklich löschen? Diese Aktion kann nicht rückgängig gemacht werden.");
        confirm.setCancelable(true);
        confirm.setCancelText("Abbrechen");
        confirm.setConfirmText("Löschen");
        confirm.setConfirmButtonTheme("error primary");
        confirm.addConfirmListener(e -> deleteIngot(ingot));
        confirm.open();
    }

    private void deleteIngot(IngotDTO ingot) {
        try {
            if (ingotService != null) {
                ingotService.delete(ingot.getId());
                Notification.show("Barren " + ingot.getIngotNo() + " wurde gelöscht",
                    3000, Notification.Position.BOTTOM_START)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                loadIngotData();  // Grid aktualisieren

                // LagerView aktualisieren (Lagerplatz-Anzeige)
                if (onRelocated != null) {
                    onRelocated.accept(null);
                }
            }
        } catch (Exception e) {
            Notification.show("Fehler beim Löschen: " + e.getMessage(),
                5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private Grid<TransportOrderDTO> createOrderGrid() {
        orderGrid = new Grid<>();
        orderGrid.setHeight("150px");
        orderGrid.setWidthFull();

        // Header-Styling - Hellgrüner Hintergrund
        orderGrid.addClassName("styled-header-grid");
        orderGrid.getElement().executeJs(
            "this.shadowRoot.querySelector('thead').style.backgroundColor = '#c8e6c9';" +
            "this.shadowRoot.querySelector('thead').style.color = '#2e7d32';"
        );

        // Spalten
        orderGrid.addColumn(TransportOrderDTO::getTransportNo)
            .setHeader("Auftrag-Nr")
            .setAutoWidth(true)
            .setFlexGrow(0);

        orderGrid.addColumn(order -> order.getStatus() != null ? order.getStatus().getDisplayName() : "-")
            .setHeader("Status")
            .setAutoWidth(true)
            .setFlexGrow(0);

        orderGrid.addColumn(TransportOrderDTO::getFromYardNo)
            .setHeader("Von")
            .setAutoWidth(true)
            .setFlexGrow(0);

        orderGrid.addColumn(TransportOrderDTO::getToYardNo)
            .setHeader("Nach")
            .setAutoWidth(true)
            .setFlexGrow(0);

        orderGrid.addColumn(TransportOrderDTO::getIngotNo)
            .setHeader("Barren")
            .setAutoWidth(true)
            .setFlexGrow(1);

        // Löschen-Spalte mit Button
        orderGrid.addComponentColumn(order -> {
            Button deleteBtn = new Button(VaadinIcon.TRASH.create());
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            deleteBtn.getElement().setAttribute("title", "Auftrag löschen");
            deleteBtn.addClickListener(e -> confirmDeleteOrder(order));
            return deleteBtn;
        }).setHeader("")
          .setWidth("45px")
          .setFlexGrow(0);

        // Daten laden
        loadOrderData();

        return orderGrid;
    }

    private void loadOrderData() {
        if (transportOrderService != null && orderGrid != null) {
            List<TransportOrderDTO> orders = transportOrderService.findByStockyardId(stockyard.getId());
            orderGrid.setItems(orders);
            orderGrid.setVisible(!orders.isEmpty());
        }
    }

    private void confirmDeleteOrder(TransportOrderDTO order) {
        ConfirmDialog confirm = new ConfirmDialog();
        confirm.setHeader("Transportauftrag löschen?");
        confirm.setText("Möchten Sie den Transportauftrag " + order.getTransportNo() +
            " wirklich löschen? Diese Aktion kann nicht rückgängig gemacht werden.");
        confirm.setCancelable(true);
        confirm.setCancelText("Abbrechen");
        confirm.setConfirmText("Löschen");
        confirm.setConfirmButtonTheme("error primary");
        confirm.addConfirmListener(e -> deleteOrder(order));
        confirm.open();
    }

    private void deleteOrder(TransportOrderDTO order) {
        try {
            transportOrderService.delete(order.getId());
            Notification.show("Auftrag " + order.getTransportNo() + " wurde gelöscht",
                3000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            loadOrderData();  // Grid aktualisieren
        } catch (Exception e) {
            Notification.show("Fehler beim Löschen: " + e.getMessage(),
                5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
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
        // Schließen-Button (links)
        Button closeButton = new Button("Schließen", VaadinIcon.CLOSE.create());
        closeButton.addClickListener(e -> close());

        // Spacer für Abstand zwischen Schließen und anderen Buttons
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

        // Liefern ohne Kran Button
        Button deliverButton = new Button("Liefern", VaadinIcon.TRUCK.create());
        deliverButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        deliverButton.setEnabled(shipmentService != null && stockyard.getStatus() != null && !stockyard.getStatus().isEmpty());
        deliverButton.getElement().setAttribute("title", "Barren ohne Kran liefern und Lieferschein erstellen");
        deliverButton.addClickListener(e -> openDeliverDialog());

        Button editButton = new Button("Bearbeiten", VaadinIcon.EDIT.create());
        editButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        editButton.addClickListener(e -> {
            if (onEdit != null) {
                onEdit.accept(stockyard);
            }
            close();
        });

        HorizontalLayout footer = new HorizontalLayout(
            closeButton, spacer, mergeButton, splitButton, relocateButton, deliverButton, editButton);
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

    /**
     * Öffnet den Dialog zum Liefern von Barren ohne Kran
     */
    private void openDeliverDialog() {
        if (shipmentService == null) {
            Notification.show("Lieferschein-Service nicht verfügbar", 3000, Notification.Position.MIDDLE);
            return;
        }

        // Barren auf diesem Lagerplatz laden
        List<IngotDTO> ingots = ingotService.findByStockyardId(stockyard.getId());
        if (ingots.isEmpty()) {
            Notification.show("Keine Barren auf diesem Lagerplatz", 3000, Notification.Position.MIDDLE);
            return;
        }

        // Dialog erstellen
        Dialog deliverDialog = new Dialog();
        deliverDialog.setHeaderTitle("Liefern ohne Kran - " + stockyard.getYardNumber());
        deliverDialog.setWidth("700px");
        deliverDialog.setModal(true);
        deliverDialog.setCloseOnOutsideClick(false);

        VerticalLayout content = new VerticalLayout();
        content.setPadding(true);
        content.setSpacing(true);

        // Lieferschein-Daten
        content.add(new H3("Lieferschein-Daten"));

        FormLayout form = new FormLayout();
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));

        TextField orderNoField = new TextField("Auftrags-Nr.");
        orderNoField.setWidth("150px");

        TextField destinationField = new TextField("Lieferort");
        destinationField.setWidth("100px");
        destinationField.setRequired(true);

        TextField customerNoField = new TextField("Kunden-Nr.");
        customerNoField.setWidth("150px");

        TextArea addressField = new TextArea("Adresse");
        addressField.setWidth("100%");
        addressField.setHeight("60px");

        form.add(orderNoField, destinationField, customerNoField, addressField);
        content.add(form);

        // Barren-Auswahl
        content.add(new Hr());
        content.add(new H3("Barren auswählen (" + ingots.size() + " verfügbar)"));

        Grid<IngotDTO> selectGrid = new Grid<>();
        selectGrid.setItems(ingots);
        selectGrid.setSelectionMode(Grid.SelectionMode.MULTI);
        selectGrid.setHeight("200px");

        selectGrid.addColumn(IngotDTO::getIngotNo).setHeader("Barren-Nr").setAutoWidth(true);
        selectGrid.addColumn(IngotDTO::getProductNo).setHeader("Produkt").setAutoWidth(true);
        selectGrid.addColumn(i -> i.getWeight() != null ? i.getWeight() + " kg" : "-").setHeader("Gewicht").setAutoWidth(true);

        // Alle auswählen
        selectGrid.asMultiSelect().select(ingots);

        content.add(selectGrid);

        // Info-Zeile
        Span infoLabel = new Span(ingots.size() + " Barren ausgewählt");
        infoLabel.getStyle().set("font-weight", "bold");
        content.add(infoLabel);

        selectGrid.asMultiSelect().addValueChangeListener(e -> {
            int count = e.getValue().size();
            int weight = e.getValue().stream().mapToInt(i -> i.getWeight() != null ? i.getWeight() : 0).sum();
            infoLabel.setText(count + " Barren ausgewählt, Gesamtgewicht: " + weight + " kg");
        });

        deliverDialog.add(content);

        // Footer
        Button cancelBtn = new Button("Abbrechen", e -> deliverDialog.close());

        Button deliverBtn = new Button("Lieferschein erstellen", VaadinIcon.CHECK.create());
        deliverBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        deliverBtn.addClickListener(e -> {
            // Validierung
            if (destinationField.isEmpty()) {
                Notification.show("Bitte Lieferort angeben", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_WARNING);
                destinationField.focus();
                return;
            }

            var selectedIngots = selectGrid.asMultiSelect().getValue();
            if (selectedIngots.isEmpty()) {
                Notification.show("Bitte mindestens einen Barren auswählen", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_WARNING);
                return;
            }

            try {
                // Lieferschein erstellen
                Shipment shipment = shipmentService.createShipment(
                    orderNoField.getValue(),
                    destinationField.getValue(),
                    customerNoField.getValue(),
                    addressField.getValue(),
                    new java.util.ArrayList<>(selectedIngots)
                );

                // PDF generieren und Download
                if (pdfService != null) {
                    byte[] pdfContent = pdfService.generatePdf(shipment.getId());
                    String fileName = "Lieferschein_" + shipment.getShipmentNumber() + ".pdf";
                    StreamResource resource = new StreamResource(fileName,
                        () -> new ByteArrayInputStream(pdfContent));
                    resource.setContentType("application/pdf");

                    Anchor downloadLink = new Anchor(resource, "");
                    downloadLink.getElement().setAttribute("download", true);
                    downloadLink.getStyle().set("display", "none");
                    deliverDialog.add(downloadLink);

                    downloadLink.getElement().executeJs(
                        "const link = document.createElement('a');" +
                        "link.href = $0.href;" +
                        "link.download = $1;" +
                        "document.body.appendChild(link);" +
                        "link.click();" +
                        "document.body.removeChild(link);",
                        downloadLink.getElement(), fileName);
                }

                Notification.show("Lieferschein " + shipment.getShipmentNumber() + " erstellt für " +
                    selectedIngots.size() + " Barren", 5000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

                deliverDialog.close();

                // Grids aktualisieren
                loadIngotData();
                if (onRelocated != null) {
                    onRelocated.accept(null);
                }

            } catch (Exception ex) {
                Notification.show("Fehler: " + ex.getMessage(), 5000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
                ex.printStackTrace();
            }
        });

        HorizontalLayout footer = new HorizontalLayout(cancelBtn, deliverBtn);
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        deliverDialog.getFooter().add(footer);

        deliverDialog.open();
    }
}
