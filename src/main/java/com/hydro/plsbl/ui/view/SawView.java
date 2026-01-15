package com.hydro.plsbl.ui.view;

import com.hydro.plsbl.dto.IngotDTO;
import com.hydro.plsbl.dto.SawStatusDTO;
import com.hydro.plsbl.dto.StockyardDTO;
import com.hydro.plsbl.dto.TransportOrderDTO;
import com.hydro.plsbl.entity.enums.StockyardType;
import com.hydro.plsbl.service.IngotService;
import com.hydro.plsbl.service.SawStatusService;
import com.hydro.plsbl.service.SettingsService;
import com.hydro.plsbl.service.StockyardService;
import com.hydro.plsbl.service.TransportOrderService;
import com.hydro.plsbl.simulator.CraneSimulatorCommand;
import com.hydro.plsbl.simulator.CraneSimulatorService;
import com.hydro.plsbl.ui.MainLayout;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Säge-Ansicht - Einlagerung von der Säge
 * Basiert auf dem Original-Layout aus SawView.fxml
 */
@Route(value = "saege", layout = MainLayout.class)
@PageTitle("Säge | PLSBL")
public class SawView extends VerticalLayout {

    private static final Logger log = LoggerFactory.getLogger(SawView.class);
    private static final int UPDATE_INTERVAL = 2;
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    private static final Long SAW_STOCKYARD_ID = 1001L;  // SAW-01 Lagerplatz ID

    private final SawStatusService sawStatusService;
    private final CraneSimulatorService simulatorService;
    private final StockyardService stockyardService;
    private final SettingsService settingsService;
    private final TransportOrderService transportOrderService;
    private final IngotService ingotService;

    // Einlagerungsmodus
    private ComboBox<SawStatusDTO.PickupMode> pickupModeCombo;
    private Checkbox rotateCheckbox;

    // Einlagerungsauftrag
    private TextField pickupNumberField;
    private TextField receivedField;

    // Position von Säge
    private TextField posXField;
    private TextField posYField;
    private TextField posZField;

    // Errechnete Position
    private TextField computedXField;
    private TextField computedYField;
    private TextField computedZField;

    // Barren-Daten
    private TextField ingotNoField;
    private TextField productNoField;
    private TextField productSuffixField;
    private TextField lengthField;
    private TextField widthField;
    private TextField thicknessField;
    private TextField weightField;

    // Säge-Status Checkboxen
    private Checkbox headSawnCheckbox;
    private Checkbox footSawnCheckbox;
    private Checkbox scrapCheckbox;
    private Checkbox revisedCheckbox;

    // Fehler
    private TextField errorTypeField;
    private TextArea errorMessageArea;

    // Bestätigungen
    private Checkbox returnConfirmedCheckbox;
    private Checkbox recycleConfirmedCheckbox;
    private Checkbox sawnConfirmedCheckbox;

    // Aktions-Buttons
    private Button swapOutButton;
    private Button toLoadingZoneButton;
    private Button toExternalStockButton;
    private Button deliverButton;
    private Button infoButton;
    private Button simulatorStoreButton;

    // Barren-Bearbeitung
    private Button editIngotButton;
    private Button saveIngotButton;
    private Button cancelEditButton;
    private IngotDTO currentIngot;
    private boolean editMode = false;

    // Polling
    private ScheduledExecutorService updateExecutor;
    private ScheduledFuture<?> updateFuture;

    public SawView(SawStatusService sawStatusService,
                   CraneSimulatorService simulatorService,
                   StockyardService stockyardService,
                   SettingsService settingsService,
                   TransportOrderService transportOrderService,
                   IngotService ingotService) {
        this.sawStatusService = sawStatusService;
        this.simulatorService = simulatorService;
        this.stockyardService = stockyardService;
        this.settingsService = settingsService;
        this.transportOrderService = transportOrderService;
        this.ingotService = ingotService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        createContent();
        loadStatus();
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        startUpdates(attachEvent.getUI());
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        stopUpdates();
        super.onDetach(detachEvent);
    }

    private void createContent() {
        // Oberer Bereich: Einlagerungsmodus
        add(createModeSection());
        add(new Hr());

        // Mittlerer Bereich: Positions- und Barren-Daten
        HorizontalLayout middleSection = new HorizontalLayout();
        middleSection.setWidthFull();
        middleSection.setSpacing(true);

        middleSection.add(createPickupSection());
        middleSection.add(createPositionSection());
        middleSection.add(createIngotSection());

        add(middleSection);
        add(new Hr());

        // Unterer Bereich: Fehler und Bestätigungen
        HorizontalLayout bottomSection = new HorizontalLayout();
        bottomSection.setWidthFull();
        bottomSection.setSpacing(true);

        bottomSection.add(createErrorSection());
        bottomSection.add(createConfirmationSection());

        add(bottomSection);
        add(new Hr());

        // Aktions-Buttons
        add(createButtonSection());
    }

    private Div createModeSection() {
        Div section = new Div();
        section.setWidthFull();

        HorizontalLayout layout = new HorizontalLayout();
        layout.setAlignItems(FlexComponent.Alignment.CENTER);
        layout.setSpacing(true);

        // Einlagerungsmodus
        pickupModeCombo = new ComboBox<>("Einlagerungsmodus");
        pickupModeCombo.setItems(SawStatusDTO.PickupMode.values());
        pickupModeCombo.setItemLabelGenerator(SawStatusDTO.PickupMode::getDisplayName);
        pickupModeCombo.setWidth("200px");
        pickupModeCombo.addValueChangeListener(e -> {
            if (e.isFromClient() && e.getValue() != null) {
                sawStatusService.updatePickupMode(e.getValue());
            }
        });

        // Barren drehen
        rotateCheckbox = new Checkbox("Barren drehen?");
        rotateCheckbox.addValueChangeListener(e -> {
            if (e.isFromClient()) {
                sawStatusService.updateRotate(e.getValue());
            }
        });

        layout.add(pickupModeCombo, rotateCheckbox);
        section.add(layout);
        return section;
    }

