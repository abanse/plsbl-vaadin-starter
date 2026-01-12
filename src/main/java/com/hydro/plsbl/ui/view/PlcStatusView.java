package com.hydro.plsbl.ui.view;

import com.hydro.plsbl.plc.PlcAlert;
import com.hydro.plsbl.plc.PlcException;
import com.hydro.plsbl.plc.PlcService;
import com.hydro.plsbl.plc.dto.*;
import com.hydro.plsbl.ui.MainLayout;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.shared.Registration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * View fuer die Anzeige des SPS/PLC-Status
 *
 * Zeigt Live-Daten von der Siemens S7 SPS:
 * - Verbindungsstatus
 * - Kran-Position (X, Y, Z)
 * - Betriebsmodus und Zustaende
 * - Tuer-Status
 * - Fehler und Alarme
 */
@Route(value = "sps-status", layout = MainLayout.class)
@PageTitle("SPS-Status | PLSBL")
public class PlcStatusView extends VerticalLayout {

    private static final Logger log = LoggerFactory.getLogger(PlcStatusView.class);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final PlcService plcService;

    // UI-Komponenten - Verbindung
    private Span connectionStatusLabel;
    private Icon connectionIcon;
    private Span connectionInfoLabel;
    private Button connectButton;
    private Button disconnectButton;
    private Button testButton;
    private Button simulatorToggleButton;

    // UI-Komponenten - Position
    private Span xPositionLabel;
    private Span yPositionLabel;
    private Span zPositionLabel;
    private ProgressBar xProgressBar;
    private ProgressBar yProgressBar;
    private ProgressBar zProgressBar;

    // UI-Komponenten - Status
    private Span craneModeLabel;
    private Span gripperStateLabel;
    private Span jobStateLabel;
    private Span workPhaseLabel;
    private Icon craneModeIcon;
    private Icon gripperIcon;
    private Icon jobIcon;

    // UI-Komponenten - Tueren
    private Span door1Label;
    private Span door7Label;
    private Span door10Label;
    private Span gatesLabel;
    private Span doorsLabel;

    // UI-Komponenten - Fehler
    private Span errorLabel;
    private Span lastUpdateLabel;
    private VerticalLayout alertList;

    // UI-Komponenten - Kommando-Panel
    private IntegerField pickupXField;
    private IntegerField pickupYField;
    private IntegerField pickupZField;
    private IntegerField releaseXField;
    private IntegerField releaseYField;
    private IntegerField releaseZField;
    private IntegerField lengthField;
    private IntegerField widthField;
    private IntegerField thicknessField;
    private IntegerField weightField;
    private Checkbox longIngotCheckbox;
    private Checkbox rotateCheckbox;
    private Button sendCommandButton;
    private Button abortButton;
    private Button copyPositionButton;

    // Listener-Registrierung
    private Consumer<PlcStatus> statusListener;
    private Consumer<PlcAlert> alertListener;

    public PlcStatusView(PlcService plcService) {
        this.plcService = plcService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        createHeader();
        createContent();
    }

    private void createHeader() {
        H3 title = new H3("SPS-Status");
        title.getStyle().set("margin", "0");

        Span info = new Span("Live-Ansicht der Siemens S7 SPS-Daten");
        info.getStyle().set("color", "gray");

        HorizontalLayout header = new HorizontalLayout(title, info);
        header.setAlignItems(FlexComponent.Alignment.BASELINE);
        header.setWidthFull();

        add(header);
    }

    private void createContent() {
        HorizontalLayout mainContent = new HorizontalLayout();
        mainContent.setSizeFull();
        mainContent.setSpacing(true);

        // Linke Spalte: Verbindung + Position
        VerticalLayout leftColumn = new VerticalLayout();
        leftColumn.setWidth("33%");
        leftColumn.setPadding(false);
        leftColumn.add(createConnectionCard());
        leftColumn.add(createPositionCard());

        // Mittlere Spalte: Status + Tueren + Alarme
        VerticalLayout middleColumn = new VerticalLayout();
        middleColumn.setWidth("33%");
        middleColumn.setPadding(false);
        middleColumn.add(createStatusCard());
        middleColumn.add(createDoorsCard());
        middleColumn.add(createAlertsCard());

        // Rechte Spalte: Kommando-Panel
        VerticalLayout rightColumn = new VerticalLayout();
        rightColumn.setWidth("33%");
        rightColumn.setPadding(false);
        rightColumn.add(createCommandCard());

        mainContent.add(leftColumn, middleColumn, rightColumn);
        add(mainContent);
    }

