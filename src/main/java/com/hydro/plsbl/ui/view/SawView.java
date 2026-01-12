package com.hydro.plsbl.ui.view;

import com.hydro.plsbl.dto.SawStatusDTO;
import com.hydro.plsbl.dto.StockyardDTO;
import com.hydro.plsbl.service.SawStatusService;
import com.hydro.plsbl.service.SettingsService;
import com.hydro.plsbl.service.StockyardService;
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

    private final SawStatusService sawStatusService;
    private final CraneSimulatorService simulatorService;
    private final StockyardService stockyardService;
    private final SettingsService settingsService;

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

    // Polling
    private ScheduledExecutorService updateExecutor;
    private ScheduledFuture<?> updateFuture;

    public SawView(SawStatusService sawStatusService,
                   CraneSimulatorService simulatorService,
                   StockyardService stockyardService,
                   SettingsService settingsService) {
        this.sawStatusService = sawStatusService;
        this.simulatorService = simulatorService;
        this.stockyardService = stockyardService;
        this.settingsService = settingsService;

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

        Span title = new Span("Barren-Daten");
        title.getStyle()
            .set("font-weight", "bold")
            .set("display", "block")
            .set("margin-bottom", "10px");
        section.add(title);

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
        } catch (Exception e) {
            log.debug("Error loading saw status: {}", e.getMessage());
        }
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

        // Barren-Daten
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

        // Buttons aktivieren/deaktivieren
        boolean hasIngot = status.hasIngot();
        boolean inProgress = status.isPickupInProgress();
        swapOutButton.setEnabled(hasIngot && !inProgress);
        toLoadingZoneButton.setEnabled(hasIngot && !inProgress);
        toExternalStockButton.setEnabled(hasIngot && !inProgress);
        deliverButton.setEnabled(hasIngot && !inProgress);

        // Simulator-Button Status aktualisieren
        if (simulatorStoreButton != null) {
            simulatorStoreButton.setEnabled(simulatorService.isRunning());
        }
    }

    // === Action Methods ===

    private void swapOut() {
        log.info("Swap out clicked");
        // TODO: Implementierung - Lagerplatz-Auswahl Dialog
    }

    private void toLoadingZone() {
        log.info("To loading zone clicked");
        // TODO: Implementierung - Bestätigungsdialog
    }

    private void toExternalStock() {
        log.info("To external stock clicked");
        // TODO: Implementierung - Lagerplatz-Auswahl Dialog
    }

    private void deliver() {
        log.info("Deliver clicked");
        // TODO: Implementierung - Lieferung
    }

    private void showInfo() {
        log.info("Info clicked");
        // TODO: Implementierung - Info-Dialog
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
