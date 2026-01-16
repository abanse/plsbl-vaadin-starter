package com.hydro.plsbl.ui.view;

import com.hydro.plsbl.dto.CalloffDTO;
import com.hydro.plsbl.dto.CalloffSearchCriteria;
import com.hydro.plsbl.dto.IngotDTO;
import com.hydro.plsbl.dto.TransportOrderDTO;
import com.hydro.plsbl.plc.PlcService;
import com.hydro.plsbl.plc.dto.PlcCommand;
import com.hydro.plsbl.plc.dto.JobState;
import com.hydro.plsbl.service.BeladungBroadcaster;
import com.hydro.plsbl.service.BeladungStateService;
import com.hydro.plsbl.service.CalloffService;
import com.hydro.plsbl.service.IngotService;
import com.hydro.plsbl.service.SettingsService;
import com.hydro.plsbl.service.StockyardService;
import com.hydro.plsbl.service.TransportOrderService;
import com.hydro.plsbl.ui.MainLayout;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Beladung-Ansicht - LKW Beladung und Versand
 *
 * Simulation:
 * - Grüne Barren = geplant (noch im Lager)
 * - Gelbe Barren = geladen (auf dem Trailer)
 */
@Route(value = "beladung", layout = MainLayout.class)
@PageTitle("Beladung | PLSBL")
public class BeladungView extends VerticalLayout {