    private Div createPickupSection() {
        Div section = new Div();
        section.getStyle()
            .set("flex", "1")
            .set("padding", "10px")
            .set("border", "1px solid #e0e0e0")
            .set("border-radius", "4px");

        Span title = new Span("Einlagerungsauftrag");
        title.getStyle()
            .set("font-weight", "bold")
            .set("display", "block")
            .set("margin-bottom", "10px");
        section.add(title);

        FormLayout form = new FormLayout();
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        pickupNumberField = createReadOnlyField("");
        form.addFormItem(pickupNumberField, "Einlagerungs-Nr:");

        receivedField = createReadOnlyField("");
        form.addFormItem(receivedField, "eingegangen:");

        section.add(form);
        return section;
    }

    private Div createPositionSection() {
        Div section = new Div();
        section.getStyle()
            .set("flex", "1")
            .set("padding", "10px")
            .set("border", "1px solid #e0e0e0")
            .set("border-radius", "4px");

        Span title = new Span("Position");
        title.getStyle()
            .set("font-weight", "bold")
            .set("display", "block")
            .set("margin-bottom", "10px");
        section.add(title);

        // Tabellen-Header
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setSpacing(true);
        Span empty = new Span("");
        empty.setWidth("100px");
        Span sawHeader = new Span("von Säge");
        sawHeader.setWidth("80px");
        Span computedHeader = new Span("errechnet");
        computedHeader.setWidth("80px");
        header.add(empty, sawHeader, computedHeader);
        section.add(header);

        // X Position
        HorizontalLayout xRow = createPositionRow("X [mm]:");
        posXField = (TextField) xRow.getComponentAt(1);
        computedXField = (TextField) xRow.getComponentAt(2);
        section.add(xRow);

        // Y Position
        HorizontalLayout yRow = createPositionRow("Y [mm]:");
        posYField = (TextField) yRow.getComponentAt(1);
        computedYField = (TextField) yRow.getComponentAt(2);
        section.add(yRow);

        // Z Position
        HorizontalLayout zRow = createPositionRow("Z [mm]:");
        posZField = (TextField) zRow.getComponentAt(1);
        computedZField = (TextField) zRow.getComponentAt(2);
        section.add(zRow);

        return section;
    }

    private HorizontalLayout createPositionRow(String label) {
        HorizontalLayout row = new HorizontalLayout();
        row.setWidthFull();
        row.setSpacing(true);
        row.setAlignItems(FlexComponent.Alignment.CENTER);

        Span labelSpan = new Span(label);
        labelSpan.setWidth("100px");

        TextField sawField = createReadOnlyField("");
        sawField.setWidth("80px");

        TextField computedField = createReadOnlyField("");
        computedField.setWidth("80px");

        row.add(labelSpan, sawField, computedField);
        return row;
    }

    private Div createIngotSection() {
        Div section = new Div();
        section.getStyle()
            .set("flex", "2")
            .set("padding", "10px")
            .set("border", "1px solid #e0e0e0")
            .set("border-radius", "4px");

        // Header mit Titel und Bearbeiten-Buttons
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        Span title = new Span("Barren-Daten (SAW-01)");
        title.getStyle()
            .set("font-weight", "bold");

        // Bearbeiten-Buttons
        HorizontalLayout editButtons = new HorizontalLayout();
        editButtons.setSpacing(true);

        editIngotButton = new Button("Bearbeiten", VaadinIcon.EDIT.create());
        editIngotButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
        editIngotButton.setEnabled(false);
        editIngotButton.addClickListener(e -> enableEditMode());

        saveIngotButton = new Button("Speichern", VaadinIcon.CHECK.create());
        saveIngotButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_SUCCESS);
        saveIngotButton.setVisible(false);
        saveIngotButton.addClickListener(e -> saveIngot());

        cancelEditButton = new Button("Abbrechen", VaadinIcon.CLOSE.create());
        cancelEditButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
        cancelEditButton.setVisible(false);
        cancelEditButton.addClickListener(e -> cancelEdit());

        editButtons.add(editIngotButton, saveIngotButton, cancelEditButton);
        header.add(title, editButtons);
        section.add(header);