    // === Verbindungs-Karte ===

    private Div createConnectionCard() {
        Div card = createCard("Verbindung");

        // Status-Zeile
        connectionIcon = VaadinIcon.CIRCLE.create();
        connectionIcon.setSize("16px");
        connectionStatusLabel = new Span("Unbekannt");
        connectionStatusLabel.getStyle().set("font-weight", "bold");

        HorizontalLayout statusRow = new HorizontalLayout(connectionIcon, connectionStatusLabel);
        statusRow.setAlignItems(FlexComponent.Alignment.CENTER);
        statusRow.setSpacing(true);

        // Info-Zeile
        connectionInfoLabel = new Span("-");
        connectionInfoLabel.getStyle().set("color", "gray").set("font-size", "14px");

        // Buttons
        connectButton = new Button("Verbinden", VaadinIcon.PLUG.create());
        connectButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        connectButton.addClickListener(e -> {
            plcService.connect();
            updateConnectionStatus();
        });

        disconnectButton = new Button("Trennen", VaadinIcon.CLOSE.create());
        disconnectButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        disconnectButton.addClickListener(e -> {
            plcService.disconnect();
            updateConnectionStatus();
        });

        testButton = new Button("Test", VaadinIcon.CHECK.create());
        testButton.addClickListener(e -> {
            boolean success = plcService.testConnection();
            if (success) {
                Notification.show("Verbindungstest erfolgreich", 3000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } else {
                Notification.show("Verbindungstest fehlgeschlagen", 3000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
            updateConnectionStatus();
        });

        simulatorToggleButton = new Button("Simulator", VaadinIcon.PLAY_CIRCLE.create());
        simulatorToggleButton.addClickListener(e -> {
            if (plcService.isSimulatorMode()) {
                plcService.disableSimulatorMode();
                Notification.show("Wechsle zu SPS-Modus...", 2000, Notification.Position.BOTTOM_CENTER);
            } else {
                plcService.enableSimulatorMode();
                Notification.show("Simulator-Modus aktiviert", 2000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            }
            updateConnectionStatus();
        });

        HorizontalLayout buttons = new HorizontalLayout(connectButton, disconnectButton, testButton, simulatorToggleButton);
        buttons.setSpacing(true);

        // Last Update
        lastUpdateLabel = new Span("Letztes Update: -");
        lastUpdateLabel.getStyle().set("color", "gray").set("font-size", "12px");

        VerticalLayout content = new VerticalLayout(statusRow, connectionInfoLabel, buttons, lastUpdateLabel);
        content.setPadding(false);
        content.setSpacing(true);

        card.add(content);
        return card;
    }

    // === Positions-Karte ===

    private Div createPositionCard() {
        Div card = createCard("Kran-Position");

        // X-Position
        xPositionLabel = new Span("X: 0 mm");
        xPositionLabel.getStyle().set("font-weight", "bold").set("min-width", "120px");
        xProgressBar = new ProgressBar(0, 100000, 0);
        xProgressBar.setWidth("100%");
        HorizontalLayout xRow = new HorizontalLayout(xPositionLabel, xProgressBar);
        xRow.setWidthFull();
        xRow.setAlignItems(FlexComponent.Alignment.CENTER);

        // Y-Position
        yPositionLabel = new Span("Y: 0 mm");
        yPositionLabel.getStyle().set("font-weight", "bold").set("min-width", "120px");
        yProgressBar = new ProgressBar(0, 40000, 0);
        yProgressBar.setWidth("100%");
        HorizontalLayout yRow = new HorizontalLayout(yPositionLabel, yProgressBar);
        yRow.setWidthFull();
        yRow.setAlignItems(FlexComponent.Alignment.CENTER);

        // Z-Position
        zPositionLabel = new Span("Z: 0 mm");
        zPositionLabel.getStyle().set("font-weight", "bold").set("min-width", "120px");
        zProgressBar = new ProgressBar(0, 6000, 0);
        zProgressBar.setWidth("100%");
        HorizontalLayout zRow = new HorizontalLayout(zPositionLabel, zProgressBar);
        zRow.setWidthFull();
        zRow.setAlignItems(FlexComponent.Alignment.CENTER);

        VerticalLayout content = new VerticalLayout(xRow, yRow, zRow);
        content.setPadding(false);
        content.setSpacing(true);

        card.add(content);
        return card;
    }

    // === Status-Karte ===

    private Div createStatusCard() {
        Div card = createCard("Kran-Status");

        // Modus
        craneModeIcon = VaadinIcon.COG.create();
        craneModeLabel = new Span("Modus: -");
        HorizontalLayout modeRow = createStatusRow(craneModeIcon, craneModeLabel);

        // Greifer
        gripperIcon = VaadinIcon.HAND.create();
        gripperStateLabel = new Span("Greifer: -");
        HorizontalLayout gripperRow = createStatusRow(gripperIcon, gripperStateLabel);

        // Job
        jobIcon = VaadinIcon.TASKS.create();
        jobStateLabel = new Span("Auftrag: -");
        HorizontalLayout jobRow = createStatusRow(jobIcon, jobStateLabel);

        // Arbeitsphase
        workPhaseLabel = new Span("Phase: -");
        workPhaseLabel.getStyle().set("color", "gray").set("font-size", "14px");

        // Fehler
        errorLabel = new Span("");
        errorLabel.getStyle().set("color", "red").set("font-weight", "bold");

        VerticalLayout content = new VerticalLayout(modeRow, gripperRow, jobRow, workPhaseLabel, errorLabel);
        content.setPadding(false);
        content.setSpacing(true);

        card.add(content);
        return card;
    }

    // === Tueren-Karte ===

    private Div createDoorsCard() {
        Div card = createCard("Tore & Tueren");

        door1Label = createDoorLabel("Tor 1");
        door7Label = createDoorLabel("Tor 7");
        door10Label = createDoorLabel("Tor 10");
        gatesLabel = createDoorLabel("Schranken (6/8)");
        doorsLabel = createDoorLabel("Tueren (2-5, 9)");

        HorizontalLayout row1 = new HorizontalLayout(door1Label, door7Label, door10Label);
        row1.setSpacing(true);

        HorizontalLayout row2 = new HorizontalLayout(gatesLabel, doorsLabel);
        row2.setSpacing(true);

        VerticalLayout content = new VerticalLayout(row1, row2);
        content.setPadding(false);
        content.setSpacing(true);

        card.add(content);
        return card;
    }

    // === Alarme-Karte ===

    private Div createAlertsCard() {
        Div card = createCard("Alarme");

        alertList = new VerticalLayout();
        alertList.setPadding(false);
        alertList.setSpacing(false);
        alertList.add(new Span("Keine Alarme"));
        alertList.getStyle().set("max-height", "150px").set("overflow-y", "auto");

        card.add(alertList);
        return card;
    }

    // === Kommando-Karte ===

    private Div createCommandCard() {
        Div card = createCard("Kommando senden");

        // Aufnahme-Position
        Span pickupTitle = new Span("Aufnahme-Position [mm]");
        pickupTitle.getStyle().set("font-weight", "500").set("font-size", "14px");

        pickupXField = createPositionField("X");
        pickupYField = createPositionField("Y");
        pickupZField = createPositionField("Z");

        HorizontalLayout pickupRow = new HorizontalLayout(pickupXField, pickupYField, pickupZField);
        pickupRow.setSpacing(true);
        pickupRow.setWidthFull();

        // Ablage-Position
        Span releaseTitle = new Span("Ablage-Position [mm]");
        releaseTitle.getStyle().set("font-weight", "500").set("font-size", "14px");

        releaseXField = createPositionField("X");
        releaseYField = createPositionField("Y");
        releaseZField = createPositionField("Z");

        HorizontalLayout releaseRow = new HorizontalLayout(releaseXField, releaseYField, releaseZField);
        releaseRow.setSpacing(true);
        releaseRow.setWidthFull();

        // Barren-Dimensionen
        Span dimensionsTitle = new Span("Barren-Daten");
        dimensionsTitle.getStyle().set("font-weight", "500").set("font-size", "14px");

        lengthField = createDimensionField("Laenge [mm]", 0, 10000);
        widthField = createDimensionField("Breite [mm]", 0, 2000);
        thicknessField = createDimensionField("Dicke [mm]", 0, 1000);
        weightField = createDimensionField("Gewicht [kg]", 0, 50000);

        FormLayout dimensionsForm = new FormLayout();
        dimensionsForm.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 2),
            new FormLayout.ResponsiveStep("300px", 4)
        );
        dimensionsForm.add(lengthField, widthField, thicknessField, weightField);

        // Optionen
        longIngotCheckbox = new Checkbox("Langbarren");
        rotateCheckbox = new Checkbox("Drehen (180°)");

        HorizontalLayout optionsRow = new HorizontalLayout(longIngotCheckbox, rotateCheckbox);
        optionsRow.setSpacing(true);

        // Buttons
        copyPositionButton = new Button("Position uebernehmen", VaadinIcon.COPY.create());
        copyPositionButton.addClickListener(e -> copyCurrentPosition());
        copyPositionButton.getStyle().set("font-size", "12px");

        sendCommandButton = new Button("Kommando senden", VaadinIcon.PLAY.create());
        sendCommandButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        sendCommandButton.addClickListener(e -> sendCommand());

        abortButton = new Button("ABBRUCH", VaadinIcon.STOP.create());
        abortButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        abortButton.addClickListener(e -> abortCommand());

        HorizontalLayout buttonRow = new HorizontalLayout(copyPositionButton, sendCommandButton, abortButton);
        buttonRow.setSpacing(true);
        buttonRow.setWidthFull();
        buttonRow.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        // Hinweis
        Span hint = new Span("Achtung: Kommandos werden direkt an die SPS gesendet!");
        hint.getStyle().set("color", "#FF9800").set("font-size", "12px").set("font-style", "italic");

        // Layout zusammenbauen
        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);
        content.add(
            pickupTitle, pickupRow,
            releaseTitle, releaseRow,
            dimensionsTitle, dimensionsForm,
            optionsRow,
            new Hr(),
            buttonRow,
            hint
        );

        card.add(content);
        return card;
    }

    private IntegerField createPositionField(String label) {
        IntegerField field = new IntegerField(label);
        field.setMin(0);
        field.setMax(200000);
        field.setValue(0);
        field.setStepButtonsVisible(true);
        field.setStep(1000);
        field.setWidth("100px");
        return field;
    }

    private IntegerField createDimensionField(String label, int min, int max) {
        IntegerField field = new IntegerField(label);
        field.setMin(min);
        field.setMax(max);
        field.setValue(0);
        field.setStepButtonsVisible(true);
        field.setWidth("100%");
        return field;
    }

    private void copyCurrentPosition() {
        PlcStatus status = plcService.getCurrentStatus();

        // Aktuelle Position als Aufnahme-Position setzen
        pickupXField.setValue(status.getXPosition());
        pickupYField.setValue(status.getYPosition());
        pickupZField.setValue(status.getZPosition());

        Notification.show("Aktuelle Position uebernommen", 2000, Notification.Position.BOTTOM_CENTER);
    }

    private void sendCommand() {
        if (!plcService.isConnected()) {
            Notification.show("Keine SPS-Verbindung!", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        try {
            PlcCommand command = PlcCommand.builder()
                .pickupPosition(
                    pickupXField.getValue() != null ? pickupXField.getValue() : 0,
                    pickupYField.getValue() != null ? pickupYField.getValue() : 0,
                    pickupZField.getValue() != null ? pickupZField.getValue() : 0
                )
                .releasePosition(
                    releaseXField.getValue() != null ? releaseXField.getValue() : 0,
                    releaseYField.getValue() != null ? releaseYField.getValue() : 0,
                    releaseZField.getValue() != null ? releaseZField.getValue() : 0
                )
                .length(lengthField.getValue() != null ? lengthField.getValue() : 0)
                .width(widthField.getValue() != null ? widthField.getValue() : 0)
                .thickness(thicknessField.getValue() != null ? thicknessField.getValue() : 0)
                .weight(weightField.getValue() != null ? weightField.getValue() : 0)
                .longIngot(longIngotCheckbox.getValue())
                .rotate(rotateCheckbox.getValue())
                .build();

            plcService.sendCommand(command);

            Notification.show("Kommando gesendet: " + command, 3000, Notification.Position.BOTTOM_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

        } catch (PlcException e) {
            log.error("Fehler beim Senden des Kommandos: {}", e.getMessage());
            Notification.show("Fehler: " + e.getMessage(), 5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void abortCommand() {
        try {
            plcService.abort();
            Notification.show("ABBRUCH gesendet!", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_WARNING);
        } catch (PlcException e) {
            log.error("Fehler beim Abbruch: {}", e.getMessage());
            Notification.show("Fehler beim Abbruch: " + e.getMessage(), 5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    // === Hilfsmethoden ===

    private Div createCard(String title) {
        Div card = new Div();
        card.getStyle()
            .set("background-color", "var(--lumo-base-color)")
            .set("border-radius", "8px")
            .set("padding", "16px")
            .set("box-shadow", "0 2px 4px rgba(0,0,0,0.1)")
            .set("margin-bottom", "16px");

        Span titleSpan = new Span(title);
        titleSpan.getStyle()
            .set("font-weight", "bold")
            .set("font-size", "16px")
            .set("display", "block")
            .set("margin-bottom", "12px");

        card.add(titleSpan);
        return card;
    }

    private HorizontalLayout createStatusRow(Icon icon, Span label) {
        icon.setSize("20px");
        icon.getStyle().set("color", "var(--lumo-secondary-text-color)");
        HorizontalLayout row = new HorizontalLayout(icon, label);
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        row.setSpacing(true);
        return row;
    }

    private Span createDoorLabel(String name) {
        Span label = new Span(name);
        label.getStyle()
            .set("padding", "4px 8px")
            .set("border-radius", "4px")
            .set("background-color", "#4CAF50")
            .set("color", "white")
            .set("font-size", "12px");
        return label;
    }

    // === Update-Methoden ===

    private void updateConnectionStatus() {
        boolean connected = plcService.isConnected();
        boolean enabled = plcService.isEnabled();
        boolean simulatorMode = plcService.isSimulatorMode();

        if (simulatorMode) {
            // Simulator-Modus aktiv
            connectionIcon.getStyle().set("color", "#FF9800");
            connectionStatusLabel.setText("SIMULATOR");
            connectionInfoLabel.setText(plcService.getConnectionInfo());
            simulatorToggleButton.setText("Zu SPS");
            simulatorToggleButton.setIcon(VaadinIcon.PLUG.create());
            simulatorToggleButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        } else if (!enabled) {
            connectionIcon.getStyle().set("color", "gray");
            connectionStatusLabel.setText("Deaktiviert");
            connectionInfoLabel.setText("SPS ist in den Einstellungen deaktiviert");
            simulatorToggleButton.setText("Simulator");
            simulatorToggleButton.setIcon(VaadinIcon.PLAY_CIRCLE.create());
            simulatorToggleButton.removeThemeVariants(ButtonVariant.LUMO_SUCCESS);
        } else if (connected) {
            connectionIcon.getStyle().set("color", "#4CAF50");
            connectionStatusLabel.setText("Verbunden");
            connectionInfoLabel.setText(plcService.getConnectionInfo());
            simulatorToggleButton.setText("Simulator");
            simulatorToggleButton.setIcon(VaadinIcon.PLAY_CIRCLE.create());
            simulatorToggleButton.removeThemeVariants(ButtonVariant.LUMO_SUCCESS);
        } else {
            connectionIcon.getStyle().set("color", "#F44336");
            connectionStatusLabel.setText("Nicht verbunden");
            connectionInfoLabel.setText("Versuche: " + plcService.getReconnectAttempts());
            simulatorToggleButton.setText("Simulator");
            simulatorToggleButton.setIcon(VaadinIcon.PLAY_CIRCLE.create());
            simulatorToggleButton.removeThemeVariants(ButtonVariant.LUMO_SUCCESS);
        }

        connectButton.setEnabled(enabled && !connected && !simulatorMode);
        disconnectButton.setEnabled(connected && !simulatorMode);
        testButton.setEnabled(enabled && !simulatorMode);
    }

    private void updateStatus(PlcStatus status) {
        // Position
        xPositionLabel.setText(String.format("X: %,d mm", status.getXPosition()));
        yPositionLabel.setText(String.format("Y: %,d mm", status.getYPosition()));
        zPositionLabel.setText(String.format("Z: %,d mm", status.getZPosition()));

        xProgressBar.setValue(status.getXPosition());
        yProgressBar.setValue(status.getYPosition());
        zProgressBar.setValue(status.getZPosition());

        // Modus
        CraneMode mode = status.getCraneMode();
        if (mode != null) {
            craneModeLabel.setText("Modus: " + mode.getDisplayName());
            craneModeIcon.getStyle().set("color",
                mode == CraneMode.AUTOMATIC ? "#4CAF50" :
                mode == CraneMode.MANUAL ? "#FF9800" : "#2196F3");
        }

        // Greifer
        GripperState gripper = status.getGripperState();
        if (gripper != null) {
            gripperStateLabel.setText("Greifer: " + gripper.getDisplayName());
            gripperIcon.getStyle().set("color",
                gripper == GripperState.LOADED ? "#4CAF50" :
                gripper == GripperState.CLOSED ? "#FF9800" : "#9E9E9E");
        }

        // Job
        JobState job = status.getJobState();
        if (job != null) {
            jobStateLabel.setText("Auftrag: " + job.getDisplayName());
            jobIcon.getStyle().set("color",
                job == JobState.IDLE ? "#9E9E9E" :
                job == JobState.DROPPED ? "#4CAF50" : "#2196F3");
        }

        // Arbeitsphase
        WorkPhase phase = status.getWorkPhase();
        if (phase != null) {
            workPhaseLabel.setText("Phase: " + phase.getDisplayName());
        }

        // Fehler
        if (status.hasError()) {
            StringBuilder errors = new StringBuilder();
            if (status.isLinkDown()) errors.append("Verbindung unterbrochen! ");
            if (status.isChecksumError()) errors.append("Pruefsummenfehler! ");
            if (status.isPlcError()) errors.append("SPS-Fehler! ");
            if (status.isCraneOff()) errors.append("Kran ausgeschaltet! ");
            errorLabel.setText(errors.toString());
        } else {
            errorLabel.setText("");
        }

        // Tueren
        updateDoorLabel(door1Label, "Tor 1", status.isDoor1Open());
        updateDoorLabel(door7Label, "Tor 7", status.isDoor7Open());
        updateDoorLabel(door10Label, "Tor 10", status.isDoor10Open());
        updateDoorLabel(gatesLabel, "Schranken", status.isGatesOpen());
        updateDoorLabel(doorsLabel, "Tueren", status.isDoorsOpen());

        // Verbindungsstatus
        updateConnectionStatus();

        // Last Update
        if (status.getLastUpdate() != null) {
            lastUpdateLabel.setText("Letztes Update: " + status.getLastUpdate().format(TIME_FORMAT));
        }
    }

    private void updateDoorLabel(Span label, String name, boolean isOpen) {
        label.setText(name + (isOpen ? " OFFEN" : ""));
        label.getStyle().set("background-color", isOpen ? "#F44336" : "#4CAF50");
    }

    private void addAlert(PlcAlert alert) {
        // Erste "Keine Alarme" Meldung entfernen
        if (alertList.getComponentCount() == 1) {
            alertList.removeAll();
        }

        Span alertSpan = new Span(alert.toString());
        alertSpan.getStyle()
            .set("font-size", "12px")
            .set("padding", "4px")
            .set("display", "block")
            .set("border-bottom", "1px solid var(--lumo-contrast-10pct)");

        switch (alert.getSeverity()) {
            case CRITICAL -> alertSpan.getStyle().set("color", "#D32F2F").set("font-weight", "bold");
            case ERROR -> alertSpan.getStyle().set("color", "#F44336");
            case WARNING -> alertSpan.getStyle().set("color", "#FF9800");
            default -> alertSpan.getStyle().set("color", "var(--lumo-secondary-text-color)");
        }

        // Am Anfang einfuegen
        alertList.addComponentAsFirst(alertSpan);

        // Max 20 Alarme behalten
        while (alertList.getComponentCount() > 20) {
            alertList.remove(alertList.getComponentAt(alertList.getComponentCount() - 1));
        }
    }

    // === Lifecycle ===

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);

        // Initiale Werte setzen
        updateConnectionStatus();
        updateStatus(plcService.getCurrentStatus());

        // Status-Listener registrieren
        UI ui = attachEvent.getUI();
        statusListener = status -> {
            ui.access(() -> updateStatus(status));
        };
        plcService.addStatusListener(statusListener);

        // Alert-Listener registrieren
        alertListener = alert -> {
            ui.access(() -> addAlert(alert));
        };
        plcService.addAlertListener(alertListener);

        log.debug("PlcStatusView attached - Listeners registered");
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        // Listener entfernen
        if (statusListener != null) {
            plcService.removeStatusListener(statusListener);
        }
        if (alertListener != null) {
            plcService.removeAlertListener(alertListener);
        }

        log.debug("PlcStatusView detached - Listeners removed");
        super.onDetach(detachEvent);
    }
}