    private static final Logger log = LoggerFactory.getLogger(BeladungView.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    // Farben für Barren-Status
    private static final String FARBE_GEPLANT = "linear-gradient(180deg, #81C784 0%, #4CAF50 100%)";  // Grün
    private static final String FARBE_GELADEN = "linear-gradient(180deg, #FFD54F 0%, #FFC107 100%)";  // Gelb
    private static final String BORDER_GEPLANT = "#388E3C";
    private static final String BORDER_GELADEN = "#FF8F00";

    private final IngotService ingotService;
    private final StockyardService stockyardService;
    private final TransportOrderService transportOrderService;
    private final CalloffService calloffService;
    private final PlcService plcService;
    private final SettingsService settingsService;
    private final BeladungStateService stateService;
    private final BeladungBroadcaster broadcaster;

    // Trailer-Position (in mm) - Position des Trailers im Lager-Grid (sichtbar!)
    // Muss innerhalb des sichtbaren Lager-Bereichs liegen (ca. X=6000-48000, Y=6000-24000)
    private static final int TRAILER_X = 45000;  // Rechte Seite des Lagers (Spalte ~15)
    private static final int TRAILER_Y = 9000;   // Vorderer Bereich (Reihe ~3)
    private static final int TRAILER_Z = 2000;

    // Konfigurations-Felder (wie im Original-Bild)
    private ComboBox<String> lieferortCombo;
    private ComboBox<String> lagerplatzArtCombo;
    private IntegerField maxGewichtField;
    private IntegerField maxBarrenField;
    private ComboBox<String> barrenartCombo;
    private IntegerField minBreiteField;
    private IntegerField maxBreiteField;
    private TextField vorzugsabrufField;
    private Checkbox terminoptimierungCheckbox;
    private boolean abrufGesperrt = false;

    // Filter-Felder (Suche)
    private TextField calloffNumberField;
    private TextField orderNumberField;
    private TextField customerNumberField;
    private TextField destinationField;
    private TextField sapProductField;
    private Checkbox incompleteOnlyCheckbox;
    private Checkbox approvedOnlyCheckbox;
    private Checkbox notApprovedOnlyCheckbox;

    // Beladungs-Bereich
    private TextField beladungsNrField;
    private Span gewichtVorneLabel;
    private Span gewichtHintenLabel;
    private Span gesamtGewichtLabel;
    private Span statusLabel;
    private Span barrenAnzahlLabel;
    private Div ladeflaeche;
    private ProgressBar fortschrittBar;

    // Barren-Listen
    private List<IngotDTO> geplanteBarren = new ArrayList<>();   // Geplant für Beladung
    private List<IngotDTO> geladeneBarren = new ArrayList<>();   // Gelb - auf Trailer

    // Grids und Ergebnis-Anzeige
    private Grid<CalloffDTO> calloffGrid;
    private Grid<TransportOrderDTO> transportGrid;
    private CalloffDTO selectedCalloff;
    private Span resultCountLabel;
    private Button approveButton;
    private Button revokeButton;

    // Beladungs-Status
    private boolean beladungAktiv = false;
    private boolean beladungLaeuft = false;
    private int beladungsNummer = 0;
    private boolean langBarrenModus = false;
    private boolean kranKommandoGesendet = false;  // Verhindert mehrfaches Senden

    // Scheduler für Animation
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> beladungsTask;

    // Buttons
    private Button startenBtn;
    private Button stoppenBtn;

    public BeladungView(IngotService ingotService,
                        StockyardService stockyardService,
                        TransportOrderService transportOrderService,
                        CalloffService calloffService,
                        PlcService plcService,
                        SettingsService settingsService,
                        BeladungStateService stateService,
                        BeladungBroadcaster broadcaster) {
        this.ingotService = ingotService;
        this.stockyardService = stockyardService;
        this.transportOrderService = transportOrderService;
        this.calloffService = calloffService;
        this.plcService = plcService;
        this.settingsService = settingsService;
        this.stateService = stateService;
        this.broadcaster = broadcaster;

        setSizeFull();
        setPadding(true);
        setSpacing(false);

        createFilterSection();
        createMainSection();
        createDataGridsSection();
        createFooterButtons();

        loadData();
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        scheduler = Executors.newSingleThreadScheduledExecutor();

        // Status aus Session wiederherstellen
        restoreStateFromService();
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        super.onDetach(detachEvent);

        // Status in Session speichern (NICHT stoppen!)
        saveStateToService();

        // Nur den Scheduler stoppen, nicht die Beladung abbrechen
        if (beladungsTask != null) {
            beladungsTask.cancel(false);
            beladungsTask = null;
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    /**
     * Speichert den aktuellen Status in den Session-Service
     */
    private void saveStateToService() {
        stateService.setBeladungAktiv(beladungAktiv);
        stateService.setBeladungLaeuft(beladungLaeuft);
        stateService.setLangBarrenModus(langBarrenModus);
        stateService.setKranKommandoGesendet(kranKommandoGesendet);
        stateService.setGeplanteBarren(geplanteBarren);
        stateService.setGeladeneBarren(geladeneBarren);
        if (beladungsNrField != null && beladungsNrField.getValue() != null) {
            stateService.setBeladungsNr(beladungsNrField.getValue());
        }
        log.debug("Beladungs-Status gespeichert: aktiv={}, laeuft={}, geplant={}, geladen={}",
            beladungAktiv, beladungLaeuft, geplanteBarren.size(), geladeneBarren.size());
    }

    /**
     * Sendet ein Broadcast-Event an alle registrierten Views (z.B. LagerView)
     */
    private void broadcastStatus() {
        if (broadcaster == null) return;
        int loadedCount = geladeneBarren.size();
        int totalCount = geplanteBarren.size() + geladeneBarren.size();
        broadcaster.broadcastStatusUpdate(loadedCount, totalCount, beladungLaeuft);
    }

    /**
     * Stellt den Status aus dem Session-Service wieder her
     */
    private void restoreStateFromService() {
        beladungAktiv = stateService.isBeladungAktiv();
        beladungLaeuft = stateService.isBeladungLaeuft();
        langBarrenModus = stateService.isLangBarrenModus();
        kranKommandoGesendet = stateService.isKranKommandoGesendet();
        geplanteBarren = new ArrayList<>(stateService.getGeplanteBarren());
        geladeneBarren = new ArrayList<>(stateService.getGeladeneBarren());
        beladungsNummer = stateService.getBeladungsNummer();

        // UI aktualisieren
        if (beladungsNrField != null && !stateService.getBeladungsNr().isEmpty()) {
            beladungsNrField.setValue(stateService.getBeladungsNr());
        }

        updateAnzeigen();
        updateLadeflaeche();

        // Buttons entsprechend setzen
        if (startenBtn != null && stoppenBtn != null) {
            startenBtn.setEnabled(beladungAktiv && !beladungLaeuft && !geplanteBarren.isEmpty());
            stoppenBtn.setEnabled(beladungLaeuft);
        }

        // Wenn Beladung lief, Scheduler wieder starten
        if (beladungLaeuft && !geplanteBarren.isEmpty()) {
            log.info("Beladung wird fortgesetzt: {} Barren noch offen", geplanteBarren.size());
            statusLabel.setText("Beladung wird fortgesetzt...");
            statusLabel.getStyle().set("color", "#FF9800");
            starteBeladungFort();
        } else if (beladungAktiv) {
            if (geplanteBarren.isEmpty() && !geladeneBarren.isEmpty()) {
                statusLabel.setText("Beladung fertig! " + geladeneBarren.size() + " Barren geladen.");
                statusLabel.getStyle().set("color", "#4CAF50");
            } else {
                statusLabel.setText("Beladung pausiert - " + geplanteBarren.size() + " Barren offen");
                statusLabel.getStyle().set("color", "#FF9800");
            }
        }

        log.debug("Beladungs-Status wiederhergestellt: aktiv={}, laeuft={}, geplant={}, geladen={}",
            beladungAktiv, beladungLaeuft, geplanteBarren.size(), geladeneBarren.size());
    }

    /**
     * Setzt die Beladung fort (nach Tab-Wechsel)
     */
    private void starteBeladungFort() {
        UI ui = getUI().orElse(null);
        if (ui == null) return;

        // Prüfen ob Kran/Simulator verfügbar ist
        boolean craneAvailable = plcService.isConnected() || plcService.isSimulatorMode();

        beladungsTask = scheduler.scheduleAtFixedRate(() -> {
            if (!beladungLaeuft) {
                return;
            }

            ui.access(() -> {
                if (craneAvailable) {
                    // Mit Kran: Status prüfen
                    var plcStatus = plcService.getCurrentStatus();
                    JobState jobState = plcStatus != null ? plcStatus.getJobState() : null;

                    if (jobState == JobState.IDLE && !kranKommandoGesendet && !geplanteBarren.isEmpty()) {
                        // Kran ist bereit - nächsten Barren senden
                        IngotDTO barren = geplanteBarren.get(0);
                        sendeCranKommando(barren);
                        kranKommandoGesendet = true;
                        statusLabel.setText("Kran holt " + barren.getIngotNo() + " vom Lager...");
                    } else if (jobState == JobState.IDLE && kranKommandoGesendet) {
                        // Job fertig - Barren als geladen markieren
                        if (!geplanteBarren.isEmpty()) {
                            IngotDTO barren = geplanteBarren.remove(0);
                            geladeneBarren.add(barren);
                            kranKommandoGesendet = false;
                            saveStateToService();

                            statusLabel.setText(barren.getIngotNo() + " auf Trailer abgelegt!");
                            updateLadeflaeche();
                            updateAnzeigen();
                            broadcastStatus();  // LagerView aktualisieren

                            if (geplanteBarren.isEmpty()) {
                                beladungFertig();
                            }
                        }
                    } else if (jobState == JobState.STARTED || jobState == JobState.LOADED || jobState == JobState.DROPPED) {
                        String phase = plcStatus != null && plcStatus.getWorkPhase() != null
                            ? plcStatus.getWorkPhase().getDisplayName() : "arbeitet...";
                        statusLabel.setText("Kran: " + phase);
                    }
                } else {
                    // Ohne Kran: direkt laden
                    if (!geplanteBarren.isEmpty()) {
                        IngotDTO barren = geplanteBarren.remove(0);
                        geladeneBarren.add(barren);
                        saveStateToService();

                        statusLabel.setText("Lade " + barren.getIngotNo() + "...");
                        updateLadeflaeche();
                        updateAnzeigen();
                        broadcastStatus();  // LagerView aktualisieren

                        if (geplanteBarren.isEmpty()) {
                            beladungFertig();
                        }
                    }
                }
            });
        }, 500, 800, TimeUnit.MILLISECONDS);
    }

    private void createFilterSection() {
        // Konfigurations-Sektion (wie im Original-Bild)
        VerticalLayout configSection = new VerticalLayout();
        configSection.setPadding(false);
        configSection.setSpacing(true);
        configSection.getStyle()
            .set("background-color", "#f5f5f5")
            .set("padding", "15px")
            .set("border-radius", "4px")
            .set("margin-bottom", "10px");

        // Zeile 1: Lieferort, max. Gewicht, max. Anzahl Barren
        HorizontalLayout row1 = new HorizontalLayout();
        row1.setWidthFull();
        row1.setSpacing(true);
        row1.setAlignItems(FlexComponent.Alignment.BASELINE);

        lieferortCombo = new ComboBox<>("Lieferort");
        lieferortCombo.setItems("NF2", "NF3", "GIESS", "SAEGE", "LKW", "EXTERN");
        lieferortCombo.setValue("NF2");
        lieferortCombo.setWidth("100px");

        maxGewichtField = new IntegerField("max. Gewicht [kg]");
        maxGewichtField.setValue(64000);
        maxGewichtField.setWidth("130px");
        maxGewichtField.setMin(1000);
        maxGewichtField.setMax(100000);
        maxGewichtField.setStep(1000);

        maxBarrenField = new IntegerField("max. Anzahl Barren");
        maxBarrenField.setValue(6);
        maxBarrenField.setWidth("130px");
        maxBarrenField.setMin(1);
        maxBarrenField.setMax(20);

        row1.add(lieferortCombo, maxGewichtField, maxBarrenField);

        // Zeile 2: Lagerplatz-Art, Barrenart, min/max Breite
        HorizontalLayout row2 = new HorizontalLayout();
        row2.setWidthFull();
        row2.setSpacing(true);
        row2.setAlignItems(FlexComponent.Alignment.BASELINE);

        lagerplatzArtCombo = new ComboBox<>("Lagerplatz-Art");
        lagerplatzArtCombo.setItems("gemischt", "kurz", "lang");
        lagerplatzArtCombo.setValue("gemischt");
        lagerplatzArtCombo.setWidth("120px");
        lagerplatzArtCombo.getElement().setAttribute("title", "kurz: <4m, lang: >=4m");

        barrenartCombo = new ComboBox<>("Barrenart");
        barrenartCombo.setItems("gemischt", "Walzbarren", "Pressbolzen", "Rundbarren");
        barrenartCombo.setValue("gemischt");
        barrenartCombo.setWidth("130px");

        minBreiteField = new IntegerField("min. Breite [mm]");
        minBreiteField.setValue(0);
        minBreiteField.setWidth("120px");
        minBreiteField.setMin(0);
        minBreiteField.setMax(9999);

        maxBreiteField = new IntegerField("max. Breite [mm]");
        maxBreiteField.setValue(9999);
        maxBreiteField.setWidth("120px");
        maxBreiteField.setMin(0);
        maxBreiteField.setMax(9999);

        row2.add(lagerplatzArtCombo, barrenartCombo, minBreiteField, maxBreiteField);

        // Zeile 3: Vorzugsabruf, Abrufe sperren, Terminoptimierung
        HorizontalLayout row3 = new HorizontalLayout();
        row3.setWidthFull();
        row3.setSpacing(true);
        row3.setAlignItems(FlexComponent.Alignment.BASELINE);

        vorzugsabrufField = new TextField("Vorzugsabruf");
        vorzugsabrufField.setPlaceholder("Abruf-Nr. eingeben");
        vorzugsabrufField.setWidth("150px");
        vorzugsabrufField.setClearButtonVisible(true);
        vorzugsabrufField.getElement().setAttribute("title", "Priorität für diesen Abruf");

        Button abrufeSperrenBtn = new Button("Abrufe sperren", VaadinIcon.LOCK.create());
        abrufeSperrenBtn.addClickListener(e -> toggleAbrufSperre(abrufeSperrenBtn));

        terminoptimierungCheckbox = new Checkbox("Terminoptimierung?");
        terminoptimierungCheckbox.setValue(true);
        terminoptimierungCheckbox.getElement().setAttribute("title", "Abrufe nach Liefertermin sortieren");

        // Spacer
        Div spacer = new Div();
        spacer.getStyle().set("flex-grow", "1");

        row3.add(vorzugsabrufField, abrufeSperrenBtn, terminoptimierungCheckbox, spacer);

        // Zeile 4: Filter für Abruf-Suche (erweitert)
        HorizontalLayout row4 = new HorizontalLayout();
        row4.setWidthFull();
        row4.setSpacing(true);
        row4.setAlignItems(FlexComponent.Alignment.BASELINE);

        calloffNumberField = new TextField("Abruf-Nr.");
        calloffNumberField.setPlaceholder("z.B. 12345");
        calloffNumberField.setWidth("100px");
        calloffNumberField.setClearButtonVisible(true);

        orderNumberField = new TextField("Auftrags-Nr.");
        orderNumberField.setPlaceholder("z.B. 4500001");
        orderNumberField.setWidth("110px");
        orderNumberField.setClearButtonVisible(true);

        customerNumberField = new TextField("Kunden-Nr.");
        customerNumberField.setPlaceholder("z.B. 100123");
        customerNumberField.setWidth("100px");
        customerNumberField.setClearButtonVisible(true);

        destinationField = new TextField("Ziel");
        destinationField.setPlaceholder("z.B. LKW");
        destinationField.setWidth("80px");
        destinationField.setClearButtonVisible(true);

        sapProductField = new TextField("SAP-Artikel");
        sapProductField.setPlaceholder("z.B. MAT123");
        sapProductField.setWidth("100px");
        sapProductField.setClearButtonVisible(true);

        incompleteOnlyCheckbox = new Checkbox("nur offene");
        incompleteOnlyCheckbox.setValue(true);

        approvedOnlyCheckbox = new Checkbox("nur genehmigte");
        approvedOnlyCheckbox.addValueChangeListener(e -> {
            if (e.getValue()) notApprovedOnlyCheckbox.setValue(false);
        });

        notApprovedOnlyCheckbox = new Checkbox("nur nicht genehmigte");
        notApprovedOnlyCheckbox.addValueChangeListener(e -> {
            if (e.getValue()) approvedOnlyCheckbox.setValue(false);
        });

        Button searchButton = new Button("Suchen", VaadinIcon.SEARCH.create());
        searchButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        searchButton.addClickListener(e -> onSearch());

        Button resetButton = new Button("Reset", VaadinIcon.REFRESH.create());
        resetButton.addClickListener(e -> onReset());

        row4.add(calloffNumberField, orderNumberField, customerNumberField, destinationField, sapProductField,
                 incompleteOnlyCheckbox, approvedOnlyCheckbox, searchButton, resetButton);

        configSection.add(row1, row2, row3, row4);
        add(configSection);
    }

    private void toggleAbrufSperre(Button btn) {
        abrufGesperrt = !abrufGesperrt;
        if (abrufGesperrt) {
            btn.setText("Abrufe entsperren");
            btn.setIcon(VaadinIcon.UNLOCK.create());
            btn.addThemeVariants(ButtonVariant.LUMO_ERROR);
            Notification.show("Abrufe gesperrt - keine automatische Ermittlung",
                3000, Notification.Position.BOTTOM_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_CONTRAST);
        } else {
            btn.setText("Abrufe sperren");
            btn.setIcon(VaadinIcon.LOCK.create());
            btn.removeThemeVariants(ButtonVariant.LUMO_ERROR);
            Notification.show("Abrufe entsperrt",
                2000, Notification.Position.BOTTOM_CENTER);
        }
    }

    private void createMainSection() {
        // Linke Seite: LKW-Visualisierung
        VerticalLayout trailerSection = new VerticalLayout();
        trailerSection.setSizeFull();
        trailerSection.setPadding(false);
        trailerSection.setSpacing(true);

        // Status-Leiste
        HorizontalLayout statusBar = new HorizontalLayout();
        statusBar.setWidthFull();
        statusBar.setAlignItems(FlexComponent.Alignment.CENTER);
        statusBar.getStyle()
            .set("background-color", "#263238")
            .set("padding", "8px 15px")
            .set("border-radius", "4px");

        statusLabel = new Span("Bereit");
        statusLabel.getStyle()
            .set("color", "#4CAF50")
            .set("font-weight", "bold");

        fortschrittBar = new ProgressBar();
        fortschrittBar.setWidth("200px");
        fortschrittBar.setValue(0);
        fortschrittBar.getStyle().set("margin-left", "auto");

        // Legende
        Div legende = new Div();
        legende.getStyle()
            .set("display", "flex")
            .set("gap", "15px")
            .set("margin-left", "20px");

        Div legendeGruen = createLegendItem("#4CAF50", "Geplant (im Lager)");
        Div legendeGelb = createLegendItem("#FFC107", "Geladen (auf Trailer)");
        legende.add(legendeGruen, legendeGelb);

        statusBar.add(statusLabel, legende, fortschrittBar);
        trailerSection.add(statusBar);

        Div trailerVisualization = createTrailerVisualization();
        trailerSection.add(trailerVisualization);
        trailerSection.setFlexGrow(1, trailerVisualization);

        // Rechte Seite: Steuerung
        VerticalLayout controlSection = new VerticalLayout();
        controlSection.setWidth("300px");
        controlSection.setPadding(true);
        controlSection.setSpacing(true);
        controlSection.getStyle()
            .set("background-color", "#fafafa")
            .set("border-radius", "4px");

        // Beladungs-Nr
        beladungsNrField = new TextField("Beladungs-Nr.");
        beladungsNrField.setWidthFull();
        beladungsNrField.setReadOnly(true);
        controlSection.add(beladungsNrField);

        // Gewichts-Anzeigen (wie im Original)
        FormLayout gewichtForm = new FormLayout();
        gewichtForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        gewichtVorneLabel = createInfoDisplay("0");
        gewichtHintenLabel = createInfoDisplay("0");
        gesamtGewichtLabel = createInfoDisplay("0");

        gewichtForm.addFormItem(gewichtVorneLabel, "Gewicht vorne [kg]");
        gewichtForm.addFormItem(gewichtHintenLabel, "Gewicht hinten [kg]");
        gewichtForm.addFormItem(gesamtGewichtLabel, "Gesamtgewicht [kg]");
        controlSection.add(gewichtForm);

        // Fortschritts-Anzeigen
        FormLayout infoForm = new FormLayout();
        infoForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        barrenAnzahlLabel = createInfoDisplay("0 / 0");
        infoForm.addFormItem(barrenAnzahlLabel, "Fortschritt");
        controlSection.add(infoForm);

        // Aktions-Buttons
        H4 aktionenTitle = new H4("Beladung");
        aktionenTitle.getStyle().set("margin", "15px 0 5px 0");
        controlSection.add(aktionenTitle);

        HorizontalLayout actionButtons1 = new HorizontalLayout();
        actionButtons1.setWidthFull();
        actionButtons1.setSpacing(true);

        Button neueBeladungBtn = new Button("Neue Beladung", VaadinIcon.PLUS.create());
        neueBeladungBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        neueBeladungBtn.addClickListener(e -> neueBeladung());

        Button ermittelnBtn = new Button("Barren ermitteln", VaadinIcon.SEARCH.create());
        ermittelnBtn.addClickListener(e -> ermittleBarren());

        actionButtons1.add(neueBeladungBtn, ermittelnBtn);
        controlSection.add(actionButtons1);

        HorizontalLayout actionButtons2 = new HorizontalLayout();
        actionButtons2.setWidthFull();
        actionButtons2.setSpacing(true);

        startenBtn = new Button("Beladung starten", VaadinIcon.PLAY.create());
        startenBtn.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        startenBtn.setEnabled(false);
        startenBtn.addClickListener(e -> starteBeladung());

        stoppenBtn = new Button("Stoppen", VaadinIcon.STOP.create());
        stoppenBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);
        stoppenBtn.setEnabled(false);
        stoppenBtn.addClickListener(e -> stoppeBeladung());

        actionButtons2.add(startenBtn, stoppenBtn);
        controlSection.add(actionButtons2);

        Button abbrechenBtn = new Button("Abbrechen", VaadinIcon.CLOSE.create());
        abbrechenBtn.setWidthFull();
        abbrechenBtn.addClickListener(e -> abbrechenBeladung());
        controlSection.add(abbrechenBtn);

        // Hauptbereich zusammenbauen
        HorizontalLayout mainSection = new HorizontalLayout(trailerSection, controlSection);
        mainSection.setSizeFull();
        mainSection.setSpacing(true);
        mainSection.setFlexGrow(1, trailerSection);

        add(mainSection);
        setFlexGrow(1, mainSection);
    }

    private Div createLegendItem(String color, String text) {
        Div item = new Div();
        item.getStyle()
            .set("display", "flex")
            .set("align-items", "center")
            .set("gap", "5px");

        Div colorBox = new Div();
        colorBox.getStyle()
            .set("width", "12px")
            .set("height", "12px")
            .set("background-color", color)
            .set("border-radius", "2px");

        Span label = new Span(text);
        label.getStyle()
            .set("color", "white")
            .set("font-size", "11px");

        item.add(colorBox, label);
        return item;
    }

    private Div createTrailerVisualization() {
        Div container = new Div();
        container.setSizeFull();
        container.getStyle()
            .set("background-color", "#e8e8e8")
            .set("border", "1px solid #ccc")
            .set("border-radius", "4px")
            .set("display", "flex")
            .set("align-items", "flex-end")
            .set("justify-content", "center")
            .set("min-height", "250px")
            .set("padding-bottom", "20px");

        Div trailer = new Div();
        trailer.getStyle()
            .set("display", "flex")
            .set("flex-direction", "row")
            .set("align-items", "flex-end");

        // Zugmaschine (Kabine)
        Div kabine = new Div();
        kabine.getStyle()
            .set("width", "80px")
            .set("height", "90px")
            .set("background-color", "#FF9800")
            .set("border-radius", "8px 8px 0 0")
            .set("border", "3px solid #E65100")
            .set("border-bottom", "none")
            .set("position", "relative")
            .set("margin-right", "-3px");

        Div fenster = new Div();
        fenster.getStyle()
            .set("position", "absolute")
            .set("top", "10px")
            .set("left", "10px")
            .set("width", "55px")
            .set("height", "30px")
            .set("background-color", "#81D4FA")
            .set("border-radius", "4px")
            .set("border", "2px solid #0288D1");
        kabine.add(fenster);

        Div radKabine = createWheel(20, 20);
        radKabine.getStyle()
            .set("position", "absolute")
            .set("bottom", "-12px")
            .set("left", "25px");
        kabine.add(radKabine);

        // Ladefläche
        ladeflaeche = new Div();
        ladeflaeche.addClassName("ladeflaeche");
        ladeflaeche.getStyle()
            .set("width", "350px")
            .set("height", "50px")
            .set("background-color", "#8D6E63")
            .set("border", "3px solid #5D4037")
            .set("border-radius", "0 4px 4px 0")
            .set("position", "relative")
            .set("display", "flex")
            .set("flex-direction", "row")
            .set("align-items", "flex-end")
            .set("justify-content", "flex-start")
            .set("padding", "5px")
            .set("gap", "3px");

        Div rad1 = createWheel(18, 18);
        rad1.getStyle()
            .set("position", "absolute")
            .set("bottom", "-11px")
            .set("left", "60px");
        ladeflaeche.add(rad1);

        Div rad2 = createWheel(18, 18);
        rad2.getStyle()
            .set("position", "absolute")
            .set("bottom", "-11px")
            .set("right", "60px");
        ladeflaeche.add(rad2);

        trailer.add(kabine, ladeflaeche);

        Div boden = new Div();
        boden.getStyle()
            .set("position", "absolute")
            .set("bottom", "0")
            .set("left", "0")
            .set("right", "0")
            .set("height", "8px")
            .set("background-color", "#424242");

        Div trailerContainer = new Div();
        trailerContainer.getStyle()
            .set("position", "relative")
            .set("padding-bottom", "10px");
        trailerContainer.add(trailer, boden);

        container.add(trailerContainer);
        return container;
    }

    private Div createWheel(int width, int height) {
        Div wheel = new Div();
        wheel.addClassName("wheel");
        wheel.getStyle()
            .set("width", width + "px")
            .set("height", height + "px")
            .set("background-color", "#212121")
            .set("border-radius", "50%")
            .set("border", "2px solid #000");
        return wheel;
    }

    /**
     * Aktualisiert die Ladefläche nur mit geladenen Barren (gelb)
     */
    private void updateLadeflaeche() {
        if (ladeflaeche == null) return;

        // Alle Kinder außer den Rädern entfernen
        ladeflaeche.getChildren()
            .filter(c -> {
                String className = c.getElement().getAttribute("class");
                return className == null || !className.contains("wheel");
            })
            .forEach(c -> {
                if (!c.getElement().getStyle().has("bottom")) {
                    ladeflaeche.remove(c);
                }
            });

        // Nur geladene Barren anzeigen (gelb)
        List<BarrenMitStatus> geladene = new ArrayList<>();
        for (IngotDTO b : geladeneBarren) {
            geladene.add(new BarrenMitStatus(b, true));
        }

        // Stapel-Container erstellen - Barren liegen AUF der Ladefläche
        Div stapelContainer = new Div();
        stapelContainer.getStyle()
            .set("position", "absolute")
            .set("bottom", "100%")
            .set("left", "10px")
            .set("right", "10px")
            .set("display", "flex")
            .set("flex-direction", "row")
            .set("align-items", "flex-end")
            .set("justify-content", "center")
            .set("gap", "6px")
            .set("padding-bottom", "2px");

        if (geladene.isEmpty()) {
            Div placeholderContainer = new Div();
            placeholderContainer.getStyle()
                .set("position", "absolute")
                .set("top", "50%")
                .set("left", "50%")
                .set("transform", "translate(-50%, -50%)");
            Span placeholder = new Span("Leer");
            placeholder.getStyle()
                .set("color", "#5D4037")
                .set("font-size", "11px")
                .set("font-style", "italic");
            placeholderContainer.add(placeholder);
            ladeflaeche.add(placeholderContainer);
        } else {
            if (langBarrenModus) {
                Div stapel = createBarrenStapelMitStatus(geladene, true);
                stapelContainer.add(stapel);
            } else {
                List<BarrenMitStatus> links = new ArrayList<>();
                List<BarrenMitStatus> rechts = new ArrayList<>();
                for (int i = 0; i < geladene.size(); i++) {
                    if (i % 2 == 0) links.add(geladene.get(i));
                    else rechts.add(geladene.get(i));
                }
                if (!links.isEmpty()) stapelContainer.add(createBarrenStapelMitStatus(links, false));
                if (!rechts.isEmpty()) stapelContainer.add(createBarrenStapelMitStatus(rechts, false));
            }
            ladeflaeche.add(stapelContainer);
        }
    }

    private static class BarrenMitStatus {
        final IngotDTO barren;
        final boolean geladen;  // true = gelb, false = grün

        BarrenMitStatus(IngotDTO barren, boolean geladen) {
            this.barren = barren;
            this.geladen = geladen;
        }
    }

    private Div createBarrenStapelMitStatus(List<BarrenMitStatus> barren, boolean langFormat) {
        Div stapel = new Div();
        stapel.getStyle()
            .set("display", "flex")
            .set("flex-direction", "column-reverse")
            .set("align-items", "center")
            .set("gap", "2px");

        // Barren-Größe angepasst an Ladefläche (280px breit)
        int breite = langFormat ? 220 : 110;
        int hoehe = 32;

        for (BarrenMitStatus bms : barren) {
            IngotDTO b = bms.barren;
            Div barrenDiv = new Div();

            String farbe = bms.geladen ? FARBE_GELADEN : FARBE_GEPLANT;
            String border = bms.geladen ? BORDER_GELADEN : BORDER_GEPLANT;

            barrenDiv.getStyle()
                .set("width", breite + "px")
                .set("height", hoehe + "px")
                .set("background", farbe)
                .set("border", "3px solid " + border)
                .set("border-radius", "3px")
                .set("display", "flex")
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("font-size", "12px")
                .set("color", "#263238")
                .set("font-weight", "bold")
                .set("box-shadow", "2px 2px 4px rgba(0,0,0,0.3)")
                .set("transition", "background 0.5s ease");

            String label = b.getIngotNo() != null ? b.getIngotNo() : "?";
            barrenDiv.setText(label);

            String status = bms.geladen ? "GELADEN" : "GEPLANT";
            barrenDiv.getElement().setAttribute("title",
                String.format("%s - %d kg [%s]",
                    b.getIngotNo() != null ? b.getIngotNo() : "?",
                    b.getWeight() != null ? b.getWeight() : 0,
                    status));

            stapel.add(barrenDiv);
        }

        return stapel;
    }

    private void createDataGridsSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(true);
        section.setWidth("100%");

        Tab calloffTab = new Tab("Abrufe (Calloffs)");
        Tab transportTab = new Tab("Transport Aufträge");

        Tabs tabs = new Tabs(calloffTab, transportTab);

        // Ergebnis-Header mit Buttons (wie im Original)
        HorizontalLayout resultsHeader = new HorizontalLayout();
        resultsHeader.setWidthFull();
        resultsHeader.setAlignItems(FlexComponent.Alignment.CENTER);
        resultsHeader.setSpacing(true);

        resultCountLabel = new Span("0 Ergebnisse");
        resultCountLabel.getStyle().set("font-weight", "bold");

        Div spacer = new Div();
        spacer.getStyle().set("flex-grow", "1");

        approveButton = new Button("Genehmigen", VaadinIcon.CHECK.create());
        approveButton.setEnabled(false);
        approveButton.addClickListener(e -> onApprove());

        revokeButton = new Button("Widerrufen", VaadinIcon.CLOSE_SMALL.create());
        revokeButton.setEnabled(false);
        revokeButton.addClickListener(e -> onRevoke());

        resultsHeader.add(resultCountLabel, spacer, approveButton, revokeButton);

        // Calloff Grid (wie im Original)
        calloffGrid = new Grid<>();
        calloffGrid.setHeight("200px");
        calloffGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);

        calloffGrid.addColumn(CalloffDTO::getCalloffNumber)
            .setHeader("Abrufnr.").setWidth("90px").setFlexGrow(0).setSortable(true);
        calloffGrid.addColumn(CalloffDTO::getOrderDisplay)
            .setHeader("Auftrag").setWidth("100px").setFlexGrow(0).setSortable(true);
        calloffGrid.addColumn(CalloffDTO::getCustomerNumber)
            .setHeader("Kunde").setWidth("80px").setFlexGrow(0).setSortable(true);
        calloffGrid.addColumn(CalloffDTO::getDestination)
            .setHeader("Ziel").setWidth("60px").setFlexGrow(0).setSortable(true);
        calloffGrid.addColumn(CalloffDTO::getSapProductNumber)
            .setHeader("SAP-Artikel").setWidth("100px").setFlexGrow(0).setSortable(true);
        calloffGrid.addColumn(CalloffDTO::getAmountRequested)
            .setHeader("Menge").setWidth("65px").setFlexGrow(0).setSortable(true);
        calloffGrid.addColumn(CalloffDTO::getAmountDelivered)
            .setHeader("Geliefert").setWidth("70px").setFlexGrow(0).setSortable(true);
        calloffGrid.addColumn(CalloffDTO::getRemainingAmount)
            .setHeader("Offen").setWidth("55px").setFlexGrow(0).setSortable(true);
        calloffGrid.addColumn(c -> c.getDeliveryDate() != null ? c.getDeliveryDate().format(DATE_FORMATTER) : "-")
            .setHeader("Liefertermin").setWidth("95px").setFlexGrow(0).setSortable(true);
        calloffGrid.addColumn(CalloffDTO::getStatusDisplay)
            .setHeader("Status").setWidth("90px").setFlexGrow(0).setSortable(true);
        calloffGrid.addColumn(CalloffDTO::getCustomerAddress)
            .setHeader("Adresse").setAutoWidth(true).setFlexGrow(1);

        calloffGrid.asSingleSelect().addValueChangeListener(e -> {
            selectedCalloff = e.getValue();
            updateCalloffButtons();
            if (selectedCalloff != null) {
                log.info("Calloff ausgewählt: {}", selectedCalloff.getCalloffNumber());
            }
        });

        // Transport Grid
        transportGrid = new Grid<>();
        transportGrid.setHeight("180px");
        transportGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);
        transportGrid.setVisible(false);

