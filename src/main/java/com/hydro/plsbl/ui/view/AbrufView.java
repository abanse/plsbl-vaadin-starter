package com.hydro.plsbl.ui.view;

import com.hydro.plsbl.dto.CalloffDTO;
import com.hydro.plsbl.dto.CalloffSearchCriteria;
import com.hydro.plsbl.dto.DeliveryNoteDTO;
import com.hydro.plsbl.dto.IngotDTO;
import com.hydro.plsbl.entity.transdata.Shipment;
import com.hydro.plsbl.service.CalloffService;
import com.hydro.plsbl.service.DataBroadcaster;
import com.hydro.plsbl.service.IngotService;
import com.hydro.plsbl.service.LieferscheinPdfService;
import com.hydro.plsbl.service.ShipmentService;
import com.hydro.plsbl.ui.MainLayout;
import com.hydro.plsbl.ui.dialog.AbrufeDialog;
import com.hydro.plsbl.ui.dialog.LieferungBestaetigenDialog;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.shared.Registration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

/**
 * Abruf-Ansicht - Übersicht aller Kundenbestellungen (Calloffs)
 *
 * Abrufe kommen über REST API rein und werden nach Alter abgearbeitet.
 * Ein Abruf kann mehrere Barren enthalten, die in Lieferungen (max. 6 Stück)
 * nach und nach ausgeliefert werden.
 */
@Route(value = "abrufe", layout = MainLayout.class)
@PageTitle("Abrufe | PLSBL")
public class AbrufView extends VerticalLayout {

    private static final Logger log = LoggerFactory.getLogger(AbrufView.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final CalloffService calloffService;
    private final IngotService ingotService;
    private final DataBroadcaster dataBroadcaster;
    private final LieferscheinPdfService pdfService;
    private final ShipmentService shipmentService;
    private Registration broadcasterRegistration;

    // UI Components
    private Grid<CalloffDTO> grid;
    private Grid<IngotDTO> ingotGrid;
    private Span countLabel;
    private Span ingotCountLabel;
    private H4 ingotSectionTitle;
    private Button deliverButton;
    private Span selectedIngotCountLabel;

    // Filter-Felder
    private TextField calloffNumberField;
    private TextField orderNumberField;
    private TextField customerNumberField;
    private TextField destinationField;
    private Checkbox incompleteOnlyCheckbox;
    private Checkbox completedOnlyCheckbox;
    private Checkbox notApprovedOnlyCheckbox;

    // Aktions-Buttons
    private Button approveButton;
    private Button revokeButton;
    private Button completeButton;

    // Aktuell selektierter Abruf
    private CalloffDTO selectedCalloff;

    public AbrufView(CalloffService calloffService, IngotService ingotService,
                     DataBroadcaster dataBroadcaster, LieferscheinPdfService pdfService,
                     ShipmentService shipmentService) {
        this.calloffService = calloffService;
        this.ingotService = ingotService;
        this.dataBroadcaster = dataBroadcaster;
        this.pdfService = pdfService;
        this.shipmentService = shipmentService;

        setSizeFull();
        setPadding(true);
        setSpacing(false);

        createHeader();
        createFilterSection();
        createMainContent();
        loadData();
    }

    private void createMainContent() {
        // SplitLayout für Abrufe oben und Barren unten
        SplitLayout splitLayout = new SplitLayout();
        splitLayout.setOrientation(SplitLayout.Orientation.VERTICAL);
        splitLayout.setSizeFull();
        splitLayout.setSplitterPosition(50);

        // Oberer Bereich: Abrufe-Grid mit Aktionen
        VerticalLayout calloffSection = new VerticalLayout();
        calloffSection.setPadding(false);
        calloffSection.setSpacing(true);
        calloffSection.setSizeFull();

        createGrid();
        createActionBar();
        calloffSection.add(grid, getActionBar());
        calloffSection.setFlexGrow(1, grid);

        // Unterer Bereich: Barren für ausgewählten Abruf
        VerticalLayout ingotSection = createIngotSection();

        splitLayout.addToPrimary(calloffSection);
        splitLayout.addToSecondary(ingotSection);

        add(splitLayout);
        setFlexGrow(1, splitLayout);
    }

    private VerticalLayout createIngotSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(true);
        section.setSpacing(true);
        section.setSizeFull();
        section.getStyle().set("background-color", "#fafafa");

        // Header mit Titel und Aktionen
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(Alignment.CENTER);

        ingotSectionTitle = new H4("Verfügbare Barren für Abruf");
        ingotSectionTitle.getStyle().set("margin", "0");

        ingotCountLabel = new Span("0");
        ingotCountLabel.getElement().getThemeList().add("badge");

        // Auswahl-Info und Liefern-Button
        selectedIngotCountLabel = new Span("0 ausgewählt");
        selectedIngotCountLabel.getStyle().set("margin-left", "20px");

        deliverButton = new Button("Liefern", VaadinIcon.TRUCK.create());
        deliverButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        deliverButton.setEnabled(false);
        deliverButton.addClickListener(e -> deliverSelectedIngots());

        header.add(ingotSectionTitle, ingotCountLabel, selectedIngotCountLabel, deliverButton);
        header.expand(ingotSectionTitle);

        // Barren-Grid
        ingotGrid = new Grid<>();
        ingotGrid.setSizeFull();
        ingotGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);

