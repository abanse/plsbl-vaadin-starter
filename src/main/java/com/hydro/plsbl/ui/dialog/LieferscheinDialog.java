package com.hydro.plsbl.ui.dialog;

import com.hydro.plsbl.dto.IngotDTO;
import com.hydro.plsbl.entity.transdata.Shipment;
import com.hydro.plsbl.entity.transdata.ShipmentLine;
import com.hydro.plsbl.service.IngotService;
import com.hydro.plsbl.service.LieferscheinPdfService;
import com.hydro.plsbl.service.ShipmentService;
import com.hydro.plsbl.service.StockyardService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
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
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.server.StreamResource;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Dialog zum Suchen und Verwalten von Lieferscheinen
 */
public class LieferscheinDialog extends Dialog {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final ShipmentService shipmentService;
    private final IngotService ingotService;
    private final StockyardService stockyardService;
    private final LieferscheinPdfService pdfService;

    // Filter-Komponenten
    private TextField shipmentNoField;
    private DatePicker fromDatePicker;
    private DatePicker toDatePicker;
    private TextField orderNoField;
    private TextField customerNoField;
    private TextField destinationField;
    private Checkbox noDestinationCheckbox;

    // Grid
    private Grid<Shipment> shipmentGrid;
    private Span countLabel;

    // Buttons
    private Button printButton;

    public LieferscheinDialog(ShipmentService shipmentService,
                              IngotService ingotService,
                              StockyardService stockyardService,
                              LieferscheinPdfService pdfService) {
        this.shipmentService = shipmentService;
        this.ingotService = ingotService;
        this.stockyardService = stockyardService;
        this.pdfService = pdfService;

        setHeaderTitle("Lieferscheine suchen");
        setWidth("900px");
        setHeight("650px");
        setModal(true);
        setDraggable(true);
        setResizable(true);
        setCloseOnOutsideClick(false);

        createContent();
        createFooter();
        loadData();
    }

    private void createContent() {
        VerticalLayout content = new VerticalLayout();
        content.setPadding(true);
        content.setSpacing(true);
        content.setSizeFull();

        // Filter-Bereich
        content.add(createFilterSection());
        content.add(new Hr());

        // Grid
        shipmentGrid = createShipmentGrid();
        content.add(shipmentGrid);
        content.setFlexGrow(1, shipmentGrid);

        // Zähler
        countLabel = new Span("0 Lieferscheine");
        countLabel.getStyle().set("font-size", "12px").set("color", "gray");
        content.add(countLabel);

        add(content);
    }

    private VerticalLayout createFilterSection() {
        VerticalLayout filterLayout = new VerticalLayout();
        filterLayout.setPadding(false);
        filterLayout.setSpacing(true);

        // Zeile 1: Lieferschein-Nr, Datum
        HorizontalLayout row1 = new HorizontalLayout();
        row1.setAlignItems(FlexComponent.Alignment.END);

        shipmentNoField = new TextField("Lieferschein-Nr.");
        shipmentNoField.setWidth("150px");
        shipmentNoField.setClearButtonVisible(true);

        fromDatePicker = new DatePicker("ab Datum");
        fromDatePicker.setValue(LocalDate.now().minusDays(30));
        fromDatePicker.setWidth("150px");

        toDatePicker = new DatePicker("bis Datum");
        toDatePicker.setWidth("150px");

        row1.add(shipmentNoField, fromDatePicker, toDatePicker);

        // Zeile 2: Auftrags-Nr, Kunden-Nr
        HorizontalLayout row2 = new HorizontalLayout();
        row2.setAlignItems(FlexComponent.Alignment.END);

        orderNoField = new TextField("Auftrags-Nr.");
        orderNoField.setWidth("150px");
        orderNoField.setClearButtonVisible(true);

        customerNoField = new TextField("Kunden-Nr.");
        customerNoField.setWidth("150px");
        customerNoField.setClearButtonVisible(true);

        row2.add(orderNoField, customerNoField);

        // Zeile 3: Lieferort
        HorizontalLayout row3 = new HorizontalLayout();
        row3.setAlignItems(FlexComponent.Alignment.END);

        destinationField = new TextField("Lieferort");
        destinationField.setWidth("150px");
        destinationField.setClearButtonVisible(true);

        noDestinationCheckbox = new Checkbox("kein Lieferort");
        noDestinationCheckbox.addValueChangeListener(e -> {
            destinationField.setEnabled(!e.getValue());
        });

        row3.add(destinationField, noDestinationCheckbox);

        filterLayout.add(row1, row2, row3);

        return filterLayout;
    }