        transportGrid.addColumn(TransportOrderDTO::getTransportNo)
            .setHeader("Auftrag").setWidth("100px");
        transportGrid.addColumn(TransportOrderDTO::getIngotNo)
            .setHeader("Barren").setWidth("100px");
        transportGrid.addColumn(TransportOrderDTO::getFromYardNo)
            .setHeader("von").setWidth("80px");
        transportGrid.addColumn(TransportOrderDTO::getToYardNo)
            .setHeader("nach").setWidth("80px");

        tabs.addSelectedChangeListener(event -> {
            calloffGrid.setVisible(event.getSelectedTab() == calloffTab);
            transportGrid.setVisible(event.getSelectedTab() == transportTab);
        });

        section.add(tabs, resultsHeader, calloffGrid, transportGrid);
        add(section);
    }

    /**
     * Aktualisiert die Genehmigen/Widerrufen Buttons basierend auf der Auswahl
     */
    private void updateCalloffButtons() {
        if (selectedCalloff == null) {
            approveButton.setEnabled(false);
            revokeButton.setEnabled(false);
        } else {
            boolean canApprove = !selectedCalloff.isApproved() && !selectedCalloff.isCompleted();
            boolean canRevoke = selectedCalloff.isApproved() && !selectedCalloff.isCompleted();
            approveButton.setEnabled(canApprove);
            revokeButton.setEnabled(canRevoke);
        }
    }

    /**
     * Genehmigt den ausgewählten Abruf
     */
    private void onApprove() {
        if (selectedCalloff == null || selectedCalloff.isApproved()) return;

        calloffService.approve(selectedCalloff.getId());
        Notification.show("Abruf " + selectedCalloff.getCalloffNumber() + " genehmigt",
            3000, Notification.Position.BOTTOM_CENTER)
            .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        onSearch();
    }

    /**
     * Widerruft die Genehmigung des ausgewählten Abrufs
     */
    private void onRevoke() {
        if (selectedCalloff == null || !selectedCalloff.isApproved()) return;

        calloffService.revokeApproval(selectedCalloff.getId());
        Notification.show("Genehmigung für " + selectedCalloff.getCalloffNumber() + " widerrufen",
            3000, Notification.Position.BOTTOM_CENTER);
        onSearch();
    }

    private void createFooterButtons() {
        HorizontalLayout footer = new HorizontalLayout();
        footer.setWidthFull();
        footer.setSpacing(true);
        footer.setPadding(true);
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        footer.getStyle()
            .set("background-color", "#f0f0f0")
            .set("border-top", "1px solid #ddd")
            .set("margin-top", "10px");

        Span infoLabel = new Span();
        infoLabel.getStyle().set("margin-right", "auto");
        int calloffCount = calloffService.countIncomplete();
        infoLabel.setText(String.format("%d offene Abrufe", calloffCount));

        Button geliefertBtn = new Button("Als geliefert markieren", VaadinIcon.CHECK.create());
        geliefertBtn.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        geliefertBtn.addClickListener(e -> alsGeliefertMarkieren());

        Button buchenBtn = new Button("Buchen", VaadinIcon.DATABASE.create());
        buchenBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        buchenBtn.addClickListener(e -> buchen());

        footer.add(infoLabel, geliefertBtn, buchenBtn);
        add(footer);
    }

    private Span createInfoDisplay(String value) {
        Span span = new Span(value);
        span.getStyle()
            .set("background-color", "white")
            .set("padding", "5px 10px")
            .set("border", "1px solid #ccc")
            .set("border-radius", "4px")
            .set("min-width", "80px")
            .set("text-align", "right")
            .set("display", "inline-block")
            .set("font-weight", "bold");
        return span;
    }

    private void loadData() {
        try {
            // Abrufe laden mit Standard-Kriterien
            onSearch();

            // Transport-Aufträge laden
            List<TransportOrderDTO> orders = transportOrderService.findPendingOrders();
            transportGrid.setItems(orders);

        } catch (Exception e) {
            log.error("Error loading data", e);
        }
    }

    /**
     * Führt die Suche mit den aktuellen Kriterien durch
     */
    private void onSearch() {
        try {
            CalloffSearchCriteria criteria = buildSearchCriteria();
            List<CalloffDTO> calloffs = calloffService.searchByCriteria(criteria);
            calloffGrid.setItems(calloffs);

            // Ergebnis-Anzeige aktualisieren
            if (resultCountLabel != null) {
                resultCountLabel.setText(String.format("%d Ergebnisse", calloffs.size()));
            }

            // Auswahl zurücksetzen
            selectedCalloff = null;
            updateCalloffButtons();

            log.info("Suche ergab {} Ergebnisse", calloffs.size());
        } catch (Exception e) {
            log.error("Fehler bei der Suche", e);
            calloffGrid.setItems(List.of());
            if (resultCountLabel != null) {
                resultCountLabel.setText("Fehler bei der Suche");
            }
        }
    }

    /**
     * Erstellt die Suchkriterien aus den Filter-Feldern
     */
    private CalloffSearchCriteria buildSearchCriteria() {
        CalloffSearchCriteria criteria = new CalloffSearchCriteria();

        String calloffNr = calloffNumberField.getValue();
        if (calloffNr != null && !calloffNr.trim().isEmpty()) {
            criteria.setCalloffNumber(calloffNr.trim());
        }

        String orderNr = orderNumberField.getValue();
        if (orderNr != null && !orderNr.trim().isEmpty()) {
            criteria.setOrderNumber(orderNr.trim());
        }

        String customerNr = customerNumberField.getValue();
        if (customerNr != null && !customerNr.trim().isEmpty()) {
            criteria.setCustomerNumber(customerNr.trim());
        }

        String dest = destinationField.getValue();
        if (dest != null && !dest.trim().isEmpty()) {
            criteria.setDestination(dest.trim());
        }

        String sapProduct = sapProductField.getValue();
        if (sapProduct != null && !sapProduct.trim().isEmpty()) {
            criteria.setSapProductNumber(sapProduct.trim());
        }

        criteria.setIncompleteOnly(incompleteOnlyCheckbox.getValue());
        criteria.setApprovedOnly(approvedOnlyCheckbox.getValue());
        criteria.setNotApprovedOnly(notApprovedOnlyCheckbox.getValue());

        return criteria;
    }

    /**
     * Setzt alle Filter zurück
     */
    private void onReset() {
        calloffNumberField.clear();
        orderNumberField.clear();
        customerNumberField.clear();
        destinationField.clear();
        sapProductField.clear();
        incompleteOnlyCheckbox.setValue(true);
        approvedOnlyCheckbox.setValue(false);
        notApprovedOnlyCheckbox.setValue(false);
        maxBarrenField.setValue(6);

        onSearch();

        Notification.show("Filter zurückgesetzt", 2000, Notification.Position.BOTTOM_CENTER);
    }

    // === Action Methods ===

    private void neueBeladung() {
        beladungsNummer++;
        beladungsNrField.setValue("BEL-" + String.format("%05d", beladungsNummer));
        geplanteBarren.clear();
        geladeneBarren.clear();
        langBarrenModus = false;
        beladungAktiv = true;
        beladungLaeuft = false;
        kranKommandoGesendet = false;

        updateAnzeigen();
        updateLadeflaeche();

        startenBtn.setEnabled(false);
        stoppenBtn.setEnabled(false);
        statusLabel.setText("Neue Beladung - Bitte Barren ermitteln");
        statusLabel.getStyle().set("color", "#2196F3");

        Notification.show("Neue Beladung gestartet: " + beladungsNrField.getValue(),
            3000, Notification.Position.BOTTOM_CENTER)
            .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    /**
     * Ermittelt die Barren für die Beladung basierend auf Konfiguration
     * Berücksichtigt: Vorzugsabruf, Lagerplatz-Art, Gewichtsgrenzen, Terminoptimierung
     */
    private void ermittleBarren() {
        if (!beladungAktiv) {
            Notification.show("Bitte zuerst 'Neue Beladung' klicken",
                3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_WARNING);
            return;
        }

        if (abrufGesperrt) {
            Notification.show("Abrufe sind gesperrt! Bitte entsperren.",
                3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_WARNING);
            return;
        }

        geplanteBarren.clear();
        geladeneBarren.clear();

        // Konfigurations-Parameter auslesen
        int maxAnzahl = maxBarrenField.getValue() != null ? maxBarrenField.getValue() : 6;
        int maxGewicht = maxGewichtField.getValue() != null ? maxGewichtField.getValue() : 64000;
        int minBreite = minBreiteField.getValue() != null ? minBreiteField.getValue() : 0;
        int maxBreite = maxBreiteField.getValue() != null ? maxBreiteField.getValue() : 9999;
        String lagerplatzArt = lagerplatzArtCombo.getValue();
        String vorzugsabruf = vorzugsabrufField.getValue();

        // Vorzugsabruf zuerst prüfen
        CalloffDTO priorityCalloff = null;
        if (vorzugsabruf != null && !vorzugsabruf.trim().isEmpty()) {
            try {
                var optionalCalloff = calloffService.findByCalloffNumber(vorzugsabruf.trim());
                if (optionalCalloff.isPresent() && optionalCalloff.get().getRemainingAmount() > 0) {
                    priorityCalloff = optionalCalloff.get();
                    log.info("Vorzugsabruf gefunden: {} mit {} offenen Barren",
                        priorityCalloff.getCalloffNumber(), priorityCalloff.getRemainingAmount());
                    selectedCalloff = priorityCalloff;
                    calloffGrid.select(priorityCalloff);
                } else {
                    Notification.show("Vorzugsabruf nicht gefunden oder bereits vollständig: " + vorzugsabruf,
                        4000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_WARNING);
                }
            } catch (Exception e) {
                log.warn("Fehler beim Suchen des Vorzugsabrufs: {}", e.getMessage());
            }
        }

        // Wenn kein Vorzugsabruf, verwende ausgewählten Calloff oder ältesten
        if (priorityCalloff == null && selectedCalloff == null) {
            // Ältesten genehmigten Abruf finden (Terminoptimierung)
            if (terminoptimierungCheckbox.getValue()) {
                try {
                    var criteria = new CalloffSearchCriteria();
                    criteria.setIncompleteOnly(true);
                    criteria.setApprovedOnly(true);
                    var calloffs = calloffService.searchByCriteria(criteria);
                    if (!calloffs.isEmpty()) {
                        selectedCalloff = calloffs.get(0);  // Ältester (nach Liefertermin sortiert)
                        log.info("Automatisch ältesten Abruf ausgewählt: {}", selectedCalloff.getCalloffNumber());
                    }
                } catch (Exception e) {
                    log.warn("Fehler bei der automatischen Abruf-Auswahl: {}", e.getMessage());
                }
            }
        }

        // Anzahl bestimmen
        int anzahl = maxAnzahl;
        if (selectedCalloff != null) {
            anzahl = Math.min(anzahl, selectedCalloff.getRemainingAmount());
            log.info("Ermittle {} Barren für Calloff {}", anzahl, selectedCalloff.getCalloffNumber());
        }

        // Barrentyp basierend auf Lagerplatz-Art bestimmen
        if ("lang".equals(lagerplatzArt)) {
            langBarrenModus = true;
            anzahl = Math.min(anzahl, 4);  // Max 4 Langbarren auf Trailer
        } else if ("kurz".equals(lagerplatzArt)) {
            langBarrenModus = false;
        } else {
            // gemischt: zufällig
            langBarrenModus = anzahl <= 4 && Math.random() > 0.5;
        }

        // Barren ermitteln mit Gewichts- und Breitenbeschränkung
        int aktuellesGewicht = 0;
        AtomicInteger counter = new AtomicInteger(1);

        for (int i = 0; i < anzahl && aktuellesGewicht < maxGewicht; i++) {
            IngotDTO barren = new IngotDTO();
            barren.setId((long) (i + 1));
            barren.setIngotNo("BAR-" + String.format("%03d", counter.getAndIncrement()));

            // Gewicht und Maße basierend auf Modus
            int barrenGewicht = langBarrenModus ? 12000 : 8500;
            int barrenBreite = langBarrenModus ? 600 : 450;

            // Prüfen ob Barren in Grenzen passt
            if (barrenBreite >= minBreite && barrenBreite <= maxBreite &&
                aktuellesGewicht + barrenGewicht <= maxGewicht) {

                barren.setWeight(barrenGewicht);
                barren.setLength(langBarrenModus ? 4500 : 2500);
                barren.setWidth(barrenBreite);
                barren.setStockyardNo("L" + String.format("%02d", (i % 5) + 1));

                geplanteBarren.add(barren);
                aktuellesGewicht += barrenGewicht;
            }
        }

        updateAnzeigen();
        updateLadeflaeche();

        startenBtn.setEnabled(!geplanteBarren.isEmpty());

        String modus = langBarrenModus ? "Lang-Barren" : "Kurz-Barren";
        String message = String.format("%d %s ermittelt (Gewicht: %d kg)",
            geplanteBarren.size(), modus, aktuellesGewicht);
        statusLabel.setText(message + " - bereit zum Laden");
        statusLabel.getStyle().set("color", "#4CAF50");

        Notification.show(message, 3000, Notification.Position.BOTTOM_CENTER)
            .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    /**
     * Startet die Beladungs-Simulation mit Kran-Animation
     * Barren werden nacheinander vom Lager zum Trailer transportiert
     * Die Kran-Bewegung ist im Lager-Tab sichtbar
     */
    private void starteBeladung() {
        if (geplanteBarren.isEmpty()) {
            Notification.show("Keine Barren zum Laden!",
                3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_WARNING);
            return;
        }

        // Prüfen ob Kran/Simulator verfügbar ist
        boolean craneAvailable = plcService.isConnected() || plcService.isSimulatorMode();
        if (!craneAvailable) {
            Notification.show("Kran nicht verfügbar! Bitte SPS oder Simulator aktivieren.",
                4000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_WARNING);
            // Fallback: normale Animation ohne Kran
            starteBeladungOhneKran();
            return;
        }

        beladungLaeuft = true;
        kranKommandoGesendet = false;  // Reset
        startenBtn.setEnabled(false);
        stoppenBtn.setEnabled(true);
        statusLabel.setText("Beladung läuft - Kran aktiv...");
        statusLabel.getStyle().set("color", "#FF9800");

        UI ui = getUI().orElse(null);
        if (ui == null) return;

        String modeText = plcService.isSimulatorMode() ? "(Simulator)" : "(SPS)";
        Notification.show("Beladung gestartet " + modeText + " - Kran-Animation im Lager-Tab sichtbar!",
            4000, Notification.Position.BOTTOM_CENTER)
            .addThemeVariants(NotificationVariant.LUMO_PRIMARY);

        // Erstes Kommando sofort senden
        if (!geplanteBarren.isEmpty()) {
            IngotDTO ersterBarren = geplanteBarren.get(0);
            sendeCranKommando(ersterBarren);
            kranKommandoGesendet = true;
            statusLabel.setText("Kran holt " + ersterBarren.getIngotNo() + " vom Lager...");
            log.info("Erstes Kran-Kommando gesendet für Barren {}", ersterBarren.getIngotNo());
        }

        // Beladung mit Timer - wartet auf Kran-Job-Completion
        beladungsTask = scheduler.scheduleAtFixedRate(() -> {
            if (!beladungLaeuft) {
                return;
            }

            ui.access(() -> {
                var plcStatus = plcService.getCurrentStatus();
                JobState jobState = plcStatus != null ? plcStatus.getJobState() : null;

                log.debug("Kran-Status: jobState={}, kranKommandoGesendet={}, geplanteBarren={}",
                    jobState, kranKommandoGesendet, geplanteBarren.size());

                if (jobState == JobState.IDLE && kranKommandoGesendet) {
                    // Job wurde abgeschlossen (war DROPPED und ist jetzt wieder IDLE)
                    // Barren als geladen markieren
                    if (!geplanteBarren.isEmpty()) {
                        IngotDTO barren = geplanteBarren.remove(0);
                        geladeneBarren.add(barren);
                        kranKommandoGesendet = false;

                        statusLabel.setText(barren.getIngotNo() + " auf Trailer abgelegt!");
                        log.info("Barren {} geladen ({}/{})",
                            barren.getIngotNo(),
                            geladeneBarren.size(),
                            geladeneBarren.size() + geplanteBarren.size());

                        updateLadeflaeche();
                        updateAnzeigen();
                        broadcastStatus();  // LagerView aktualisieren

                        // Nächsten Barren laden wenn vorhanden
                        if (!geplanteBarren.isEmpty()) {
                            IngotDTO naechsterBarren = geplanteBarren.get(0);
                            sendeCranKommando(naechsterBarren);
                            kranKommandoGesendet = true;
                            statusLabel.setText("Kran holt " + naechsterBarren.getIngotNo() + " vom Lager...");
                            log.info("Nächstes Kran-Kommando gesendet für Barren {}", naechsterBarren.getIngotNo());
                        } else {
                            // Alle Barren geladen
                            beladungFertig();
                        }
                    }
                } else if (jobState == JobState.STARTED || jobState == JobState.LOADED || jobState == JobState.DROPPED) {
                    // Kran ist beschäftigt - Status anzeigen
                    String phase = "arbeitet...";
                    if (plcStatus != null && plcStatus.getWorkPhase() != null) {
                        phase = plcStatus.getWorkPhase().getDisplayName();
                    }
                    statusLabel.setText("Kran: " + phase);
                }
            });
        }, 1000, 800, TimeUnit.MILLISECONDS);
    }

    /**
     * Sendet ein Kran-Kommando zum Abholen eines Barrens
     */
    private void sendeCranKommando(IngotDTO barren) {
        try {
            // Pickup-Position aus Stockyard-Nummer ermitteln (simuliert)
            int pickupX = getPickupX(barren);
            int pickupY = getPickupY(barren);
            int pickupZ = 2000;  // Standard-Greifhöhe

            log.info("Kran-Kommando wird gesendet: Pickup=({},{},{}), Release=({},{},{})",
                pickupX, pickupY, pickupZ, TRAILER_X, TRAILER_Y, TRAILER_Z);

            // PlcCommand erstellen
            PlcCommand cmd = PlcCommand.builder()
                .pickupPosition(pickupX, pickupY, pickupZ)
                .releasePosition(TRAILER_X, TRAILER_Y, TRAILER_Z)
                .dimensions(
                    barren.getLength() != null ? barren.getLength() : 1800,
                    barren.getWidth() != null ? barren.getWidth() : 500,
                    200  // Standard-Dicke
                )
                .weight(barren.getWeight() != null ? barren.getWeight() : 8500)
                .longIngot(langBarrenModus)
                .rotate(false)
                .build();

            plcService.sendCommand(cmd);
            log.info("Kran-Kommando gesendet für {}: {}", barren.getIngotNo(), cmd);

        } catch (Exception e) {
            log.error("Fehler beim Senden des Kran-Kommandos", e);
            Notification.show("Kran-Fehler: " + e.getMessage(),
                3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    /**
     * Ermittelt die X-Position des Barrens im Lager (in mm)
     * Lagerplätze sind etwa bei X = 6000 bis 48000 mm
     */
    private int getPickupX(IngotDTO barren) {
        String yardNo = barren.getStockyardNo();
        if (yardNo != null && yardNo.startsWith("L")) {
            try {
                int num = Integer.parseInt(yardNo.substring(1));
                // Verteile Barren im Lager (Spalten 3-15)
                int gridX = 3 + (num * 2);  // L01=5, L02=7, L03=9, L04=11, L05=13
                return settingsService.gridToMmX(gridX);
            } catch (NumberFormatException e) {
                log.debug("Konnte Stockyard-Nr nicht parsen: {}", yardNo);
            }
        }
        // Default: Mitte des Lagers (Spalte 9 = ~27000mm)
        return 27000;
    }

    /**
     * Ermittelt die Y-Position des Barrens im Lager (in mm)
     * Lagerplätze sind etwa bei Y = 6000 bis 24000 mm
     */
    private int getPickupY(IngotDTO barren) {
        String yardNo = barren.getStockyardNo();
        if (yardNo != null && yardNo.startsWith("L")) {
            try {
                int num = Integer.parseInt(yardNo.substring(1));
                // Verteile Barren in Y-Richtung (Reihen 3-7)
                int gridY = 3 + (num % 5);  // Reihe 3-7
                return settingsService.gridToMmY(gridY);
            } catch (NumberFormatException e) {
                log.debug("Konnte Stockyard-Nr nicht parsen: {}", yardNo);
            }
        }
        // Default: Mitte (Reihe 5 = ~15000mm)
        return 15000;
    }

    /**
     * Fallback: Beladung ohne Kran-Animation (wenn Kran nicht verfügbar)
     */
    private void starteBeladungOhneKran() {
        beladungLaeuft = true;
        startenBtn.setEnabled(false);
        stoppenBtn.setEnabled(true);
        statusLabel.setText("Beladung läuft (ohne Kran)...");
        statusLabel.getStyle().set("color", "#FF9800");

        UI ui = getUI().orElse(null);
        if (ui == null) return;

        beladungsTask = scheduler.scheduleAtFixedRate(() -> {
            if (!beladungLaeuft) {
                return;
            }

            ui.access(() -> {
                if (!geplanteBarren.isEmpty()) {
                    IngotDTO barren = geplanteBarren.remove(0);
                    geladeneBarren.add(barren);

                    statusLabel.setText("Lade " + barren.getIngotNo() + "...");
                    log.info("Barren {} geladen (ohne Kran)", barren.getIngotNo());

                    updateLadeflaeche();
                    updateAnzeigen();
                    broadcastStatus();  // LagerView aktualisieren

                    if (geplanteBarren.isEmpty()) {
                        beladungFertig();
                    }
                } else {
                    beladungFertig();
                }
            });
        }, 500, 1000, TimeUnit.MILLISECONDS);
    }

    private void stoppeBeladung() {
        beladungLaeuft = false;
        kranKommandoGesendet = false;
        if (beladungsTask != null) {
            beladungsTask.cancel(false);
            beladungsTask = null;
        }

        // Kran-Kommando abbrechen wenn aktiv
        try {
            if (plcService.isConnected() || plcService.isSimulatorMode()) {
                plcService.abort();
                log.info("Kran-Kommando abgebrochen");
            }
        } catch (Exception e) {
            log.debug("Kein aktives Kran-Kommando zum Abbrechen");
        }

        stoppenBtn.setEnabled(false);
        startenBtn.setEnabled(!geplanteBarren.isEmpty());
        updateLadeflaeche();

        if (!geplanteBarren.isEmpty()) {
            statusLabel.setText("Beladung gestoppt - " + geplanteBarren.size() + " Barren noch offen");
            statusLabel.getStyle().set("color", "#FF5722");
        }
    }

    private void beladungFertig() {
        beladungLaeuft = false;
        kranKommandoGesendet = false;
        if (beladungsTask != null) {
            beladungsTask.cancel(false);
            beladungsTask = null;
        }

        stoppenBtn.setEnabled(false);
        startenBtn.setEnabled(false);
        updateLadeflaeche();
        broadcastStatus();  // LagerView aktualisieren

        statusLabel.setText("Beladung fertig! Alle " + geladeneBarren.size() + " Barren geladen.");
        statusLabel.getStyle().set("color", "#4CAF50");
        fortschrittBar.setValue(1.0);

        Notification.show("Beladung abgeschlossen! " + geladeneBarren.size() + " Barren auf dem Trailer.",
            5000, Notification.Position.BOTTOM_CENTER)
            .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private void abbrechenBeladung() {
        stoppeBeladung();
        beladungAktiv = false;
        geplanteBarren.clear();
        geladeneBarren.clear();
        beladungsNrField.clear();

        updateAnzeigen();
        updateLadeflaeche();
        broadcastStatus();  // LagerView aktualisieren

        startenBtn.setEnabled(false);
        stoppenBtn.setEnabled(false);
        statusLabel.setText("Bereit");
        statusLabel.getStyle().set("color", "#4CAF50");
        fortschrittBar.setValue(0);

        Notification.show("Beladung abgebrochen",
            3000, Notification.Position.BOTTOM_CENTER);
    }

    private void updateAnzeigen() {
        int gesamt = geladeneBarren.size() + geplanteBarren.size();
        barrenAnzahlLabel.setText(geladeneBarren.size() + " / " + gesamt);

        // Gewicht berechnen (vorne/hinten Verteilung simuliert)
        int gesamtGewicht = geladeneBarren.stream()
            .mapToInt(b -> b.getWeight() != null ? b.getWeight() : 0)
            .sum();

        // Gewichtsverteilung berechnen: vorne sind die zuerst geladenen Barren
        int gewichtVorne = 0;
        int gewichtHinten = 0;
        for (int i = 0; i < geladeneBarren.size(); i++) {
            IngotDTO barren = geladeneBarren.get(i);
            int weight = barren.getWeight() != null ? barren.getWeight() : 0;
            if (i < geladeneBarren.size() / 2 || geladeneBarren.size() == 1) {
                gewichtVorne += weight;
            } else {
                gewichtHinten += weight;
            }
        }

        gewichtVorneLabel.setText(String.valueOf(gewichtVorne));
        gewichtHintenLabel.setText(String.valueOf(gewichtHinten));
        gesamtGewichtLabel.setText(String.valueOf(gesamtGewicht));

        if (gesamt > 0) {
            fortschrittBar.setValue((double) geladeneBarren.size() / gesamt);
        } else {
            fortschrittBar.setValue(0);
        }

        // Warnung bei Übergewicht
        int maxGewicht = maxGewichtField.getValue() != null ? maxGewichtField.getValue() : 64000;
        if (gesamtGewicht > maxGewicht) {
            gesamtGewichtLabel.getStyle().set("color", "red");
        } else {
            gesamtGewichtLabel.getStyle().set("color", "inherit");
        }
    }

    private void alsGeliefertMarkieren() {
        if (selectedCalloff != null && !geladeneBarren.isEmpty()) {
            calloffService.addDelivered(selectedCalloff.getId(), geladeneBarren.size());
            Notification.show("Calloff " + selectedCalloff.getCalloffNumber() +
                " - " + geladeneBarren.size() + " Barren geliefert",
                3000, Notification.Position.BOTTOM_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            onSearch();
        } else {
            Notification.show("Bitte Abruf auswählen und Barren laden",
                3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_WARNING);
        }
    }

    private void buchen() {
        if (geladeneBarren.isEmpty()) {
            Notification.show("Keine Barren zum Buchen",
                3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_WARNING);
            return;
        }

        Notification.show(String.format("Beladung %s mit %d Barren gebucht!",
            beladungsNrField.getValue(), geladeneBarren.size()),
            3000, Notification.Position.BOTTOM_CENTER)
            .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

        abbrechenBeladung();
    }
}
