package com.hydro.plsbl.ui.view;

import com.hydro.plsbl.dto.CraneStatusDTO;
import com.hydro.plsbl.service.CraneStatusService;
import com.hydro.plsbl.simulator.CraneSimulatorCommand;
import com.hydro.plsbl.simulator.CraneSimulatorService;
import com.hydro.plsbl.ui.MainLayout;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Kran-Ansicht - Detaillierte Kran-Status-Anzeige
 * Basiert auf dem Original-Layout aus Kran-Tab.png
 */
@Route(value = "kran", layout = MainLayout.class)
@PageTitle("Kran | PLSBL")
public class KranView extends VerticalLayout {

    private static final Logger log = LoggerFactory.getLogger(KranView.class);
    private static final int UPDATE_INTERVAL = 1;
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    private final CraneStatusService craneStatusService;
    private final CraneSimulatorService simulatorService;

    // Auftragsstatus
    private TextField auftragStatusField;
    private TextField laufNrField;
    private TextField statuszeitField;
    private TextField betriebsartField;
    private TextField zangenzustandField;
    private TextField erledigtField;

    // Status-Checkboxen
    private Checkbox tor1Offen;
    private Checkbox tor7Offen;
    private Checkbox tor10Offen;
    private Checkbox schrankenOffen;
    private Checkbox toreOffen;
    private Checkbox kranAus;
    private Checkbox spsFehler;
    private Checkbox pruefFehler;
    private Checkbox verbindungTot;

    // Positions-Grid
    private Grid<PositionRow> positionGrid;
    private List<PositionRow> positionRows;

    // Kommando-Bereich
    private TextField kommandoArtField;
    private Checkbox barrenDrehenCheck;
    private TextField barrenNrField;
    private Checkbox langerBarrenCheck;
    private TextField laengeField;
    private TextField artikelNrField;
    private TextField breiteField;
    private TextField gewichtField;
    private TextField dickeField;

    // Alarm-Anzeige
    private Div alarmSection;
    private Span alarmText;
    private boolean alarmBlinkState = false;

    // Simulator-Steuerung
    private Button simStartButton;
    private Button simStopButton;
    private Span simStatusLabel;

    // Polling
    private ScheduledExecutorService updateExecutor;
    private ScheduledFuture<?> updateFuture;