        ingotGrid.addColumn(IngotDTO::getIngotNo)
            .setHeader("Barren-Nr.")
            .setWidth("120px")
            .setFlexGrow(0)
            .setSortable(true);

        ingotGrid.addColumn(IngotDTO::getProductNo)
            .setHeader("Produkt")
            .setWidth("100px")
            .setFlexGrow(0)
            .setSortable(true);

        ingotGrid.addColumn(IngotDTO::getStockyardNo)
            .setHeader("Lagerplatz")
            .setWidth("100px")
            .setFlexGrow(0)
            .setSortable(true);

        ingotGrid.addColumn(IngotDTO::getPilePosition)
            .setHeader("Position")
            .setWidth("80px")
            .setFlexGrow(0)
            .setSortable(true);

        ingotGrid.addColumn(dto -> dto.getLength() != null ? dto.getLength() + " mm" : "-")
            .setHeader("Länge")
            .setWidth("90px")
            .setFlexGrow(0)
            .setSortable(true);

        ingotGrid.addColumn(dto -> dto.getWeight() != null ? dto.getWeight() + " kg" : "-")
            .setHeader("Gewicht")
            .setWidth("90px")
            .setFlexGrow(0)
            .setSortable(true);

        // Status - alle angezeigten Barren sind verfügbar (nicht korrigiert, nicht Schrott)
        ingotGrid.addComponentColumn(dto -> {
            Span badge = new Span("Verfügbar");
            badge.getStyle()
                .set("color", "white")
                .set("background-color", "#4CAF50")
                .set("padding", "2px 8px")
                .set("border-radius", "4px")
                .set("font-size", "11px");
            return badge;
        }).setHeader("Status").setWidth("100px").setFlexGrow(0);

        // Multi-Select aktivieren
        ingotGrid.setSelectionMode(Grid.SelectionMode.MULTI);
        ingotGrid.asMultiSelect().addValueChangeListener(e -> {
            int count = e.getValue().size();
            selectedIngotCountLabel.setText(count + " ausgewählt");

            // Liefern-Button nur aktiv wenn Barren ausgewählt UND Abruf ausgewählt
            boolean canDeliver = count > 0 && selectedCalloff != null && !selectedCalloff.isCompleted();

            // Prüfen ob noch genug offen ist
            if (canDeliver && selectedCalloff.getRemainingAmount() < count) {
                deliverButton.setEnabled(false);
                deliverButton.setText("Zu viele ausgewählt (" + count + "/" + selectedCalloff.getRemainingAmount() + ")");
            } else {
                deliverButton.setEnabled(canDeliver);
                deliverButton.setText("Liefern (" + count + ")");
            }
        });

        // Platzhalter-Text wenn kein Abruf ausgewählt
        ingotGrid.setItems(Collections.emptyList());