    private Grid<Shipment> createShipmentGrid() {
        Grid<Shipment> grid = new Grid<>();
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        grid.setSelectionMode(Grid.SelectionMode.SINGLE);
        grid.setSizeFull();

        grid.addColumn(Shipment::getShipmentNumber)
            .setHeader("Nr.")
            .setWidth("100px")
            .setFlexGrow(0)
            .setSortable(true);

        grid.addColumn(Shipment::getDestination)
            .setHeader("Lieferort")
            .setWidth("100px")
            .setFlexGrow(0)
            .setSortable(true);

        grid.addColumn(Shipment::getOrderNumber)
            .setHeader("Auftrags-Nr.")
            .setWidth("120px")
            .setFlexGrow(0)
            .setSortable(true);

        grid.addColumn(Shipment::getCustomerNumber)
            .setHeader("Kunden-Nr.")
            .setWidth("120px")
            .setFlexGrow(0)
            .setSortable(true);

        grid.addColumn(shipment -> formatDateTime(shipment.getPrinted()))
            .setHeader("gedruckt")
            .setWidth("150px")
            .setFlexGrow(0)
            .setSortable(true);

        // Doppelklick öffnet Details
        grid.addItemDoubleClickListener(e -> showShipmentDetails(e.getItem()));

        // Auswahl-Handler für Buttons
        grid.asSingleSelect().addValueChangeListener(e -> updateButtons());

        return grid;
    }

    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) return "-";
        return dateTime.format(DATE_TIME_FORMAT);
    }

    private void createFooter() {
        // Zähler links
        Span countInfo = new Span();

        // Buttons rechts
        Button filterButton = new Button("filtern", VaadinIcon.FILTER.create());
        filterButton.addClickListener(e -> loadData());

        Button searchButton = new Button("suchen", VaadinIcon.SEARCH.create());
        searchButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        searchButton.addClickListener(e -> loadData());

        printButton = new Button("Lieferschein drucken", VaadinIcon.PRINT.create());
        printButton.setEnabled(false);
        printButton.addClickListener(e -> printSelectedShipment());

        Button newButton = new Button("Neu", VaadinIcon.PLUS.create());
        newButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        newButton.addClickListener(e -> openNewShipmentDialog());

        Button closeButton = new Button("schließen", VaadinIcon.CLOSE.create());
        closeButton.addClickListener(e -> close());

        HorizontalLayout footer = new HorizontalLayout(filterButton, searchButton, printButton, newButton, closeButton);
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        footer.setWidthFull();
        getFooter().add(footer);
    }

    private void updateButtons() {
        Shipment selected = shipmentGrid.asSingleSelect().getValue();
        printButton.setEnabled(selected != null);
    }

    private void loadData() {
        try {
            LocalDateTime fromDate = fromDatePicker.getValue() != null
                ? fromDatePicker.getValue().atStartOfDay()
                : null;

            LocalDateTime toDate = toDatePicker.getValue() != null
                ? toDatePicker.getValue().atTime(LocalTime.MAX)
                : null;

            String destination = noDestinationCheckbox.getValue()
                ? ""  // Leerer String für "kein Lieferort"
                : destinationField.getValue();

            List<Shipment> shipments = shipmentService.search(
                shipmentNoField.getValue(),
                orderNoField.getValue(),
                customerNoField.getValue(),
                destination,
                fromDate,
                toDate
            );

            shipmentGrid.setItems(shipments);
            countLabel.setText(shipments.size() + " Lieferscheine");

        } catch (Exception e) {
            Notification.show("Fehler beim Laden: " + e.getMessage(),
                5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            e.printStackTrace();
        }
    }

    private void showShipmentDetails(Shipment shipment) {
        // Lade Positionen
        List<ShipmentLine> lines = shipmentService.findLinesByShipmentId(shipment.getId());
        int totalWeight = shipmentService.getTotalWeight(shipment.getId());

        // Zeige Details-Dialog
        Dialog detailDialog = new Dialog();
        detailDialog.setHeaderTitle("Lieferschein " + shipment.getShipmentNumber());
        detailDialog.setWidth("700px");
        detailDialog.setHeight("500px");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(true);
        content.setSpacing(true);

        // Kopfdaten
        content.add(new Span("Auftrags-Nr.: " + (shipment.getOrderNumber() != null ? shipment.getOrderNumber() : "-")));
        content.add(new Span("Lieferort: " + (shipment.getDestination() != null ? shipment.getDestination() : "-")));
        content.add(new Span("Kunden-Nr.: " + (shipment.getCustomerNumber() != null ? shipment.getCustomerNumber() : "-")));
        content.add(new Span("Datum: " + formatDateTime(shipment.getDelivered())));

        if (shipment.getAddress() != null && !shipment.getAddress().isEmpty()) {
            content.add(new Hr());
            content.add(new Span("Adresse: " + shipment.getAddress()));
        }

        content.add(new Hr());

        // Positionen-Grid
        Grid<ShipmentLine> lineGrid = new Grid<>();
        lineGrid.addThemeVariants(GridVariant.LUMO_COMPACT);
        lineGrid.setHeight("200px");

        lineGrid.addColumn(ShipmentLine::getPosition).setHeader("Nr").setWidth("50px");
        lineGrid.addColumn(ShipmentLine::getIngotNumber).setHeader("Barren").setAutoWidth(true);
        lineGrid.addColumn(ShipmentLine::getProductNumber).setHeader("Artikel").setAutoWidth(true);
        lineGrid.addColumn(ShipmentLine::getSapProductNumber).setHeader("SAP-Nr").setAutoWidth(true);
        lineGrid.addColumn(l -> l.getWeight() != null ? l.getWeight() + " kg" : "-")
            .setHeader("Gewicht").setWidth("100px");

        lineGrid.setItems(lines);
        content.add(lineGrid);

        // Summe
        Span sumLabel = new Span("Gesamt: " + totalWeight + " kg");
        sumLabel.getStyle().set("font-weight", "bold");
        content.add(sumLabel);

        detailDialog.add(content);

        Button closeBtn = new Button("Schließen", e -> detailDialog.close());
        detailDialog.getFooter().add(closeBtn);

        detailDialog.open();
    }

    private void printSelectedShipment() {
        Shipment selected = shipmentGrid.asSingleSelect().getValue();
        if (selected == null) {
            Notification.show("Bitte wählen Sie einen Lieferschein aus",
                3000, Notification.Position.MIDDLE);
            return;
        }

        try {
            // PDF generieren
            byte[] pdfContent = pdfService.generatePdf(selected.getId());

            // Markiere als gedruckt
            shipmentService.markAsPrinted(selected.getId());

            // PDF als Download anbieten
            String fileName = "Lieferschein_" + selected.getShipmentNumber() + ".pdf";
            StreamResource resource = new StreamResource(fileName,
                () -> new ByteArrayInputStream(pdfContent));
            resource.setContentType("application/pdf");
            resource.setCacheTime(0);

            // Download-Link erstellen und automatisch klicken
            Anchor downloadLink = new Anchor(resource, "");
            downloadLink.setId("pdf-download-link");
            downloadLink.getElement().setAttribute("download", true);
            downloadLink.getStyle().set("display", "none");
            add(downloadLink);

            // Automatisch Download starten
            downloadLink.getElement().executeJs(
                "const link = document.createElement('a');" +
                "link.href = $0.href;" +
                "link.download = $1;" +
                "document.body.appendChild(link);" +
                "link.click();" +
                "document.body.removeChild(link);",
                downloadLink.getElement(), fileName);

            Notification.show("Lieferschein " + selected.getShipmentNumber() + " wurde erstellt",
                3000, Notification.Position.BOTTOM_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            // Grid aktualisieren
            loadData();

        } catch (Exception e) {
            Notification.show("Fehler beim Drucken: " + e.getMessage(),
                5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            e.printStackTrace();
        }
    }

    private void openNewShipmentDialog() {
        // Öffne Dialog zum Erstellen eines neuen Lieferscheins
        NeuerLieferscheinDialog dialog = new NeuerLieferscheinDialog(
            shipmentService, ingotService, stockyardService);
        dialog.setOnCreated(shipment -> {
            loadData();
            Notification.show("Lieferschein " + shipment.getShipmentNumber() + " erstellt",
                3000, Notification.Position.BOTTOM_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });
        dialog.open();
    }
}