    public KranView(CraneStatusService craneStatusService, CraneSimulatorService simulatorService) {
        this.craneStatusService = craneStatusService;
        this.simulatorService = simulatorService;

        setSizeFull();
        setPadding(true);
        setSpacing(false);

        // Initialisiere Positions-Daten
        initPositionRows();

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

    private void initPositionRows() {
        positionRows = new ArrayList<>();
        positionRows.add(new PositionRow("Abholen:", "", 0, 0, 0, "", ""));
        positionRows.add(new PositionRow("abgeholt", "", 0, 0, 0, "", ""));
        positionRows.add(new PositionRow("Ablegen:", "", 0, 0, 0, "", ""));
        positionRows.add(new PositionRow("abgelegt", "", 0, 0, 0, "", ""));
        positionRows.add(new PositionRow("aktuell", "", 0, 0, 0, "", ""));
    }

    private void createContent() {
        // Alarm-Anzeige (oben, nur bei Störung sichtbar)
        add(createAlarmSection());

        // Oberer Bereich: Auftragsstatus und Status-Checkboxen
        HorizontalLayout topSection = new HorizontalLayout();
        topSection.setWidthFull();
        topSection.setSpacing(true);

        topSection.add(createAuftragStatusSection());
        topSection.add(createStatusCheckboxSection());

        add(topSection);
        add(new Hr());

        // Mittlerer Bereich: Positions-Tabelle
        add(createPositionSection());
        add(new Hr());

        // Unterer Bereich: Kommando
        add(createKommandoSection());

        // Simulator-Steuerung (nur für Entwicklung)
        add(new Hr());
        add(createSimulatorSection());
    }

    private Div createSimulatorSection() {
        Div section = new Div();
        section.setWidthFull();
        section.getStyle()
            .set("background-color", "#E3F2FD")
            .set("padding", "15px")
            .set("border-radius", "4px")
            .set("border", "1px solid #90CAF9");

        Span title = new Span("Kran-Simulator");
        title.getStyle()
            .set("font-weight", "bold")
            .set("font-size", "14px")
            .set("display", "block")
            .set("margin-bottom", "10px");
        section.add(title);

        HorizontalLayout controls = new HorizontalLayout();
        controls.setAlignItems(FlexComponent.Alignment.CENTER);
        controls.setSpacing(true);

        // Status-Anzeige
        simStatusLabel = new Span();
        simStatusLabel.getStyle()
            .set("font-weight", "bold")
            .set("padding", "4px 12px")
            .set("border-radius", "4px");

        // Start-Button
        simStartButton = new Button("Start", VaadinIcon.PLAY.create());
        simStartButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        simStartButton.addClickListener(e -> {
            simulatorService.start();
            updateSimulatorStatus();
            Notification.show("Simulator gestartet", 2000, Notification.Position.BOTTOM_START);
        });

        // Stop-Button
        simStopButton = new Button("Stop", VaadinIcon.STOP.create());
        simStopButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        simStopButton.addClickListener(e -> {
            simulatorService.stop();
            updateSimulatorStatus();
            Notification.show("Simulator gestoppt", 2000, Notification.Position.BOTTOM_START);
        });

        // Reset-Button
        Button resetButton = new Button("Reset", VaadinIcon.REFRESH.create());
        resetButton.addClickListener(e -> {
            simulatorService.reset();
            Notification.show("Simulator zurückgesetzt", 2000, Notification.Position.BOTTOM_START);
        });

        // Test-Fahrt Button
        Button testButton = new Button("Test-Fahrt", VaadinIcon.AUTOMATION.create());
        testButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        testButton.addClickListener(e -> startTestMove());

        // Status-Anzeige Button (zeigt aktuelle Position)
        Button statusButton = new Button("Status", VaadinIcon.INFO.create());
        statusButton.addClickListener(e -> {
            CraneSimulatorService.SimulatorStatus s = simulatorService.getSimulatorStatus();
            Notification.show(String.format(
                "Pos: (%d, %d, %d) | Phase: %s | Job: %s | Running: %s",
                s.xPosition(), s.yPosition(), s.zPosition(),
                s.workPhase(), s.jobState(), s.running()),
                5000, Notification.Position.MIDDLE);
        });

        // Status nach Erstellung der Buttons aktualisieren
        updateSimulatorStatus();

        controls.add(simStatusLabel, simStartButton, simStopButton, resetButton, testButton, statusButton);
        section.add(controls);

        return section;
    }

    private void updateSimulatorStatus() {
        boolean running = simulatorService.isRunning();
        CraneSimulatorService.SimulatorStatus status = simulatorService.getSimulatorStatus();

        if (running) {
            // Zeige Position im Status-Label
            simStatusLabel.setText(String.format("Läuft | X:%d Y:%d | %s",
                status.xPosition(), status.yPosition(), status.workPhase()));
            simStatusLabel.getStyle()
                .set("background-color", "#4CAF50")
                .set("color", "white");
            simStartButton.setEnabled(false);
            simStopButton.setEnabled(true);
        } else {
            simStatusLabel.setText("Gestoppt");
            simStatusLabel.getStyle()
                .set("background-color", "#9E9E9E")
                .set("color", "white");
            simStartButton.setEnabled(true);
            simStopButton.setEnabled(false);
        }
    }

    private void startTestMove() {
        log.info("Test-Fahrt Button geklickt, Simulator running={}", simulatorService.isRunning());

        if (!simulatorService.isRunning()) {
            Notification.show("Simulator ist nicht gestartet! Bitte erst 'Start' klicken.", 3000, Notification.Position.BOTTOM_START);
            return;
        }

        // Aktuelle Position loggen
        CraneSimulatorService.SimulatorStatus status = simulatorService.getSimulatorStatus();
        log.info("Aktuelle Position: ({},{},{}), Phase={}, Job={}",
            status.xPosition(), status.yPosition(), status.zPosition(),
            status.workPhase(), status.jobState());

        // Testfahrt: Von Position (3000, 30000) nach (48000, 15000) - quer durchs Lager
        CraneSimulatorCommand cmd = CraneSimulatorCommand.builder()
            .pickup(3000, 30000, 2000)  // Oben links, Abholhöhe
            .release(48000, 15000, 2000)  // Mitte rechts, Ablagehöhe
            .ingot(5000, 500, 200, 1500)  // Beispiel-Barren
            .build();

        simulatorService.sendCommand(cmd);
        Notification.show("Test-Fahrt gesendet - Position wird aktualisiert...", 3000, Notification.Position.BOTTOM_START);
    }

    private Div createAlarmSection() {
        alarmSection = new Div();
        alarmSection.setWidthFull();
        alarmSection.getStyle()
            .set("background-color", "#F44336")
            .set("color", "white")
            .set("padding", "12px 20px")
            .set("border-radius", "4px")
            .set("margin-bottom", "10px")
            .set("display", "flex")
            .set("align-items", "center")
            .set("gap", "15px")
            .set("box-shadow", "0 2px 8px rgba(244, 67, 54, 0.4)");
        alarmSection.setVisible(false);

        // Alarm-Icon
        Span icon = new Span("⚠");
        icon.getStyle()
            .set("font-size", "24px")
            .set("animation", "pulse 1s infinite");

        // Alarm-Text
        alarmText = new Span();
        alarmText.getStyle()
            .set("font-weight", "bold")
            .set("font-size", "16px")
            .set("flex", "1");

        // Zeitstempel
        Span timeLabel = new Span();
        timeLabel.getStyle()
            .set("font-size", "12px")
            .set("opacity", "0.8");
        timeLabel.setId("alarm-time");

        alarmSection.add(icon, alarmText, timeLabel);

        // CSS Animation für Blinken hinzufügen
        alarmSection.getElement().executeJs(
            "if (!document.getElementById('alarm-blink-style')) {" +
            "  var style = document.createElement('style');" +
            "  style.id = 'alarm-blink-style';" +
            "  style.textContent = '@keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.5; } } " +
            "                       @keyframes blink { 0%, 100% { background-color: #F44336; } 50% { background-color: #D32F2F; } }';" +
            "  document.head.appendChild(style);" +
            "}"
        );

        return alarmSection;
    }

    private Div createAuftragStatusSection() {
        Div section = new Div();
        section.getStyle().set("flex", "1");

        FormLayout form = new FormLayout();
        form.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 2),
            new FormLayout.ResponsiveStep("500px", 4)
        );