        FormLayout form = new FormLayout();
        form.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 2),
            new FormLayout.ResponsiveStep("400px", 4)
        );

        // Erste Zeile
        ingotNoField = createReadOnlyField("");
        ingotNoField.setWidth("120px");
        form.addFormItem(ingotNoField, "Barren-Nr:");

        productNoField = createReadOnlyField("");
        productNoField.setWidth("180px");
        form.addFormItem(productNoField, "Artikel-Nr:");

        productSuffixField = createReadOnlyField("");
        productSuffixField.setWidth("60px");
        form.addFormItem(productSuffixField, "Suffix:");

        // Platzhalter
        form.add(new Span());

        // Zweite Zeile
        lengthField = createReadOnlyField("");
        lengthField.setWidth("80px");
        form.addFormItem(lengthField, "Länge [mm]:");

        headSawnCheckbox = createStatusCheckbox("Kopf gesägt?");
        form.add(headSawnCheckbox);

        widthField = createReadOnlyField("");
        widthField.setWidth("80px");
        form.addFormItem(widthField, "Breite [mm]:");

        footSawnCheckbox = createStatusCheckbox("Fuß gesägt?");
        form.add(footSawnCheckbox);

        // Dritte Zeile
        thicknessField = createReadOnlyField("");
        thicknessField.setWidth("80px");
        form.addFormItem(thicknessField, "Dicke [mm]:");

        scrapCheckbox = createStatusCheckbox("Schrott?");
        form.add(scrapCheckbox);

        weightField = createReadOnlyField("");
        weightField.setWidth("80px");
        form.addFormItem(weightField, "Gewicht [kg]:");

        revisedCheckbox = createStatusCheckbox("Korrektur?");
        form.add(revisedCheckbox);

        section.add(form);
        return section;
    }

    private Div createErrorSection() {
        Div section = new Div();
        section.getStyle()
            .set("flex", "2")
            .set("padding", "10px")
            .set("border", "1px solid #e0e0e0")
            .set("border-radius", "4px");

        Span title = new Span("Fehler / Hinweis");
        title.getStyle()
            .set("font-weight", "bold")
            .set("display", "block")
            .set("margin-bottom", "10px");
        section.add(title);

        FormLayout form = new FormLayout();
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        errorTypeField = createReadOnlyField("");
        form.addFormItem(errorTypeField, "Fehler:");

        errorMessageArea = new TextArea("Hinweis:");
        errorMessageArea.setReadOnly(true);
        errorMessageArea.setWidthFull();
        errorMessageArea.setHeight("100px");
        errorMessageArea.getStyle()
            .set("--vaadin-input-field-background", "white")
            .set("font-size", "13px");
        form.add(errorMessageArea);

        section.add(form);
        return section;
    }

    private Div createConfirmationSection() {
        Div section = new Div();
        section.getStyle()
            .set("flex", "1")
            .set("padding", "10px")
            .set("border", "1px solid #e0e0e0")
            .set("border-radius", "4px");

        Span title = new Span("Bestätigungen");
        title.getStyle()
            .set("font-weight", "bold")
            .set("display", "block")
            .set("margin-bottom", "10px");
        section.add(title);

        VerticalLayout checkboxes = new VerticalLayout();
        checkboxes.setPadding(false);
        checkboxes.setSpacing(false);

        returnConfirmedCheckbox = createStatusCheckbox("Retoure?");
        recycleConfirmedCheckbox = createStatusCheckbox("Barren-Nr. erneut verwendet?");
        sawnConfirmedCheckbox = createStatusCheckbox("erneut gesägt?");

        checkboxes.add(returnConfirmedCheckbox, recycleConfirmedCheckbox, sawnConfirmedCheckbox);

        // Info-Button
        infoButton = new Button("Info", VaadinIcon.INFO_CIRCLE.create());
        infoButton.setVisible(false);
        infoButton.addClickListener(e -> showInfo());
        checkboxes.add(infoButton);

        section.add(checkboxes);
        return section;
    }

    private Div createButtonSection() {
        Div section = new Div();
        section.setWidthFull();

        HorizontalLayout buttons = new HorizontalLayout();
        buttons.setWidthFull();
        buttons.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        buttons.setSpacing(true);

        swapOutButton = new Button("zu Auslagerplatz", VaadinIcon.ARROWS_LONG_RIGHT.create());
        swapOutButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        swapOutButton.setEnabled(false);
        swapOutButton.addClickListener(e -> swapOut());

        toLoadingZoneButton = new Button("zur Verladezone", VaadinIcon.TRUCK.create());
        toLoadingZoneButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        toLoadingZoneButton.setEnabled(false);
        toLoadingZoneButton.addClickListener(e -> toLoadingZone());

        toExternalStockButton = new Button("extern einlagern", VaadinIcon.STORAGE.create());
        toExternalStockButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        toExternalStockButton.setEnabled(false);
        toExternalStockButton.addClickListener(e -> toExternalStock());

        deliverButton = new Button("liefern", VaadinIcon.PACKAGE.create());
        deliverButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        deliverButton.setEnabled(false);
        deliverButton.addClickListener(e -> deliver());

        // Simulator Einlagern Button
        simulatorStoreButton = new Button("Einlagern (Sim)", VaadinIcon.AUTOMATION.create());
        simulatorStoreButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
        simulatorStoreButton.setEnabled(simulatorService.isRunning());
        simulatorStoreButton.addClickListener(e -> openSimulatorStoreDialog());

        buttons.add(swapOutButton, toLoadingZoneButton, toExternalStockButton, deliverButton, simulatorStoreButton);
        section.add(buttons);
        return section;
    }

    // === Helper Methods ===

    private TextField createReadOnlyField(String value) {
        TextField field = new TextField();
        field.setValue(value);
        field.setReadOnly(true);
        field.getStyle()
            .set("--vaadin-input-field-background", "white")
            .set("font-size", "13px");
        return field;
    }

    private Checkbox createStatusCheckbox(String label) {
        Checkbox cb = new Checkbox(label);
        cb.setReadOnly(true);
        cb.getStyle().set("font-size", "13px");
        return cb;
    }

    private String formatNumber(Integer value) {
        if (value == null || value == 0) return "";
        return String.valueOf(value);
    }

    // === Data Loading ===

    private void startUpdates(UI ui) {
        if (updateExecutor != null) return;

        updateExecutor = Executors.newSingleThreadScheduledExecutor();
        updateFuture = updateExecutor.scheduleAtFixedRate(() -> {
            try {
                ui.access(this::loadStatus);
            } catch (Exception e) {
                log.debug("Update skipped: {}", e.getMessage());
            }
        }, UPDATE_INTERVAL, UPDATE_INTERVAL, TimeUnit.SECONDS);

        log.info("Saw status updates started");
    }

    private void stopUpdates() {
        if (updateFuture != null) {
            updateFuture.cancel(true);
            updateFuture = null;
        }
        if (updateExecutor != null) {
            updateExecutor.shutdownNow();
            updateExecutor = null;
        }
        log.info("Saw status updates stopped");
    }

    private void loadStatus() {
        try {
            Optional<SawStatusDTO> statusOpt = sawStatusService.getCurrentStatus();

            if (statusOpt.isPresent()) {
                updateDisplay(statusOpt.get());
            }

            // Barren von SAW-01 laden (wenn nicht im Bearbeitungsmodus)
            if (!editMode) {
                loadIngotFromSaw();
            }
        } catch (Exception e) {
            log.debug("Error loading saw status: {}", e.getMessage());
        }
    }

    /**
     * Lädt den aktuellen Barren vom SAW-01 Lagerplatz
     */
    private void loadIngotFromSaw() {
        try {
            java.util.List<IngotDTO> ingots = ingotService.findByStockyardId(SAW_STOCKYARD_ID);

            if (!ingots.isEmpty()) {
                // Obersten Barren auf dem Säge-Platz verwenden
                currentIngot = ingots.get(0);
                updateIngotDisplay(currentIngot);
                editIngotButton.setEnabled(true);
                log.debug("Ingot loaded from SAW-01: {}", currentIngot.getIngotNo());
            } else {
                // Kein Barren auf SAW-01
                currentIngot = null;
                clearIngotDisplay();
                editIngotButton.setEnabled(false);
            }
        } catch (Exception e) {
            log.debug("Error loading ingot from SAW-01: {}", e.getMessage());
            currentIngot = null;
            editIngotButton.setEnabled(false);
        }
    }

    /**
     * Aktualisiert die Barren-Anzeige mit den Daten des aktuellen Barrens
     */
    private void updateIngotDisplay(IngotDTO ingot) {
        if (ingot == null) {
            clearIngotDisplay();
            return;
        }

        ingotNoField.setValue(ingot.getIngotNo() != null ? ingot.getIngotNo() : "");
        productNoField.setValue(ingot.getProductNo() != null ? ingot.getProductNo() : "");
        productSuffixField.setValue(ingot.getProductSuffix() != null ? ingot.getProductSuffix() : "");
        lengthField.setValue(formatNumber(ingot.getLength()));
        widthField.setValue(formatNumber(ingot.getWidth()));
        thicknessField.setValue(formatNumber(ingot.getThickness()));
        weightField.setValue(formatNumber(ingot.getWeight()));

        headSawnCheckbox.setValue(ingot.getHeadSawn() != null && ingot.getHeadSawn());
        footSawnCheckbox.setValue(ingot.getFootSawn() != null && ingot.getFootSawn());
        scrapCheckbox.setValue(ingot.getScrap() != null && ingot.getScrap());
        revisedCheckbox.setValue(ingot.getRevised() != null && ingot.getRevised());
    }

    /**
     * Leert die Barren-Anzeige
     */
    private void clearIngotDisplay() {
        ingotNoField.setValue("");
        productNoField.setValue("");
        productSuffixField.setValue("");
        lengthField.setValue("");
        widthField.setValue("");
        thicknessField.setValue("");
        weightField.setValue("");

        headSawnCheckbox.setValue(false);
        footSawnCheckbox.setValue(false);
        scrapCheckbox.setValue(false);
        revisedCheckbox.setValue(false);
    }

    private void updateDisplay(SawStatusDTO status) {
        // Einlagerungsmodus
        pickupModeCombo.setValue(status.getPickupMode());
        rotateCheckbox.setValue(status.isRotate());

        // Einlagerungsauftrag
        pickupNumberField.setValue(status.getPickupNumber() != null ? status.getPickupNumber() : "");
        if (status.getReceived() != null) {
            receivedField.setValue(status.getReceived().format(DATE_TIME_FORMAT));
        } else {
            receivedField.setValue("");
        }

        // Positionen von Säge
        posXField.setValue(formatNumber(status.getPositionX()));
        posYField.setValue(formatNumber(status.getPositionY()));
        posZField.setValue(formatNumber(status.getPositionZ()));

        // Errechnete Positionen
        computedXField.setValue(formatNumber(status.getComputedX()));
        computedYField.setValue(formatNumber(status.getComputedY()));
        computedZField.setValue(formatNumber(status.getComputedZ()));

        // Barren-Daten NUR aktualisieren wenn KEIN Barren von SAW-01 geladen ist
        // und NICHT im Bearbeitungsmodus
        if (currentIngot == null && !editMode) {
            ingotNoField.setValue(status.getIngotNo() != null ? status.getIngotNo() : "");
            productNoField.setValue(status.getProductNo() != null ? status.getProductNo() : "");
            productSuffixField.setValue(status.getProductSuffix() != null ? status.getProductSuffix() : "");
            lengthField.setValue(formatNumber(status.getLength()));
            widthField.setValue(formatNumber(status.getWidth()));
            thicknessField.setValue(formatNumber(status.getThickness()));
            weightField.setValue(formatNumber(status.getWeight()));

            // Säge-Status
            headSawnCheckbox.setValue(status.isHeadSawn());
            footSawnCheckbox.setValue(status.isFootSawn());
            scrapCheckbox.setValue(status.isScrap());
            revisedCheckbox.setValue(status.isRevised());
        }

        // Fehler
        errorTypeField.setValue(status.getErrorType() != null ? status.getErrorType() : "");
        errorMessageArea.setValue(status.getErrorMessage() != null ? status.getErrorMessage() : "");

        // Bestätigungen
        returnConfirmedCheckbox.setValue(status.isReturnConfirmed());
        recycleConfirmedCheckbox.setValue(status.isRecycleConfirmed());
        sawnConfirmedCheckbox.setValue(status.isSawnConfirmed());

        // Fehler-Stil
        if (status.hasError()) {
            errorMessageArea.getStyle().set("color", "red");
        } else {
            errorMessageArea.getStyle().set("color", "blue");
        }

        // Buttons aktivieren/deaktivieren - Barren von SAW-01 oder SawStatus
        boolean hasIngot = currentIngot != null || status.hasIngot();
        boolean inProgress = status.isPickupInProgress();
        swapOutButton.setEnabled(hasIngot && !inProgress && !editMode);
        toLoadingZoneButton.setEnabled(hasIngot && !inProgress && !editMode);
        toExternalStockButton.setEnabled(hasIngot && !inProgress && !editMode);
        deliverButton.setEnabled(hasIngot && !inProgress && !editMode);

        // Simulator-Button Status aktualisieren
        if (simulatorStoreButton != null) {
            simulatorStoreButton.setEnabled(simulatorService.isRunning());
        }
    }

    // === Edit Mode Methods ===

    /**
     * Aktiviert den Bearbeitungsmodus für den Barren
     */
    private void enableEditMode() {
        if (currentIngot == null) {
            Notification.show("Kein Barren zum Bearbeiten vorhanden!", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_WARNING);
            return;
        }

        editMode = true;

        // Felder bearbeitbar machen
        setFieldsEditable(true);

        // Buttons umschalten
        editIngotButton.setVisible(false);
        saveIngotButton.setVisible(true);
        cancelEditButton.setVisible(true);

        log.info("Edit mode enabled for ingot: {}", currentIngot.getIngotNo());
    }

    /**
     * Speichert die Änderungen am Barren
     */
    private void saveIngot() {
        if (currentIngot == null) {
            Notification.show("Kein Barren zum Speichern!", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        try {
            // Werte aus den Feldern übernehmen
            currentIngot.setIngotNo(ingotNoField.getValue());
            currentIngot.setProductSuffix(productSuffixField.getValue());

            // Numerische Werte parsen
            currentIngot.setLength(parseIntOrNull(lengthField.getValue()));
            currentIngot.setWidth(parseIntOrNull(widthField.getValue()));
            currentIngot.setThickness(parseIntOrNull(thicknessField.getValue()));
            currentIngot.setWeight(parseIntOrNull(weightField.getValue()));

            // Checkboxen
            currentIngot.setHeadSawn(headSawnCheckbox.getValue());
            currentIngot.setFootSawn(footSawnCheckbox.getValue());
            currentIngot.setScrap(scrapCheckbox.getValue());
            currentIngot.setRevised(revisedCheckbox.getValue());

            // Speichern
            currentIngot = ingotService.save(currentIngot);

            Notification.show("Barren " + currentIngot.getIngotNo() + " gespeichert!", 3000, Notification.Position.BOTTOM_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            log.info("Ingot saved: {}", currentIngot.getIngotNo());

            // Edit-Modus beenden
            disableEditMode();

        } catch (Exception e) {
            log.error("Error saving ingot", e);
            Notification.show("Fehler beim Speichern: " + e.getMessage(), 5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    /**
     * Bricht die Bearbeitung ab
     */
    private void cancelEdit() {
        disableEditMode();

        // Daten neu laden um Änderungen zu verwerfen
        if (currentIngot != null && currentIngot.getId() != null) {
            ingotService.findById(currentIngot.getId()).ifPresent(this::updateIngotDisplay);
        }

        Notification.show("Bearbeitung abgebrochen", 2000, Notification.Position.BOTTOM_CENTER);
    }

    /**
     * Deaktiviert den Bearbeitungsmodus
     */
    private void disableEditMode() {
        editMode = false;

        // Felder wieder schreibgeschützt
        setFieldsEditable(false);

        // Buttons zurücksetzen
        editIngotButton.setVisible(true);
        editIngotButton.setEnabled(currentIngot != null);
        saveIngotButton.setVisible(false);
        cancelEditButton.setVisible(false);
    }

    /**
     * Setzt die Bearbeitbarkeit der Barren-Felder
     */
    private void setFieldsEditable(boolean editable) {
        ingotNoField.setReadOnly(!editable);
        productSuffixField.setReadOnly(!editable);
        lengthField.setReadOnly(!editable);
        widthField.setReadOnly(!editable);
        thicknessField.setReadOnly(!editable);
        weightField.setReadOnly(!editable);

        headSawnCheckbox.setReadOnly(!editable);
        footSawnCheckbox.setReadOnly(!editable);
        scrapCheckbox.setReadOnly(!editable);
        revisedCheckbox.setReadOnly(!editable);

        // Visuelle Hervorhebung bei Bearbeitung
        String borderColor = editable ? "2px solid var(--lumo-primary-color)" : "1px solid #e0e0e0";
        String background = editable ? "var(--lumo-primary-color-10pct)" : "transparent";

        ingotNoField.getStyle().set("border", editable ? "1px solid var(--lumo-primary-color)" : "none");
        productSuffixField.getStyle().set("border", editable ? "1px solid var(--lumo-primary-color)" : "none");
        lengthField.getStyle().set("border", editable ? "1px solid var(--lumo-primary-color)" : "none");
        widthField.getStyle().set("border", editable ? "1px solid var(--lumo-primary-color)" : "none");
        thicknessField.getStyle().set("border", editable ? "1px solid var(--lumo-primary-color)" : "none");
        weightField.getStyle().set("border", editable ? "1px solid var(--lumo-primary-color)" : "none");
    }

    /**
     * Parst einen String zu Integer oder gibt null zurück
     */
    private Integer parseIntOrNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // === Action Methods ===

    /**
     * Prüft ob ein Barren auf der Säge vorhanden ist (entweder von SAW-01 oder SawStatus)
     */
    private boolean hasIngotOnSaw() {
        if (currentIngot != null) {
            return true;
        }
        Optional<SawStatusDTO> statusOpt = sawStatusService.getCurrentStatus();
        return statusOpt.isPresent() && statusOpt.get().hasIngot();
    }

    /**
     * Gibt die Barren-Nummer des aktuellen Barrens zurück
     */
    private String getCurrentIngotNo() {
        if (currentIngot != null) {
            return currentIngot.getIngotNo();
        }
        Optional<SawStatusDTO> statusOpt = sawStatusService.getCurrentStatus();
        return statusOpt.map(SawStatusDTO::getIngotNo).orElse("");
    }

    /**
     * Öffnet Dialog zur Auswahl eines Auslagerplatzes (Typ O)
     */
    private void swapOut() {
        log.info("Swap out clicked");

        if (!hasIngotOnSaw()) {
            Notification.show("Kein Barren auf der Säge!", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_WARNING);
            return;
        }

        String typeCode = String.valueOf(StockyardType.SWAPOUT.getCode());
        java.util.List<StockyardDTO> swapOutYards = stockyardService.findAvailableByType(typeCode);

        if (swapOutYards.isEmpty()) {
            Notification.show("Keine Auslagerplätze verfügbar!", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_WARNING);
            return;
        }

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Zu Auslagerplatz");
        dialog.setWidth("450px");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);

        Span info = new Span("Barren: " + getCurrentIngotNo());
        info.getStyle().set("font-weight", "bold");
        content.add(info);

        Span infoText = new Span("Wählen Sie den Auslagerplatz:");
        content.add(infoText);

        ComboBox<StockyardDTO> yardCombo = new ComboBox<>("Auslagerplatz");
        yardCombo.setWidthFull();
        yardCombo.setItemLabelGenerator(yard ->
            yard.getYardNumber() + " - " + (yard.getDescription() != null ? yard.getDescription() : "Auslagerung"));
        yardCombo.setItems(swapOutYards);
        if (!swapOutYards.isEmpty()) {
            yardCombo.setValue(swapOutYards.get(0));
        }
        content.add(yardCombo);

        Button cancelButton = new Button("Abbrechen", e -> dialog.close());
        Button confirmButton = new Button("Transport erstellen", VaadinIcon.CHECK.create());
        confirmButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        confirmButton.addClickListener(e -> {
            StockyardDTO target = yardCombo.getValue();
            if (target != null) {
                relocateIngotToYard(target, "Auslagerung von Säge");
                dialog.close();
            }
        });

        dialog.add(content);
        dialog.getFooter().add(cancelButton, confirmButton);
        dialog.open();
    }

    /**
     * Öffnet Bestätigungsdialog für Transport zur Verladezone (Typ L)
     */
    private void toLoadingZone() {
        log.info("To loading zone clicked");

        if (!hasIngotOnSaw()) {
            Notification.show("Kein Barren auf der Säge!", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_WARNING);
            return;
        }

        String typeCode = String.valueOf(StockyardType.LOADING.getCode());
        java.util.List<StockyardDTO> loadingYards = stockyardService.findAvailableByType(typeCode);

        if (loadingYards.isEmpty()) {
            Notification.show("Keine Verladeplätze verfügbar!", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_WARNING);
            return;
        }

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Zur Verladezone");
        dialog.setWidth("450px");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);

        Span info = new Span("Barren: " + getCurrentIngotNo());
        info.getStyle().set("font-weight", "bold");
        content.add(info);

        ComboBox<StockyardDTO> yardCombo = new ComboBox<>("Verladeplatz");
        yardCombo.setWidthFull();
        yardCombo.setItemLabelGenerator(yard ->
            yard.getYardNumber() + " - " + (yard.getDescription() != null ? yard.getDescription() : "Verladung"));
        yardCombo.setItems(loadingYards);
        if (!loadingYards.isEmpty()) {
            yardCombo.setValue(loadingYards.get(0));
        }
        content.add(yardCombo);

        Span confirmText = new Span("Barren wird zur Verladezone transportiert und steht zur Auslieferung bereit.");
        confirmText.getStyle().set("color", "var(--lumo-secondary-text-color)");
        content.add(confirmText);

        Button cancelButton = new Button("Abbrechen", e -> dialog.close());
        Button confirmButton = new Button("Zur Verladung", VaadinIcon.TRUCK.create());
        confirmButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        confirmButton.addClickListener(e -> {
            StockyardDTO target = yardCombo.getValue();
            if (target != null) {
                relocateIngotToYard(target, "Transport zur Verladezone");
                dialog.close();
            }
        });

        dialog.add(content);
        dialog.getFooter().add(cancelButton, confirmButton);
        dialog.open();
    }

    /**
     * Öffnet Dialog zur Auswahl eines externen Lagerplatzes (Typ E)
     */
    private void toExternalStock() {
        log.info("To external stock clicked");

        if (!hasIngotOnSaw()) {
            Notification.show("Kein Barren auf der Säge!", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_WARNING);
            return;
        }

        String typeCode = String.valueOf(StockyardType.EXTERNAL.getCode());
        java.util.List<StockyardDTO> externalYards = stockyardService.findAvailableByType(typeCode);

        if (externalYards.isEmpty()) {
            Notification.show("Keine externen Lagerplätze verfügbar!", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_WARNING);
            return;
        }

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Extern einlagern");
        dialog.setWidth("450px");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);

        Span info = new Span("Barren: " + getCurrentIngotNo());
        info.getStyle().set("font-weight", "bold");
        content.add(info);

        Span infoText = new Span("Wählen Sie den externen Lagerplatz:");
        content.add(infoText);

        ComboBox<StockyardDTO> yardCombo = new ComboBox<>("Externer Lagerplatz");
        yardCombo.setWidthFull();
        yardCombo.setItemLabelGenerator(yard ->
            yard.getYardNumber() + (yard.getStatus() != null ? " - " + yard.getStatus().getIngotsCount() + " Barren" : " - leer"));
        yardCombo.setItems(externalYards);
        if (!externalYards.isEmpty()) {
            yardCombo.setValue(externalYards.get(0));
        }
        content.add(yardCombo);

        Button cancelButton = new Button("Abbrechen", e -> dialog.close());
        Button confirmButton = new Button("Extern einlagern", VaadinIcon.STORAGE.create());
        confirmButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        confirmButton.addClickListener(e -> {
            StockyardDTO target = yardCombo.getValue();
            if (target != null) {
                relocateIngotToYard(target, "Externe Einlagerung von Säge");
                dialog.close();
            }
        });

        dialog.add(content);
        dialog.getFooter().add(cancelButton, confirmButton);
        dialog.open();
    }

    /**
     * Öffnet Bestätigungsdialog für direkte Lieferung
     */
    private void deliver() {
        log.info("Deliver clicked");

        if (!hasIngotOnSaw()) {
            Notification.show("Kein Barren auf der Säge!", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_WARNING);
            return;
        }

        // Ausgang-Platz finden
        String typeCode = String.valueOf(StockyardType.AUSGANG.getCode());
        java.util.List<StockyardDTO> exitYards = stockyardService.findAvailableByType(typeCode);

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Liefern");
        dialog.setWidth("450px");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);

        Span info = new Span("Barren: " + getCurrentIngotNo());
        info.getStyle().set("font-weight", "bold");
        content.add(info);

        // Produkt und Maße von currentIngot verwenden wenn verfügbar
        if (currentIngot != null) {
            if (currentIngot.getProductNo() != null) {
                Span product = new Span("Artikel: " + currentIngot.getProductNo() +
                    (currentIngot.getProductSuffix() != null ? "-" + currentIngot.getProductSuffix() : ""));
                content.add(product);
            }

            Span dimensions = new Span(String.format("Maße: %d x %d x %d mm, %d kg",
                currentIngot.getLength() != null ? currentIngot.getLength() : 0,
                currentIngot.getWidth() != null ? currentIngot.getWidth() : 0,
                currentIngot.getThickness() != null ? currentIngot.getThickness() : 0,
                currentIngot.getWeight() != null ? currentIngot.getWeight() : 0));
            content.add(dimensions);
        }

        content.add(new Hr());

        Span confirmText = new Span("Der Barren wird direkt zur Auslieferung freigegeben.");
        confirmText.getStyle().set("color", "var(--lumo-primary-color)").set("font-weight", "bold");
        content.add(confirmText);

        Button cancelButton = new Button("Abbrechen", e -> dialog.close());
        Button confirmButton = new Button("Liefern", VaadinIcon.PACKAGE.create());
        confirmButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        confirmButton.addClickListener(e -> {
            if (!exitYards.isEmpty()) {
                relocateIngotToYard(exitYards.get(0), "Direkte Lieferung von Säge");
            } else {
                // Falls kein Ausgang-Platz definiert ist, zur ersten Verladezone
                String loadingCode = String.valueOf(StockyardType.LOADING.getCode());
                java.util.List<StockyardDTO> loadingYards = stockyardService.findAvailableByType(loadingCode);
                if (!loadingYards.isEmpty()) {
                    relocateIngotToYard(loadingYards.get(0), "Direkte Lieferung von Säge");
                } else {
                    Notification.show("Kein Auslieferungsplatz verfügbar!", 3000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                }
            }
            dialog.close();
        });

        dialog.add(content);
        dialog.getFooter().add(cancelButton, confirmButton);
        dialog.open();
    }

    /**
     * Zeigt Info-Dialog mit Barren-Details
     */
    private void showInfo() {
        log.info("Info clicked");

        Optional<SawStatusDTO> statusOpt = sawStatusService.getCurrentStatus();
        if (statusOpt.isEmpty()) {
            Notification.show("Kein Status verfügbar!", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_WARNING);
            return;
        }

        SawStatusDTO status = statusOpt.get();

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Barren-Information");
        dialog.setWidth("500px");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);

        // Barren-Grunddaten
        FormLayout form = new FormLayout();
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));

        addInfoField(form, "Barren-Nr:", status.getIngotNo());
        addInfoField(form, "Artikel-Nr:", status.getProductNo());
        addInfoField(form, "Suffix:", status.getProductSuffix());
        addInfoField(form, "Einlagerungs-Nr:", status.getPickupNumber());

        content.add(form);
        content.add(new Hr());

        // Maße
        Span dimensionsTitle = new Span("Maße");
        dimensionsTitle.getStyle().set("font-weight", "bold");
        content.add(dimensionsTitle);

        FormLayout dimensionsForm = new FormLayout();
        dimensionsForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));
        addInfoField(dimensionsForm, "Länge:", formatMm(status.getLength()));
        addInfoField(dimensionsForm, "Breite:", formatMm(status.getWidth()));
        addInfoField(dimensionsForm, "Dicke:", formatMm(status.getThickness()));
        addInfoField(dimensionsForm, "Gewicht:", status.getWeight() != null ? status.getWeight() + " kg" : "-");
        content.add(dimensionsForm);

        content.add(new Hr());

        // Status-Flags
        Span statusTitle = new Span("Status");
        statusTitle.getStyle().set("font-weight", "bold");
        content.add(statusTitle);

        HorizontalLayout flags = new HorizontalLayout();
        flags.setSpacing(true);
        flags.setWidthFull();

        addStatusBadge(flags, "Kopf gesägt", status.isHeadSawn());
        addStatusBadge(flags, "Fuß gesägt", status.isFootSawn());
        addStatusBadge(flags, "Schrott", status.isScrap());
        addStatusBadge(flags, "Korrektur", status.isRevised());
        content.add(flags);

        // Positionen
        if (status.getPositionX() != null || status.getComputedX() != null) {
            content.add(new Hr());
            Span posTitle = new Span("Positionen");
            posTitle.getStyle().set("font-weight", "bold");
            content.add(posTitle);

            FormLayout posForm = new FormLayout();
            posForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));
            addInfoField(posForm, "Von Säge (X/Y/Z):",
                String.format("%s / %s / %s",
                    formatMm(status.getPositionX()),
                    formatMm(status.getPositionY()),
                    formatMm(status.getPositionZ())));
            addInfoField(posForm, "Errechnet (X/Y/Z):",
                String.format("%s / %s / %s",
                    formatMm(status.getComputedX()),
                    formatMm(status.getComputedY()),
                    formatMm(status.getComputedZ())));
            content.add(posForm);
        }

        Button closeButton = new Button("Schließen", e -> dialog.close());
        closeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.add(content);
        dialog.getFooter().add(closeButton);
        dialog.open();
    }

    // === Helper Methods für Dialoge ===

    private void addInfoField(FormLayout form, String label, String value) {
        TextField field = new TextField(label);
        field.setValue(value != null ? value : "-");
        field.setReadOnly(true);
        field.getStyle().set("--vaadin-input-field-background", "white");
        form.add(field);
    }

    private void addStatusBadge(HorizontalLayout layout, String label, boolean active) {
        Span badge = new Span(label);
        badge.getStyle()
            .set("padding", "4px 8px")
            .set("border-radius", "4px")
            .set("font-size", "12px");
        if (active) {
            badge.getStyle()
                .set("background-color", "var(--lumo-success-color-10pct)")
                .set("color", "var(--lumo-success-text-color)");
        } else {
            badge.getStyle()
                .set("background-color", "var(--lumo-contrast-10pct)")
                .set("color", "var(--lumo-secondary-text-color)");
        }
        layout.add(badge);
    }

    private String formatMm(Integer value) {
        if (value == null || value == 0) return "-";
        return value + " mm";
    }

    /**
     * Lagert den aktuellen Barren von SAW-01 auf den Ziel-Lagerplatz um
     */
    private void relocateIngotToYard(StockyardDTO targetYard, String description) {
        if (currentIngot == null || currentIngot.getId() == null) {
            Notification.show("Kein Barren zum Umlagern vorhanden!", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        try {
            // Barren umlagern
            ingotService.relocate(currentIngot.getId(), targetYard.getId());

            Notification.show(
                String.format("Barren %s umgelagert: SAW-01 → %s",
                    currentIngot.getIngotNo(), targetYard.getYardNumber()),
                5000, Notification.Position.BOTTOM_CENTER
            ).addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            log.info("Ingot {} relocated from SAW-01 to {}: {}",
                currentIngot.getIngotNo(), targetYard.getYardNumber(), description);

            // Barren-Anzeige leeren und neu laden
            currentIngot = null;
            clearIngotDisplay();
            editIngotButton.setEnabled(false);

        } catch (Exception e) {
            log.error("Error relocating ingot", e);
            Notification.show("Fehler beim Umlagern: " + e.getMessage(),
                5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    /**
     * Erstellt einen Transport-Auftrag von der Säge zum Ziel-Lagerplatz
     */
    private void createTransportToYard(SawStatusDTO sawStatus, StockyardDTO targetYard, String description) {
        try {
            // Säge-Position finden
            String sawTypeCode = String.valueOf(StockyardType.SAW.getCode());
            java.util.List<StockyardDTO> sawYards = stockyardService.findAvailableByType(sawTypeCode);
            Long sawYardId = sawYards.isEmpty() ? null : sawYards.get(0).getId();

            // Transport-Auftrag erstellen
            TransportOrderDTO order = new TransportOrderDTO();
            order.setTransportNo(generateTransportNo());
            order.setNormText(description);
            order.setFromYardId(sawYardId);
            order.setFromPilePosition(1);
            order.setToYardId(targetYard.getId());
            order.setPriority(10); // Hohe Priorität für Säge-Aufträge

            TransportOrderDTO saved = transportOrderService.save(order);

            Notification.show(
                String.format("Transport-Auftrag %s erstellt: Säge → %s",
                    saved.getTransportNo(), targetYard.getYardNumber()),
                5000, Notification.Position.BOTTOM_CENTER
            ).addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            log.info("Transport order created: {} -> {}", saved.getTransportNo(), targetYard.getYardNumber());

        } catch (Exception e) {
            log.error("Error creating transport order", e);
            Notification.show("Fehler beim Erstellen des Transport-Auftrags: " + e.getMessage(),
                5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private String generateTransportNo() {
        String prefix = "TA-SAW-";
        long timestamp = System.currentTimeMillis() % 100000;
        return prefix + String.format("%05d", timestamp);
    }

    // === Simulator Methods ===

    /**
     * Öffnet den Dialog zur Auswahl des Ziel-Lagerplatzes für die Simulator-Einlagerung
     */
    private void openSimulatorStoreDialog() {
        if (!simulatorService.isRunning()) {
            Notification.show("Simulator ist nicht gestartet!", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Einlagern (Simulator)");
        dialog.setWidth("400px");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);

        // Info-Text
        Span info = new Span("Wählen Sie den Ziel-Lagerplatz für die Simulator-Einlagerung:");
        content.add(info);

        // Lagerplatz-Auswahl
        ComboBox<StockyardDTO> stockyardCombo = new ComboBox<>("Ziel-Lagerplatz");
        stockyardCombo.setWidthFull();
        stockyardCombo.setItemLabelGenerator(yard ->
            yard.getYardNumber() + " (" + yard.getXCoordinate() + "/" + yard.getYCoordinate() + ")" +
            (yard.getStatus() != null ? " - " + yard.getStatus().getIngotsCount() + " Barren" : " - leer")
        );

        // Verfügbare Lagerplätze laden (nicht volle)
        java.util.List<StockyardDTO> destinations = stockyardService.findAvailableDestinations();
        stockyardCombo.setItems(destinations);

        if (destinations.isEmpty()) {
            Notification.show("Keine verfügbaren Ziel-Lagerplätze gefunden!", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_WARNING);
            return;
        }

        content.add(stockyardCombo);

        // Buttons
        Button cancelButton = new Button("Abbrechen", e -> dialog.close());

        Button startButton = new Button("Einlagern starten", VaadinIcon.PLAY.create());
        startButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        startButton.setEnabled(false);
        startButton.addClickListener(e -> {
            StockyardDTO destination = stockyardCombo.getValue();
            if (destination != null) {
                sendSimulatorStoreCommand(destination);
                dialog.close();
            }
        });

        // Button aktivieren wenn Auswahl getroffen
        stockyardCombo.addValueChangeListener(e -> startButton.setEnabled(e.getValue() != null));

        dialog.add(content);
        dialog.getFooter().add(cancelButton, startButton);
        dialog.open();
    }

    /**
     * Sendet einen Einlagerungs-Befehl an den Simulator (Säge -> Lagerplatz)
     */
    private void sendSimulatorStoreCommand(StockyardDTO destination) {
        log.info("Simulator store: Säge -> {}", destination.getYardNumber());

        // Ziel-Position in mm aus Grid-Koordinaten berechnen (via SettingsService)
        int releaseX = settingsService.gridToMmX(destination.getXCoordinate());
        int releaseY = settingsService.gridToMmY(destination.getYCoordinate());
        int releaseZ = destination.getZPosition() > 0 ? destination.getZPosition() : 2000;

        // Säge-Position aus Einstellungen
        int sawX = settingsService.getSawX();
        int sawY = settingsService.getSawY();
        int sawZ = settingsService.getSawZ();

        // Kommando erstellen: Pickup von Säge, Release auf Lagerplatz
        CraneSimulatorCommand cmd = CraneSimulatorCommand.builder()
            .pickup(sawX, sawY, sawZ)
            .release(releaseX, releaseY, releaseZ)
            .fromStockyard(null)  // Säge hat keine Stockyard-ID
            .toStockyard(destination.getId())
            .ingot(5000, 500, 200, 1500)  // Default Barren-Maße
            .build();

        // An Simulator senden
        simulatorService.sendCommand(cmd);

        Notification.show(
            String.format("Einlagerung gestartet: Säge (%d,%d) → %s", sawX, sawY, destination.getYardNumber()),
            5000, Notification.Position.BOTTOM_CENTER
        ).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }
}