        section.add(header, ingotGrid);
        section.setFlexGrow(1, ingotGrid);

        return section;
    }

    private void deliverSelectedIngots() {
        if (selectedCalloff == null) {
            Notification.show("Bitte zuerst einen Abruf auswählen", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_WARNING);
            return;
        }

        var selectedIngots = ingotGrid.asMultiSelect().getValue();
        if (selectedIngots.isEmpty()) {
            Notification.show("Bitte Barren zum Liefern auswählen", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_WARNING);
            return;
        }

        int remaining = selectedCalloff.getRemainingAmount();
        if (selectedIngots.size() > remaining) {
            Notification.show("Zu viele Barren ausgewählt! Offen: " + remaining, 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        try {
            // Lieferschein in Datenbank erstellen (inkl. Barren als geliefert markieren)
            Shipment shipment = shipmentService.createShipment(
                selectedCalloff.getOrderNumber(),
                selectedCalloff.getDestination(),
                selectedCalloff.getCustomerNumber(),
                selectedCalloff.getCustomerAddress(),
                new java.util.ArrayList<>(selectedIngots)
            );

            log.info("Lieferschein {} erstellt fuer Abruf {}",
                shipment.getShipmentNumber(), selectedCalloff.getCalloffNumber());

            // Abruf aktualisieren (AMOUNT_DELIVERED erhoehen)
            calloffService.addDeliveredAmount(selectedCalloff.getId(), selectedIngots.size());

            // DeliveryNoteDTO für Dialog erstellen
            DeliveryNoteDTO deliveryNote = new DeliveryNoteDTO();
            deliveryNote.setId(shipment.getId());
            deliveryNote.setDeliveryNoteNumber(shipment.getShipmentNumber());
            deliveryNote.setCreatedAt(shipment.getDelivered());
            deliveryNote.setCalloffNumber(selectedCalloff.getCalloffNumber());
            deliveryNote.setOrderNumber(selectedCalloff.getOrderNumber());
            deliveryNote.setOrderPosition(selectedCalloff.getOrderPosition());
            deliveryNote.setCustomerNumber(selectedCalloff.getCustomerNumber());
            deliveryNote.setCustomerName(selectedCalloff.getCustomerName());
            deliveryNote.setCustomerAddress(selectedCalloff.getCustomerAddress());
            deliveryNote.setDestination(selectedCalloff.getDestination());
            deliveryNote.setSapProductNumber(selectedCalloff.getSapProductNumber());
            deliveryNote.setDeliveredIngots(new java.util.ArrayList<>(selectedIngots));
            deliveryNote.calculateTotals();

            // Daten neu laden
            loadData();
            loadIngotsForSelectedCalloff();

            // Auswahl zuruecksetzen
            ingotGrid.asMultiSelect().clear();

            // Lieferschein-Dialog anzeigen
            LieferungBestaetigenDialog dialog = new LieferungBestaetigenDialog(deliveryNote, pdfService, shipmentService);
            dialog.open();

            Notification.show("Lieferschein " + shipment.getShipmentNumber() + " erstellt",
                3000, Notification.Position.BOTTOM_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

        } catch (Exception e) {
            log.error("Fehler beim Liefern", e);
            Notification.show("Fehler: " + e.getMessage(), 5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);

        // Registriere für Daten-Updates
        broadcasterRegistration = dataBroadcaster.register(event -> {
            if (event.getType() == DataBroadcaster.DataEventType.CALLOFF_CHANGED ||
                event.getType() == DataBroadcaster.DataEventType.REFRESH_ALL) {
                log.info("AbrufView: Received {} event", event.getType());
                // Hole die aktuelle UI direkt
                getUI().ifPresent(currentUi -> {
                    log.info("AbrufView: Got UI, pushing update...");
                    currentUi.access(() -> {
                        log.info("AbrufView: Inside ui.access(), loading data...");
                        loadData();
                        log.info("AbrufView: Data reloaded via broadcast");
                    });
                });
            }
        });
        log.info("AbrufView attached, broadcaster registered");
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        super.onDetach(detachEvent);
        if (broadcasterRegistration != null) {
            broadcasterRegistration.remove();
            broadcasterRegistration = null;
        }
        log.debug("AbrufView detached, broadcaster unregistered");
    }

    private void createHeader() {
        H3 title = new H3("Abrufe-Übersicht");
        title.getStyle().set("margin", "0");

        countLabel = new Span("0");
        countLabel.getElement().getThemeList().add("badge");
        countLabel.getElement().getThemeList().add("contrast");

        Button refreshButton = new Button("Aktualisieren", VaadinIcon.REFRESH.create());
        refreshButton.addClickListener(e -> loadData());

        Button createTestButton = new Button("Test-Abruf erstellen", VaadinIcon.PLUS.create());
        createTestButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        createTestButton.addClickListener(e -> {
            calloffService.createTestCalloff();
            loadData();
            Notification.show("Test-Abruf erstellt", 3000, Notification.Position.BOTTOM_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });

        HorizontalLayout header = new HorizontalLayout(title, countLabel, refreshButton, createTestButton);
        header.setAlignItems(Alignment.CENTER);
        header.setWidthFull();
        header.expand(title);

        add(header);
    }

    private void createFilterSection() {
        // Filter-Container mit Hintergrund
        VerticalLayout filterSection = new VerticalLayout();
        filterSection.getStyle()
            .set("background-color", "#f5f5f5")
            .set("padding", "15px")
            .set("border-radius", "4px")
            .set("margin-bottom", "10px");
        filterSection.setSpacing(true);
        filterSection.setPadding(false);

        // Zeile 1: Text-Filter
        HorizontalLayout row1 = new HorizontalLayout();
        row1.setSpacing(true);
        row1.setAlignItems(Alignment.END);

        calloffNumberField = new TextField("Abruf-Nr.");
        calloffNumberField.setPlaceholder("z.B. ABR-2025");
        calloffNumberField.setWidth("130px");
        calloffNumberField.setClearButtonVisible(true);

        orderNumberField = new TextField("Auftrags-Nr.");
        orderNumberField.setPlaceholder("z.B. 4500001234");
        orderNumberField.setWidth("130px");
        orderNumberField.setClearButtonVisible(true);

        customerNumberField = new TextField("Kunden-Nr.");
        customerNumberField.setPlaceholder("z.B. 100123");
        customerNumberField.setWidth("100px");
        customerNumberField.setClearButtonVisible(true);

        destinationField = new TextField("Lieferort");
        destinationField.setPlaceholder("z.B. NF2");
        destinationField.setWidth("80px");
        destinationField.setClearButtonVisible(true);

        row1.add(calloffNumberField, orderNumberField, customerNumberField, destinationField);

        // Zeile 2: Checkboxen und Buttons
        HorizontalLayout row2 = new HorizontalLayout();
        row2.setSpacing(true);
        row2.setAlignItems(Alignment.CENTER);
        row2.setWidthFull();

        incompleteOnlyCheckbox = new Checkbox("nur offene Abrufe");
        incompleteOnlyCheckbox.setValue(true);

        completedOnlyCheckbox = new Checkbox("nur erledigte Abrufe");
        completedOnlyCheckbox.setValue(false);

        notApprovedOnlyCheckbox = new Checkbox("nur gesperrte Abrufe");
        notApprovedOnlyCheckbox.setValue(false);

        // Gegenseitiger Ausschluss: offen und erledigt schließen sich aus
        incompleteOnlyCheckbox.addValueChangeListener(e -> {
            if (e.getValue()) completedOnlyCheckbox.setValue(false);
        });
        completedOnlyCheckbox.addValueChangeListener(e -> {
            if (e.getValue()) incompleteOnlyCheckbox.setValue(false);
        });

        HorizontalLayout checkboxes = new HorizontalLayout(
            incompleteOnlyCheckbox, completedOnlyCheckbox, notApprovedOnlyCheckbox);
        checkboxes.setSpacing(true);

        // Spacer
        Span spacer = new Span();
        spacer.getStyle().set("flex-grow", "1");

        // Such-Buttons
        Button searchButton = new Button("Suchen", VaadinIcon.SEARCH.create());
        searchButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        searchButton.addClickListener(e -> loadData());

        Button advancedSearchButton = new Button("Erweiterte Suche", VaadinIcon.OPTIONS.create());
        advancedSearchButton.addClickListener(e -> openAdvancedSearchDialog());

        Button resetButton = new Button("Zurücksetzen", VaadinIcon.CLOSE.create());
        resetButton.addClickListener(e -> resetFilters());

        row2.add(checkboxes, spacer, searchButton, advancedSearchButton, resetButton);

        filterSection.add(row1, row2);
        add(filterSection);
    }

    private void createGrid() {
        grid = new Grid<>();
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);

        // Spalten wie im Bild
        grid.addColumn(CalloffDTO::getCalloffNumber)
            .setHeader("AbrufNr.")
            .setWidth("120px")
            .setFlexGrow(0)
            .setSortable(true);

        grid.addColumn(dto -> dto.getReceived() != null
                ? dto.getReceived().format(DATETIME_FORMATTER) : "-")
            .setHeader("eingegangen")
            .setWidth("140px")
            .setFlexGrow(0)
            .setSortable(true);

        grid.addColumn(dto -> dto.getSapProductNumber() != null ? dto.getSapProductNumber() : "-")
            .setHeader("SAP-Artikel")
            .setWidth("100px")
            .setFlexGrow(0)
            .setSortable(true);

        grid.addColumn(CalloffDTO::getRemainingAmount)
            .setHeader("offen")
            .setWidth("60px")
            .setFlexGrow(0)
            .setSortable(true);

        grid.addColumn(dto -> dto.getAmountRequested() != null ? dto.getAmountRequested() : 0)
            .setHeader("bestellt")
            .setWidth("70px")
            .setFlexGrow(0)
            .setSortable(true);

        grid.addColumn(dto -> dto.getDeliveryDate() != null
                ? dto.getDeliveryDate().format(DATE_FORMATTER) : "-")
            .setHeader("Liefertermin")
            .setWidth("100px")
            .setFlexGrow(0)
            .setSortable(true);

        grid.addColumn(dto -> dto.getDestination() != null ? dto.getDestination() : "-")
            .setHeader("Lieferort")
            .setWidth("80px")
            .setFlexGrow(0)
            .setSortable(true);

        grid.addColumn(CalloffDTO::getOrderDisplay)
            .setHeader("AuftragsNr.")
            .setWidth("120px")
            .setFlexGrow(0)
            .setSortable(true);

        // Freigabe-Badge
        grid.addComponentColumn(dto -> {
            Span badge = new Span(dto.isApproved() ? "Ja" : "Nein");
            badge.getStyle()
                .set("color", "white")
                .set("background-color", dto.isApproved() ? "#4CAF50" : "#9E9E9E")
                .set("padding", "2px 8px")
                .set("border-radius", "4px")
                .set("font-size", "11px");
            return badge;
        }).setHeader("Freigabe").setWidth("80px").setFlexGrow(0);

        // Status-Badge
        grid.addComponentColumn(dto -> {
            String status = dto.getStatusDisplay();
            String color;
            if (dto.isCompleted()) {
                color = "#2196F3"; // Blau - abgeschlossen
            } else if (dto.isApproved()) {
                color = "#4CAF50"; // Grün - genehmigt
            } else {
                color = "#FF9800"; // Orange - offen
            }
            Span badge = new Span(status);
            badge.getStyle()
                .set("color", "white")
                .set("background-color", color)
                .set("padding", "2px 8px")
                .set("border-radius", "4px")
                .set("font-size", "11px");
            return badge;
        }).setHeader("Status").setWidth("100px").setFlexGrow(0);

        // Kunden-Info (flexibel)
        grid.addColumn(dto -> {
            String customer = dto.getCustomerName() != null ? dto.getCustomerName() : "";
            if (dto.getCustomerNumber() != null) {
                customer = "[" + dto.getCustomerNumber() + "] " + customer;
            }
            return customer;
        }).setHeader("Kunde").setFlexGrow(1).setSortable(true);

        // Selektion
        grid.asSingleSelect().addValueChangeListener(e -> {
            selectedCalloff = e.getValue();
            updateActionButtons();
            loadIngotsForSelectedCalloff();
        });

        // Doppelklick für Details (TODO: Dialog)
        grid.addItemDoubleClickListener(event -> {
            CalloffDTO calloff = event.getItem();
            Notification.show("Details für: " + calloff.getCalloffNumber() +
                    "\nKunde: " + calloff.getCustomerName() +
                    "\nOffen: " + calloff.getRemainingAmount() + " / " + calloff.getAmountRequested(),
                5000, Notification.Position.MIDDLE);
        });
    }

    private void loadIngotsForSelectedCalloff() {
        log.info("loadIngotsForSelectedCalloff aufgerufen, selectedCalloff={}", selectedCalloff);

        if (selectedCalloff == null) {
            log.info("Kein Abruf ausgewählt");
            ingotGrid.setItems(Collections.emptyList());
            ingotCountLabel.setText("0");
            ingotSectionTitle.setText("Verfügbare Barren - Bitte Abruf auswählen");
            return;
        }

        if (selectedCalloff.getProductId() == null) {
            log.warn("Abruf {} hat keine PRODUCT_ID!", selectedCalloff.getCalloffNumber());
            ingotGrid.setItems(Collections.emptyList());
            ingotCountLabel.setText("0");
            ingotSectionTitle.setText("Abruf " + selectedCalloff.getCalloffNumber() + " hat kein Produkt zugeordnet!");
            return;
        }

        log.info("Lade Barren für Abruf {} mit PRODUCT_ID={}", selectedCalloff.getCalloffNumber(), selectedCalloff.getProductId());

        try {
            List<IngotDTO> ingots = ingotService.findAvailableForDelivery(selectedCalloff.getProductId());
            ingotGrid.setItems(ingots);
            ingotCountLabel.setText(String.valueOf(ingots.size()));
            ingotSectionTitle.setText("Verfügbare Barren für Abruf " + selectedCalloff.getCalloffNumber() +
                " (Produkt: " + selectedCalloff.getSapProductNumber() + ")");

            log.debug("Loaded {} ingots for calloff {} (productId={})",
                ingots.size(), selectedCalloff.getCalloffNumber(), selectedCalloff.getProductId());
        } catch (Exception e) {
            log.error("Error loading ingots for calloff", e);
            ingotGrid.setItems(Collections.emptyList());
            ingotCountLabel.setText("0");
            Notification.show("Fehler beim Laden der Barren: " + e.getMessage(),
                5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void createActionBar() {
        HorizontalLayout actionBar = new HorizontalLayout();
        actionBar.setSpacing(true);
        actionBar.setWidthFull();
        actionBar.setJustifyContentMode(JustifyContentMode.START);

        approveButton = new Button("Genehmigen", VaadinIcon.CHECK.create());
        approveButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        approveButton.setEnabled(false);
        approveButton.addClickListener(e -> approveSelected());

        revokeButton = new Button("Widerrufen", VaadinIcon.CLOSE_SMALL.create());
        revokeButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        revokeButton.setEnabled(false);
        revokeButton.addClickListener(e -> revokeSelected());

        completeButton = new Button("Abschließen", VaadinIcon.CHECK_CIRCLE.create());
        completeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        completeButton.setEnabled(false);
        completeButton.addClickListener(e -> completeSelected());

        actionBar.add(approveButton, revokeButton, completeButton);
        // actionBar wird in createMainContent hinzugefügt
    }

    private HorizontalLayout getActionBar() {
        HorizontalLayout actionBar = new HorizontalLayout();
        actionBar.setSpacing(true);
        actionBar.setWidthFull();
        actionBar.setJustifyContentMode(JustifyContentMode.START);
        actionBar.add(approveButton, revokeButton, completeButton);
        return actionBar;
    }

    private void loadData() {
        try {
            CalloffSearchCriteria criteria = buildSearchCriteria();
            List<CalloffDTO> calloffs = calloffService.searchByCriteria(criteria);
            grid.setItems(calloffs);
            countLabel.setText(String.valueOf(calloffs.size()));
            log.debug("Loaded {} calloffs", calloffs.size());
        } catch (Exception e) {
            log.error("Error loading calloffs", e);
            grid.setItems();
            Notification.show("Fehler beim Laden: " + e.getMessage(),
                5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private CalloffSearchCriteria buildSearchCriteria() {
        CalloffSearchCriteria criteria = new CalloffSearchCriteria();

        String calloffNo = calloffNumberField.getValue();
        if (calloffNo != null && !calloffNo.trim().isEmpty()) {
            criteria.setCalloffNumber(calloffNo.trim());
        }

        String orderNo = orderNumberField.getValue();
        if (orderNo != null && !orderNo.trim().isEmpty()) {
            criteria.setOrderNumber(orderNo.trim());
        }

        String customerNo = customerNumberField.getValue();
        if (customerNo != null && !customerNo.trim().isEmpty()) {
            criteria.setCustomerNumber(customerNo.trim());
        }

        String dest = destinationField.getValue();
        if (dest != null && !dest.trim().isEmpty()) {
            criteria.setDestination(dest.trim());
        }

        criteria.setIncompleteOnly(incompleteOnlyCheckbox.getValue());
        criteria.setCompletedOnly(completedOnlyCheckbox.getValue());
        criteria.setNotApprovedOnly(notApprovedOnlyCheckbox.getValue());

        return criteria;
    }

    private void openAdvancedSearchDialog() {
        AbrufeDialog dialog = new AbrufeDialog(calloffService);
        dialog.open();
    }

    private void resetFilters() {
        calloffNumberField.clear();
        orderNumberField.clear();
        customerNumberField.clear();
        destinationField.clear();
        incompleteOnlyCheckbox.setValue(true);
        completedOnlyCheckbox.setValue(false);
        notApprovedOnlyCheckbox.setValue(false);
        loadData();
    }

    private void updateActionButtons() {
        if (selectedCalloff == null) {
            approveButton.setEnabled(false);
            revokeButton.setEnabled(false);
            completeButton.setEnabled(false);
            return;
        }

        boolean isApproved = selectedCalloff.isApproved();
        boolean isCompleted = selectedCalloff.isCompleted();

        // Genehmigen: nur wenn nicht genehmigt und nicht abgeschlossen
        approveButton.setEnabled(!isApproved && !isCompleted);

        // Widerrufen: nur wenn genehmigt und nicht abgeschlossen
        revokeButton.setEnabled(isApproved && !isCompleted);

        // Abschließen: nur wenn genehmigt und nicht abgeschlossen
        completeButton.setEnabled(isApproved && !isCompleted);
    }

    private void approveSelected() {
        if (selectedCalloff == null) return;
        try {
            calloffService.approve(selectedCalloff.getId());
            Notification.show("Abruf " + selectedCalloff.getCalloffNumber() + " genehmigt",
                3000, Notification.Position.BOTTOM_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            loadData();
        } catch (Exception e) {
            log.error("Error approving calloff", e);
            Notification.show("Fehler: " + e.getMessage(),
                5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void revokeSelected() {
        if (selectedCalloff == null) return;
        try {
            calloffService.revokeApproval(selectedCalloff.getId());
            Notification.show("Genehmigung für " + selectedCalloff.getCalloffNumber() + " widerrufen",
                3000, Notification.Position.BOTTOM_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_CONTRAST);
            loadData();
        } catch (Exception e) {
            log.error("Error revoking calloff", e);
            Notification.show("Fehler: " + e.getMessage(),
                5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void completeSelected() {
        if (selectedCalloff == null) return;
        try {
            calloffService.complete(selectedCalloff.getId());
            Notification.show("Abruf " + selectedCalloff.getCalloffNumber() + " abgeschlossen",
                3000, Notification.Position.BOTTOM_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            loadData();
        } catch (Exception e) {
            log.error("Error completing calloff", e);
            Notification.show("Fehler: " + e.getMessage(),
                5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
}