        // Auftragsstatus
        auftragStatusField = createReadOnlyField("kein Auftrag");
        form.addFormItem(auftragStatusField, "Auftragsstatus");

        // Lauf-Nr
        laufNrField = createReadOnlyField("");
        form.addFormItem(laufNrField, "Lauf-Nr:");

        // Platzhalter für "angenommen"
        TextField angenommenField = createReadOnlyField("");
        form.addFormItem(angenommenField, "angenommen");

        // Statuszeit
        statuszeitField = createReadOnlyField("");
        form.addFormItem(statuszeitField, "Statuszeit:");

        // in Bearbeitung
        TextField bearbeitungField = createReadOnlyField("");
        form.addFormItem(bearbeitungField, "in Bearbeitung");

        // Betriebsart
        betriebsartField = createReadOnlyField("vollautomatisch");
        form.addFormItem(betriebsartField, "Betriebsart:");

        // erledigt
        erledigtField = createReadOnlyField("");
        form.addFormItem(erledigtField, "erledigt:");

        // Zangenzustand
        zangenzustandField = createReadOnlyField("offen");
        form.addFormItem(zangenzustandField, "Zangenzustand:");

        section.add(form);
        return section;
    }

    private Div createStatusCheckboxSection() {
        Div section = new Div();
        section.getStyle()
            .set("display", "flex")
            .set("gap", "30px");

        // Linke Spalte
        VerticalLayout leftCol = new VerticalLayout();
        leftCol.setPadding(false);
        leftCol.setSpacing(false);

        tor1Offen = createStatusCheckbox("Tor 1 offen");
        tor7Offen = createStatusCheckbox("Tor 7 offen");
        tor10Offen = createStatusCheckbox("Tor 10 offen");
        schrankenOffen = createStatusCheckbox("Schranken offen");
        toreOffen = createStatusCheckbox("Tore offen");

        leftCol.add(tor1Offen, tor7Offen, tor10Offen, schrankenOffen, toreOffen);

        // Rechte Spalte
        VerticalLayout rightCol = new VerticalLayout();
        rightCol.setPadding(false);
        rightCol.setSpacing(false);

        kranAus = createStatusCheckbox("Kran aus");
        spsFehler = createStatusCheckbox("SPS Fehler");
        pruefFehler = createStatusCheckbox("Prüfsummenfehler");
        verbindungTot = createStatusCheckbox("Verbindung / SPS tot");

        rightCol.add(kranAus, spsFehler, pruefFehler, verbindungTot);

        section.add(leftCol, rightCol);
        return section;
    }

    private Div createPositionSection() {
        Div section = new Div();
        section.setWidthFull();

        positionGrid = new Grid<>();
        positionGrid.setItems(positionRows);
        positionGrid.setHeight("220px");
        positionGrid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);

        positionGrid.addColumn(PositionRow::getLabel)
            .setHeader("")
            .setWidth("80px")
            .setFlexGrow(0);

        positionGrid.addColumn(PositionRow::getPlatzNr)
            .setHeader("Platz-Nr.")
            .setWidth("100px")
            .setFlexGrow(0);

        positionGrid.addColumn(row -> formatNumber(row.getX()))
            .setHeader("X [mm]")
            .setWidth("100px")
            .setFlexGrow(0);

        positionGrid.addColumn(row -> formatNumber(row.getY()))
            .setHeader("Y [mm]")
            .setWidth("100px")
            .setFlexGrow(0);

        positionGrid.addColumn(row -> formatNumber(row.getZ()))
            .setHeader("Z [mm]")
            .setWidth("100px")
            .setFlexGrow(0);

        positionGrid.addColumn(PositionRow::getZeitpunkt)
            .setHeader("Zeitpunkt")
            .setWidth("160px")
            .setFlexGrow(0);

        positionGrid.addColumn(PositionRow::getBewertung)
            .setHeader("Bewertung")
            .setFlexGrow(1);

        section.add(positionGrid);
        return section;
    }

    private Div createKommandoSection() {
        Div section = new Div();
        section.setWidthFull();

        HorizontalLayout layout = new HorizontalLayout();
        layout.setWidthFull();
        layout.setSpacing(true);
        layout.setAlignItems(FlexComponent.Alignment.END);

        // Linke Seite: Kommando-Art und Checkboxen
        VerticalLayout leftPart = new VerticalLayout();
        leftPart.setPadding(false);
        leftPart.setSpacing(true);

        HorizontalLayout kommandoRow = new HorizontalLayout();
        kommandoRow.setAlignItems(FlexComponent.Alignment.BASELINE);
        Span kommandoLabel = new Span("Kommando-Art:");
        kommandoArtField = createReadOnlyField("");
        kommandoArtField.setWidth("150px");
        barrenDrehenCheck = new Checkbox("Barren drehen");
        barrenDrehenCheck.setReadOnly(true);
        kommandoRow.add(kommandoLabel, kommandoArtField, barrenDrehenCheck);
        leftPart.add(kommandoRow);

        // Barren-Infos
        FormLayout barrenForm = new FormLayout();
        barrenForm.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 2),
            new FormLayout.ResponsiveStep("400px", 4)
        );

        barrenNrField = createReadOnlyField("");
        barrenNrField.setWidth("120px");
        barrenForm.addFormItem(barrenNrField, "Barren-Nr:");

        langerBarrenCheck = new Checkbox("langer Barren");
        langerBarrenCheck.setReadOnly(true);
        barrenForm.add(langerBarrenCheck);

        laengeField = createReadOnlyField("");
        laengeField.setWidth("80px");
        barrenForm.addFormItem(laengeField, "Länge [mm]:");

        // Zweite Zeile
        artikelNrField = createReadOnlyField("");
        artikelNrField.setWidth("200px");
        barrenForm.addFormItem(artikelNrField, "Artikel-Nr:");

        Span spacer1 = new Span();
        barrenForm.add(spacer1);

        breiteField = createReadOnlyField("");
        breiteField.setWidth("80px");
        barrenForm.addFormItem(breiteField, "Breite [mm]:");

        // Dritte Zeile
        gewichtField = createReadOnlyField("");
        gewichtField.setWidth("100px");
        barrenForm.addFormItem(gewichtField, "Gewicht [kg]:");

        Span spacer2 = new Span();
        barrenForm.add(spacer2);

        dickeField = createReadOnlyField("");
        dickeField.setWidth("80px");
        barrenForm.addFormItem(dickeField, "Dicke [mm]:");

        leftPart.add(barrenForm);
        layout.add(leftPart);

        section.add(layout);
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

    private String formatNumber(int value) {
        if (value == 0) return "";
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

        log.info("Crane status updates started");
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
        log.info("Crane status updates stopped");
    }

    private void loadStatus() {
        try {
            // Simulator-Status zuerst aktualisieren
            updateSimulatorStatus();

            boolean simRunning = simulatorService.isRunning();

            // Wenn Simulator läuft, dessen Position anzeigen
            if (simRunning) {
                updateFromSimulator();
            } else {
                // Sonst Datenbank-Status verwenden
                log.debug("Simulator not running (running={}), loading from DB", simRunning);
                Optional<CraneStatusDTO> statusOpt = craneStatusService.getCurrentStatus();
                if (statusOpt.isPresent()) {
                    updateDisplay(statusOpt.get());
                }
            }
        } catch (Exception e) {
            log.warn("Error loading crane status: {}", e.getMessage());
        }
    }

    private void updateFromSimulator() {
        CraneSimulatorService.SimulatorStatus simStatus = simulatorService.getSimulatorStatus();

        // Auftragsstatus aus Simulator
        auftragStatusField.setValue(simStatus.jobState().name());
        betriebsartField.setValue(simStatus.craneMode().name());
        zangenzustandField.setValue(simStatus.gripperState().name());

        // Aktuelle Position (letzte Zeile im Grid)
        PositionRow aktuell = positionRows.get(4);
        aktuell.setX(simStatus.xPosition());
        aktuell.setY(simStatus.yPosition());
        aktuell.setZ(simStatus.zPosition());
        aktuell.setBewertung(simStatus.workPhase().name());

        // Debug: Position in Statuszeit anzeigen
        statuszeitField.setValue(String.format("X:%d Y:%d Z:%d",
            simStatus.xPosition(), simStatus.yPosition(), simStatus.zPosition()));

        positionGrid.getDataProvider().refreshAll();
    }

    private void updateDisplay(CraneStatusDTO status) {
        // Auftragsstatus
        String jobState = status.getJobState();
        if (jobState != null) {
            auftragStatusField.setValue(status.getJobStateDisplay());
        }

        // Betriebsart
        betriebsartField.setValue(status.getModeDisplay());

        // Zangenzustand
        zangenzustandField.setValue(status.getGripperStateDisplay());

        // Tore/Türen Status
        Boolean gatesOpen = status.getGatesOpen();
        Boolean doorsOpen = status.getDoorsOpen();
        toreOffen.setValue(gatesOpen != null && gatesOpen);

        // Incident-basierte Checkboxen
        boolean hasIncident = status.hasIncident();
        spsFehler.setValue(hasIncident && "SPS_ERROR".equals(status.getIncident()));
        verbindungTot.setValue(hasIncident && "CONNECTION_LOST".equals(status.getIncident()));

        // Aktuelle Position (letzte Zeile im Grid)
        int x = status.getXPosition() != null ? status.getXPosition() : 0;
        int y = status.getYPosition() != null ? status.getYPosition() : 0;
        int z = status.getZPosition() != null ? status.getZPosition() : 0;

        PositionRow aktuell = positionRows.get(4);
        aktuell.setX(x);
        aktuell.setY(y);
        aktuell.setZ(z);

        // Abholen Position
        if (status.getFromStockyardNo() != null) {
            PositionRow abholen = positionRows.get(0);
            abholen.setPlatzNr(status.getFromStockyardNo());
        }

        // Ablegen Position
        if (status.getToStockyardNo() != null) {
            PositionRow ablegen = positionRows.get(2);
            ablegen.setPlatzNr(status.getToStockyardNo());
        }

        // Daemon State als Bewertung
        String daemonState = status.getDaemonState();
        if (daemonState != null) {
            positionRows.get(4).setBewertung(daemonState);
        }

        positionGrid.getDataProvider().refreshAll();

        // Kommando-Bereich
        if (status.getFromStockyardNo() != null && status.getToStockyardNo() != null) {
            kommandoArtField.setValue(status.getFromStockyardNo() + " > " + status.getToStockyardNo());
        }

        // Barren-Infos
        if (status.hasIngot()) {
            barrenNrField.setValue(status.getIngotNo() != null ? status.getIngotNo() : "");
            if (status.getIngotProductNo() != null) {
                artikelNrField.setValue(status.getIngotProductNo());
            }
            if (status.getIngotLength() != null) {
                laengeField.setValue(String.valueOf(status.getIngotLength()));
                langerBarrenCheck.setValue(status.getIngotLength() > 6000);
            }
            if (status.getIngotWidth() != null) {
                breiteField.setValue(String.valueOf(status.getIngotWidth()));
            }
        } else {
            barrenNrField.setValue("");
            artikelNrField.setValue("");
            laengeField.setValue("");
            breiteField.setValue("");
            langerBarrenCheck.setValue(false);
        }

        // Alarm-Anzeige
        updateAlarmDisplay(status);
    }

    private void updateAlarmDisplay(CraneStatusDTO status) {
        boolean hasIncident = status.hasIncident();

        if (hasIncident) {
            // Alarm-Text zusammenstellen
            StringBuilder alarmMessage = new StringBuilder();
            alarmMessage.append("STÖRUNG: ");

            String incident = status.getIncident();
            if (incident != null) {
                alarmMessage.append(incident);
            }

            String incidentText = status.getIncidentText();
            if (incidentText != null && !incidentText.isEmpty()) {
                alarmMessage.append(" - ").append(incidentText);
            }

            alarmText.setText(alarmMessage.toString());

            // Blink-Animation aktivieren
            alarmBlinkState = !alarmBlinkState;
            if (alarmBlinkState) {
                alarmSection.getStyle()
                    .set("background-color", "#F44336")
                    .set("animation", "blink 0.5s infinite");
            }

            // Zeitstempel aktualisieren
            alarmSection.getElement().executeJs(
                "var timeEl = this.querySelector('#alarm-time');" +
                "if (timeEl) timeEl.textContent = new Date().toLocaleTimeString('de-DE');"
            );

            alarmSection.setVisible(true);
        } else {
            alarmSection.setVisible(false);
            alarmSection.getStyle().remove("animation");
        }
    }

    // === Inner Class for Position Grid ===

    public static class PositionRow {
        private String label;
        private String platzNr;
        private int x;
        private int y;
        private int z;
        private String zeitpunkt;
        private String bewertung;

        public PositionRow(String label, String platzNr, int x, int y, int z, String zeitpunkt, String bewertung) {
            this.label = label;
            this.platzNr = platzNr;
            this.x = x;
            this.y = y;
            this.z = z;
            this.zeitpunkt = zeitpunkt;
            this.bewertung = bewertung;
        }

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }

        public String getPlatzNr() { return platzNr; }
        public void setPlatzNr(String platzNr) { this.platzNr = platzNr; }

        public int getX() { return x; }
        public void setX(int x) { this.x = x; }

        public int getY() { return y; }
        public void setY(int y) { this.y = y; }

        public int getZ() { return z; }
        public void setZ(int z) { this.z = z; }

        public String getZeitpunkt() { return zeitpunkt; }
        public void setZeitpunkt(String zeitpunkt) { this.zeitpunkt = zeitpunkt; }

        public String getBewertung() { return bewertung; }
        public void setBewertung(String bewertung) { this.bewertung = bewertung; }
    }
}
