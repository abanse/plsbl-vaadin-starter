package com.hydro.plsbl.ui.view;

import com.hydro.plsbl.dto.CalloffDTO;
import com.hydro.plsbl.dto.CalloffSearchCriteria;
import com.hydro.plsbl.dto.IngotDTO;
import com.hydro.plsbl.dto.TransportOrderDTO;
import com.hydro.plsbl.plc.PlcService;
import com.hydro.plsbl.plc.dto.PlcCommand;
import com.hydro.plsbl.plc.dto.JobState;
import com.hydro.plsbl.dto.DeliveryNoteDTO;
import com.hydro.plsbl.entity.transdata.Shipment;
import com.hydro.plsbl.service.BeladungBroadcaster;
import com.hydro.plsbl.service.BeladungProcessorService;
import com.hydro.plsbl.service.BeladungStateService;
import com.hydro.plsbl.service.CalloffService;
import com.hydro.plsbl.service.DataBroadcaster;
import com.hydro.plsbl.service.IngotService;
import com.hydro.plsbl.service.LieferscheinPdfService;
import com.hydro.plsbl.service.SettingsService;
import com.hydro.plsbl.service.ShipmentService;
import com.hydro.plsbl.service.StockyardService;
import com.hydro.plsbl.service.TransportOrderService;
import com.hydro.plsbl.ui.dialog.LieferungBestaetigenDialog;
import com.hydro.plsbl.ui.MainLayout;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
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
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Beladung-Ansicht - LKW Beladung und Versand
 * Layout entspricht der Original-Applikation
 */
@Route(value = "beladung", layout = MainLayout.class)
@PageTitle("Beladung | PLSBL")
public class BeladungView extends VerticalLayout {

    private static final Logger log = LoggerFactory.getLogger(BeladungView.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    // Farben für Barren-Status: GRAU = geplant/wartend, GELB = geladen
    private static final String FARBE_GEPLANT = "linear-gradient(180deg, #BDBDBD 0%, #9E9E9E 100%)";  // Grau
    private static final String FARBE_GELADEN = "linear-gradient(180deg, #FFD54F 0%, #FFC107 100%)";  // Gelb
    private static final String BORDER_GEPLANT = "#757575";  // Dunkelgrau
    private static final String BORDER_GELADEN = "#FF8F00";  // Orange

    private final IngotService ingotService;
    private final StockyardService stockyardService;
    private final TransportOrderService transportOrderService;
    private final CalloffService calloffService;
    private final PlcService plcService;
    private final SettingsService settingsService;
    private final BeladungStateService stateService;
    private final BeladungBroadcaster broadcaster;
    private final DataBroadcaster dataBroadcaster;
    private final ShipmentService shipmentService;
    private final LieferscheinPdfService pdfService;
    private final BeladungProcessorService processorService;
    private com.vaadin.flow.shared.Registration dataBroadcasterRegistration;
    private com.vaadin.flow.shared.Registration beladungBroadcasterRegistration;

    // Trailer-Position (in mm)
    // Die Trailer-Beladungsposition liegt UNTERHALB des Lager-Grids
    // X: zwischen Position 04-07 (ca. 31000-47000 mm), Y: UNTER Y=1 (6270mm)
    // Diese Koordinaten werden in LagerGrid.updateCranePixelPosition() speziell behandelt
    private static final int TRAILER_X = 40000;   // Mitte der Beladungsfläche (zwischen 04-07)
    private static final int TRAILER_Y = 2000;    // UNTER dem Grid (Y=1 ist bei 6270mm)
    private static final int TRAILER_Z = 2000;

    // Filter-Felder (Zeile 1)
    private ComboBox<String> lieferortCombo;
    private IntegerField maxGewichtField;
    private IntegerField maxBarrenField;

    // Filter-Felder (Zeile 2)
    private ComboBox<String> lagerplatzArtCombo;
    private ComboBox<String> barrenartCombo;
    private IntegerField minBreiteField;
    private IntegerField maxBreiteField;

    // Filter-Felder (Zeile 3)
    private TextField vorzugsabrufField;
    private Checkbox terminoptimierungCheckbox;
    private boolean abrufGesperrt = false;

    // Visualisierung
    private Div visualisierungsBereich;
    private Div ladeflaeche;

    // Beladungs-Steuerung
    private TextField beladungsNrField;
    private TextField gewichtVorneField;
    private TextField gewichtHintenField;
    private TextField gesamtGewichtField;

    // Buttons
    private Button neueBeladungBtn;
    private Button startenBtn;
    private Button abbrechenBtn;
    private Button pauseBtn;

    // Transport-Aufträge Grid
    private Grid<TransportAuftragZeile> transportGrid;

    // Barren-Listen
    private List<IngotDTO> geplanteBarren = new ArrayList<>();
    private List<IngotDTO> geladeneBarren = new ArrayList<>();

    // Status
    private boolean beladungAktiv = false;
    private boolean beladungLaeuft = false;
    private int beladungsNummer = 0;
    private boolean langBarrenModus = false;
    private boolean kranKommandoGesendet = false;
    private CalloffDTO selectedCalloff;

    // Scheduler
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> beladungsTask;

    public BeladungView(IngotService ingotService,
                        StockyardService stockyardService,
                        TransportOrderService transportOrderService,
                        CalloffService calloffService,
                        PlcService plcService,
                        SettingsService settingsService,
                        BeladungStateService stateService,
                        BeladungBroadcaster broadcaster,
                        DataBroadcaster dataBroadcaster,
                        ShipmentService shipmentService,
                        LieferscheinPdfService pdfService,
                        BeladungProcessorService processorService) {
        this.ingotService = ingotService;
        this.stockyardService = stockyardService;
        this.transportOrderService = transportOrderService;
        this.calloffService = calloffService;
        this.plcService = plcService;
        this.settingsService = settingsService;
        this.stateService = stateService;
        this.broadcaster = broadcaster;
        this.dataBroadcaster = dataBroadcaster;
        this.shipmentService = shipmentService;
        this.pdfService = pdfService;
        this.processorService = processorService;

        setSizeFull();
        setPadding(true);
        setSpacing(false);

        // Layout aufbauen wie Original
        add(createFilterSection());
        add(createVisualisierungsBereich());
        add(createSteuerungsBereich());
        add(createTransportAuftraegeSection());
        add(createFooterButtons());

        loadData();
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "BeladungView-Scheduler");
            t.setDaemon(true);
            return t;
        });

        dataBroadcasterRegistration = dataBroadcaster.register(event -> {
            if (event.getType() == DataBroadcaster.DataEventType.CALLOFF_CHANGED ||
                event.getType() == DataBroadcaster.DataEventType.REFRESH_ALL) {
                getUI().ifPresent(ui -> {
                    try {
                        if (ui.isAttached()) {
                            ui.access(this::loadData);
                        }
                    } catch (Exception e) {
                        log.debug("Could not access UI: {}", e.getMessage());
                    }
                });
            }
        });

        // Für BELADUNG_ENDED Events registrieren (Lieferschein-Dialog)
        beladungBroadcasterRegistration = broadcaster.register(event -> {
            if (event.getType() == BeladungBroadcaster.BeladungEventType.BELADUNG_ENDED && event.hasShipment()) {
                getUI().ifPresent(ui -> {
                    try {
                        if (ui.isAttached()) {
                            ui.access(() -> {
                                log.info(">>> BELADUNG_ENDED Event empfangen - Zeige Lieferschein: {}", event.getShipmentNumber());

                                // Lokalen Status zurücksetzen
                                beladungLaeuft = false;
                                beladungAktiv = false;
                                kranKommandoGesendet = false;
                                if (beladungsTask != null) {
                                    beladungsTask.cancel(false);
                                    beladungsTask = null;
                                }

                                // Barren-Listen leeren (Vorgang abgeschlossen)
                                geplanteBarren.clear();
                                geladeneBarren.clear();
                                selectedCalloff = null;

                                // StateService zurücksetzen
                                stateService.reset();

                                // UI aktualisieren
                                updateLadeflaeche();
                                updateAnzeigen();
                                updateTransportGrid();
                                beladungsNrField.clear();
                                startenBtn.setEnabled(false);
                                pauseBtn.setEnabled(false);

                                log.info(">>> Beladung abgeschlossen - View zurückgesetzt");

                                // Lieferschein-Dialog öffnen
                                openLieferscheinDialogById(event.getShipmentId(), event.getShipmentNumber());
                            });
                        }
                    } catch (Exception e) {
                        log.error("Fehler bei BELADUNG_ENDED Event: {}", e.getMessage());
                    }
                });
            }
        });

        restoreStateFromService();
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        log.info("=== BELADUNG VIEW DETACH ===");
        log.info("  beladungLaeuft={}, kranKommandoGesendet={}", beladungLaeuft, kranKommandoGesendet);
        log.info("  geplanteBarren={}, geladeneBarren={}", geplanteBarren.size(), geladeneBarren.size());

        // Zuerst State speichern
        saveStateToService();

        // Dann Broadcaster abmelden
        if (dataBroadcasterRegistration != null) {
            dataBroadcasterRegistration.remove();
            dataBroadcasterRegistration = null;
        }
        if (beladungBroadcasterRegistration != null) {
            beladungBroadcasterRegistration.remove();
            beladungBroadcasterRegistration = null;
        }

        // Dann Tasks stoppen
        if (beladungsTask != null) {
            log.info("  >>> STOPPE BELADUNGS-SCHEDULER");
            beladungsTask.cancel(false);
            beladungsTask = null;
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }

        super.onDetach(detachEvent);
    }

    /**
     * Erstellt den Filter-Bereich (3 Zeilen wie im Original)
     */
    private VerticalLayout createFilterSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(false);
        section.getStyle()
            .set("background-color", "#f5f5f5")
            .set("padding", "10px 15px")
            .set("border-bottom", "1px solid #ddd");

        // Zeile 1: Lieferort, max. Gewicht, max. Anzahl Barren
        HorizontalLayout row1 = new HorizontalLayout();
        row1.setAlignItems(FlexComponent.Alignment.BASELINE);
        row1.setSpacing(true);
        row1.getStyle().set("gap", "20px");

        lieferortCombo = new ComboBox<>("Lieferort:");
        lieferortCombo.setItems("NF2", "NF3", "GIESS", "SAEGE", "LKW", "EXTERN");
        lieferortCombo.setValue("NF2");
        lieferortCombo.setWidth("100px");

        maxGewichtField = new IntegerField("max. Gewicht [kg]:");
        maxGewichtField.setValue(64000);
        maxGewichtField.setWidth("100px");

        maxBarrenField = new IntegerField("max. Anzahl Barren:");
        maxBarrenField.setValue(6);
        maxBarrenField.setWidth("80px");

        row1.add(lieferortCombo, maxGewichtField, maxBarrenField);

        // Zeile 2: Lagerplatz-Art, Barrenart, min/max Breite
        HorizontalLayout row2 = new HorizontalLayout();
        row2.setAlignItems(FlexComponent.Alignment.BASELINE);
        row2.setSpacing(true);
        row2.getStyle().set("gap", "20px");

        lagerplatzArtCombo = new ComboBox<>("Lagerplatz-Art:");
        lagerplatzArtCombo.setItems("gemischt", "kurz", "lang");
        lagerplatzArtCombo.setValue("gemischt");
        lagerplatzArtCombo.setWidth("100px");

        barrenartCombo = new ComboBox<>("Barrenart:");
        barrenartCombo.setItems("gemischt", "Walzbarren", "Pressbolzen");
        barrenartCombo.setValue("gemischt");
        barrenartCombo.setWidth("110px");

        minBreiteField = new IntegerField("min. Breite [mm]:");
        minBreiteField.setValue(0);
        minBreiteField.setWidth("80px");

        maxBreiteField = new IntegerField("max. Breite [mm]:");
        maxBreiteField.setValue(9999);
        maxBreiteField.setWidth("80px");

        row2.add(lagerplatzArtCombo, barrenartCombo, minBreiteField, maxBreiteField);

        // Zeile 3: Vorzugsabruf, Abrufe sperren, Terminoptimierung
        HorizontalLayout row3 = new HorizontalLayout();
        row3.setAlignItems(FlexComponent.Alignment.BASELINE);
        row3.setSpacing(true);
        row3.getStyle().set("gap", "20px");

        vorzugsabrufField = new TextField("Vorzugsabruf:");
        vorzugsabrufField.setWidth("120px");
        vorzugsabrufField.setClearButtonVisible(true);

        Button abrufeSperrenBtn = new Button("Abrufe sperren", e -> toggleAbrufSperre((Button) e.getSource()));

        terminoptimierungCheckbox = new Checkbox("Terminoptimierung?");
        terminoptimierungCheckbox.setValue(true);

        row3.add(vorzugsabrufField, abrufeSperrenBtn, terminoptimierungCheckbox);

        section.add(row1, row2, row3);
        return section;
    }

    /**
     * Erstellt den grauen Visualisierungsbereich (LKW/Trailer)
     */
    private Div createVisualisierungsBereich() {
        visualisierungsBereich = new Div();
        visualisierungsBereich.getStyle()
            .set("background-color", "#d0d0d0")
            .set("border", "1px solid #999")
            .set("min-height", "180px")
            .set("margin", "10px 0")
            .set("display", "flex")
            .set("align-items", "center")
            .set("justify-content", "center")
            .set("flex-grow", "1")
            .set("width", "100%");

        // Trailer-Visualisierung - größer
        Div trailerContainer = new Div();
        trailerContainer.getStyle()
            .set("display", "flex")
            .set("flex-direction", "row")
            .set("align-items", "flex-end");

        // Zugmaschine - größer
        Div kabine = new Div();
        kabine.getStyle()
            .set("width", "80px")
            .set("height", "90px")
            .set("background-color", "#FF9800")
            .set("border-radius", "8px 8px 0 0")
            .set("border", "3px solid #E65100")
            .set("position", "relative")
            .set("margin-right", "-3px");

        Div fenster = new Div();
        fenster.getStyle()
            .set("position", "absolute")
            .set("top", "10px")
            .set("left", "10px")
            .set("width", "55px")
            .set("height", "28px")
            .set("background-color", "#81D4FA")
            .set("border-radius", "4px")
            .set("border", "2px solid #0288D1");
        kabine.add(fenster);

        Div radKabine = createWheel(22);
        radKabine.getStyle()
            .set("position", "absolute")
            .set("bottom", "-13px")
            .set("left", "25px");
        kabine.add(radKabine);

        // Ladefläche - viel breiter
        ladeflaeche = new Div();
        ladeflaeche.getStyle()
            .set("width", "600px")
            .set("height", "60px")
            .set("background-color", "#8D6E63")
            .set("border", "3px solid #5D4037")
            .set("border-radius", "0 4px 4px 0")
            .set("position", "relative")
            .set("display", "flex")
            .set("flex-direction", "row")
            .set("align-items", "flex-end")
            .set("justify-content", "flex-start")
            .set("padding", "4px")
            .set("gap", "3px");

        // Räder - größer
        Div rad1 = createWheel(20);
        rad1.getStyle().set("position", "absolute").set("bottom", "-12px").set("left", "80px");
        ladeflaeche.add(rad1);

        Div rad2 = createWheel(20);
        rad2.getStyle().set("position", "absolute").set("bottom", "-12px").set("left", "140px");
        ladeflaeche.add(rad2);

        Div rad3 = createWheel(20);
        rad3.getStyle().set("position", "absolute").set("bottom", "-12px").set("right", "80px");
        ladeflaeche.add(rad3);

        Div rad4 = createWheel(20);
        rad4.getStyle().set("position", "absolute").set("bottom", "-12px").set("right", "140px");
        ladeflaeche.add(rad4);

        trailerContainer.add(kabine, ladeflaeche);

        // Boden - breiter
        Div boden = new Div();
        boden.getStyle()
            .set("width", "720px")
            .set("height", "8px")
            .set("background-color", "#424242")
            .set("margin-top", "15px");

        Div wrapper = new Div();
        wrapper.getStyle()
            .set("display", "flex")
            .set("flex-direction", "column")
            .set("align-items", "center")
            .set("margin-top", "180px");  // Trailer tiefer setzen für Barren-Stapel oben drauf
        wrapper.add(trailerContainer, boden);

        visualisierungsBereich.add(wrapper);
        return visualisierungsBereich;
    }

    private Div createWheel(int size) {
        Div wheel = new Div();
        wheel.addClassName("wheel");
        wheel.getStyle()
            .set("width", size + "px")
            .set("height", size + "px")
            .set("background-color", "#212121")
            .set("border-radius", "50%")
            .set("border", "2px solid #000");
        return wheel;
    }

    /**
     * Erstellt den Steuerungsbereich (Beladungs-Nr links, Gewichte in der Mitte-rechts)
     */
    private HorizontalLayout createSteuerungsBereich() {
        HorizontalLayout steuerung = new HorizontalLayout();
        steuerung.setWidthFull();
        steuerung.setAlignItems(FlexComponent.Alignment.END);
        steuerung.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        steuerung.getStyle()
            .set("padding", "10px 20px")
            .set("gap", "40px");

        // Links: Beladungs-Nr und Buttons
        VerticalLayout linksBereich = new VerticalLayout();
        linksBereich.setPadding(false);
        linksBereich.setSpacing(true);
        linksBereich.setWidth("auto");

        HorizontalLayout beladungsNrRow = new HorizontalLayout();
        beladungsNrRow.setAlignItems(FlexComponent.Alignment.BASELINE);
        Span beladungsNrLabel = new Span("Beladungs-Nr.:");
        beladungsNrField = new TextField();
        beladungsNrField.setWidth("150px");
        beladungsNrField.setReadOnly(true);
        beladungsNrRow.add(beladungsNrLabel, beladungsNrField);

        HorizontalLayout buttonsRow = new HorizontalLayout();
        buttonsRow.setSpacing(true);

        neueBeladungBtn = new Button("neue Beladung", VaadinIcon.PLUS.create());
        neueBeladungBtn.addClickListener(e -> neueBeladung());

        startenBtn = new Button("starten", VaadinIcon.PLAY.create());
        startenBtn.addClickListener(e -> starteBeladung());
        startenBtn.setEnabled(false);

        abbrechenBtn = new Button("abbrechen", VaadinIcon.CLOSE.create());
        abbrechenBtn.addClickListener(e -> abbrechenBeladung());

        pauseBtn = new Button("Pause", VaadinIcon.PAUSE.create());
        pauseBtn.addClickListener(e -> pauseBeladung());
        pauseBtn.setEnabled(false);

        buttonsRow.add(neueBeladungBtn, startenBtn, abbrechenBtn, pauseBtn);

        linksBereich.add(beladungsNrRow, buttonsRow);

        // Mitte-Rechts: Gewichts-Anzeigen
        VerticalLayout gewichtBereich = new VerticalLayout();
        gewichtBereich.setPadding(false);
        gewichtBereich.setSpacing(false);
        gewichtBereich.setWidth("auto");
        gewichtBereich.getStyle().set("gap", "5px");

        HorizontalLayout gewichtVorneRow = createGewichtRow("Gewicht vorne [kg]:");
        gewichtVorneField = (TextField) gewichtVorneRow.getComponentAt(1);
        gewichtVorneField.setValue("0");

        HorizontalLayout gewichtHintenRow = createGewichtRow("Gewicht hinten [kg]:");
        gewichtHintenField = (TextField) gewichtHintenRow.getComponentAt(1);
        gewichtHintenField.setValue("0");

        HorizontalLayout gesamtGewichtRow = createGewichtRow("Gesamtgewicht [kg]:");
        gesamtGewichtField = (TextField) gesamtGewichtRow.getComponentAt(1);
        gesamtGewichtField.setValue("0");

        gewichtBereich.add(gewichtVorneRow, gewichtHintenRow, gesamtGewichtRow);

        steuerung.add(linksBereich, gewichtBereich);
        return steuerung;
    }

    private HorizontalLayout createGewichtRow(String labelText) {
        HorizontalLayout row = new HorizontalLayout();
        row.setAlignItems(FlexComponent.Alignment.BASELINE);
        row.setSpacing(true);

        Span label = new Span(labelText);
        label.setWidth("150px");
        label.getStyle().set("text-align", "right");

        TextField field = new TextField();
        field.setWidth("100px");
        field.setReadOnly(true);
        field.getStyle().set("text-align", "right");

        row.add(label, field);
        return row;
    }

    /**
     * Erstellt die Transport-Aufträge Tabelle
     */
    private VerticalLayout createTransportAuftraegeSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(false);

        H4 title = new H4("Transport-Aufträge");
        title.getStyle().set("margin", "10px 0 5px 0").set("text-align", "center");

        transportGrid = new Grid<>();
        transportGrid.setHeight("200px");
        transportGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);

        transportGrid.addColumn(TransportAuftragZeile::getNr)
            .setHeader("Nr.").setWidth("60px").setFlexGrow(0);
        transportGrid.addColumn(TransportAuftragZeile::getCalloff)
            .setHeader("Calloff").setWidth("100px").setFlexGrow(0);
        transportGrid.addColumn(TransportAuftragZeile::getAuftrag)
            .setHeader("Auftrag").setWidth("100px").setFlexGrow(0);
        transportGrid.addColumn(TransportAuftragZeile::getBarren)
            .setHeader("Barren").setWidth("100px").setFlexGrow(0);
        transportGrid.addColumn(TransportAuftragZeile::getArtikel)
            .setHeader("Artikel").setWidth("100px").setFlexGrow(0);
        transportGrid.addColumn(TransportAuftragZeile::getVon)
            .setHeader("von").setWidth("80px").setFlexGrow(0);
        transportGrid.addColumn(TransportAuftragZeile::getNach)
            .setHeader("nach").setWidth("80px").setFlexGrow(0);
        transportGrid.addColumn(TransportAuftragZeile::getGedruckt)
            .setHeader("gedruckt").setWidth("130px").setFlexGrow(0);
        transportGrid.addColumn(TransportAuftragZeile::getGeliefert)
            .setHeader("geliefert").setWidth("130px").setFlexGrow(1);

        section.add(title, transportGrid);
        return section;
    }

    /**
     * Erstellt die Footer-Buttons
     */
    private HorizontalLayout createFooterButtons() {
        HorizontalLayout footer = new HorizontalLayout();
        footer.setWidthFull();
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        footer.setSpacing(true);
        footer.getStyle()
            .set("padding", "10px 0")
            .set("border-top", "1px solid #ddd")
            .set("margin-top", "10px");

        Button testDatenBtn = new Button("Test-Daten", VaadinIcon.DATABASE.create());
        testDatenBtn.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        testDatenBtn.addClickListener(e -> erstelleTestDaten());

        Button baumBtn = new Button("Baum", VaadinIcon.SITEMAP.create());
        baumBtn.addClickListener(e -> showBaum());

        Button druckenBtn = new Button("drucken", VaadinIcon.PRINT.create());
        druckenBtn.addClickListener(e -> drucken());

        Button alsGeliefertBtn = new Button("als geliefert markieren", VaadinIcon.CHECK.create());
        alsGeliefertBtn.addClickListener(e -> alsGeliefertMarkieren());

        Button loeschenBtn = new Button("löschen", VaadinIcon.TRASH.create());
        loeschenBtn.addClickListener(e -> loeschen());

        footer.add(testDatenBtn, baumBtn, druckenBtn, alsGeliefertBtn, loeschenBtn);
        return footer;
    }

    // === Helper-Klasse für Grid ===
    public static class TransportAuftragZeile {
        private int nr;
        private String calloff;
        private String auftrag;
        private String barren;
        private String artikel;
        private String von;
        private String nach;
        private String gedruckt;
        private String geliefert;

        public TransportAuftragZeile(int nr, String calloff, String auftrag, String barren,
                                     String artikel, String von, String nach,
                                     String gedruckt, String geliefert) {
            this.nr = nr;
            this.calloff = calloff;
            this.auftrag = auftrag;
            this.barren = barren;
            this.artikel = artikel;
            this.von = von;
            this.nach = nach;
            this.gedruckt = gedruckt;
            this.geliefert = geliefert;
        }

        public int getNr() { return nr; }
        public String getCalloff() { return calloff; }
        public String getAuftrag() { return auftrag; }
        public String getBarren() { return barren; }
        public String getArtikel() { return artikel; }
        public String getVon() { return von; }
        public String getNach() { return nach; }
        public String getGedruckt() { return gedruckt; }
        public String getGeliefert() { return geliefert; }
    }

    // === Logik-Methoden ===

    private void loadData() {
        updateTransportGrid();
    }

    private void updateTransportGrid() {
        List<TransportAuftragZeile> zeilen = new ArrayList<>();

        // Geladene Barren als Transport-Aufträge anzeigen
        int nr = 1;
        for (IngotDTO barren : geladeneBarren) {
            zeilen.add(new TransportAuftragZeile(
                nr++,
                selectedCalloff != null ? selectedCalloff.getCalloffNumber() : "-",
                selectedCalloff != null ? selectedCalloff.getOrderNumber() : "-",
                barren.getIngotNo(),
                barren.getProductNo() != null ? barren.getProductNo() : "-",
                barren.getStockyardNo() != null ? barren.getStockyardNo() : "-",
                "Trailer",
                "-",
                LocalDateTime.now().format(DATE_TIME_FORMATTER)
            ));
        }

        // Geplante Barren
        for (IngotDTO barren : geplanteBarren) {
            zeilen.add(new TransportAuftragZeile(
                nr++,
                selectedCalloff != null ? selectedCalloff.getCalloffNumber() : "-",
                selectedCalloff != null ? selectedCalloff.getOrderNumber() : "-",
                barren.getIngotNo(),
                barren.getProductNo() != null ? barren.getProductNo() : "-",
                barren.getStockyardNo() != null ? barren.getStockyardNo() : "-",
                "Trailer",
                "-",
                "-"
            ));
        }

        transportGrid.setItems(zeilen);
    }

    private void toggleAbrufSperre(Button btn) {
        abrufGesperrt = !abrufGesperrt;
        if (abrufGesperrt) {
            btn.setText("Abrufe entsperren");
            btn.addThemeVariants(ButtonVariant.LUMO_ERROR);
        } else {
            btn.setText("Abrufe sperren");
            btn.removeThemeVariants(ButtonVariant.LUMO_ERROR);
        }
    }

    private void neueBeladung() {
        beladungsNummer++;
        beladungsNrField.setValue("BEL-" + String.format("%05d", beladungsNummer));
        geplanteBarren.clear();
        geladeneBarren.clear();
        langBarrenModus = false;
        beladungAktiv = true;
        beladungLaeuft = false;
        kranKommandoGesendet = false;

        // Automatisch Abruf auswählen und Barren ermitteln
        ermittleBarren();

        updateAnzeigen();
        updateLadeflaeche();
        updateTransportGrid();

        startenBtn.setEnabled(!geplanteBarren.isEmpty());
        pauseBtn.setEnabled(false);

        Notification.show("Neue Beladung gestartet: " + beladungsNrField.getValue(),
            3000, Notification.Position.BOTTOM_CENTER)
            .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private void ermittleBarren() {
        if (abrufGesperrt) {
            Notification.show("Abrufe sind gesperrt!", 3000, Notification.Position.MIDDLE);
            return;
        }

        // Automatisch genehmigten Abruf auswählen
        if (selectedCalloff == null) {
            try {
                var criteria = new CalloffSearchCriteria();
                criteria.setIncompleteOnly(true);
                criteria.setApprovedOnly(true);
                var calloffs = calloffService.searchByCriteria(criteria);
                log.info("Gefundene offene Abrufe: {}", calloffs.size());

                if (!calloffs.isEmpty()) {
                    selectedCalloff = calloffs.get(0);
                    log.info("Automatisch Abruf ausgewählt: {} (ProductId={}, Remaining={})",
                        selectedCalloff.getCalloffNumber(),
                        selectedCalloff.getProductId(),
                        selectedCalloff.getRemainingAmount());
                } else {
                    Notification.show("Kein offener/genehmigter Abruf gefunden! Bitte erst Test-Daten erstellen.",
                        5000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_WARNING);
                    return;
                }
            } catch (Exception e) {
                log.error("Fehler bei automatischer Abruf-Auswahl: {}", e.getMessage(), e);
                Notification.show("Fehler: " + e.getMessage(), 5000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
        }

        // Barren laden - immer alle verfügbaren laden, unabhängig von PRODUCT_ID
        List<IngotDTO> verfuegbareBarren = ingotService.findAllInStock();
        log.info("Verfügbare Barren im Lager: {}", verfuegbareBarren.size());

        if (verfuegbareBarren.isEmpty()) {
            Notification.show("Keine Barren im Lager gefunden!",
                3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_WARNING);
            return;
        }

        int maxAnzahl = maxBarrenField.getValue() != null ? maxBarrenField.getValue() : 6;
        int maxGewicht = maxGewichtField.getValue() != null ? maxGewichtField.getValue() : 64000;
        int remaining = selectedCalloff.getRemainingAmount();

        // Wenn remaining <= 0, nehmen wir maxAnzahl
        int anzahl = remaining > 0 ? Math.min(maxAnzahl, remaining) : maxAnzahl;
        anzahl = Math.min(anzahl, verfuegbareBarren.size());

        log.info("Ermittle {} Barren (max={}, remaining={}, verfügbar={})",
            anzahl, maxAnzahl, remaining, verfuegbareBarren.size());

        int aktuellesGewicht = 0;
        for (int i = 0; i < verfuegbareBarren.size() && geplanteBarren.size() < anzahl; i++) {
            IngotDTO barren = verfuegbareBarren.get(i);
            int barrenGewicht = barren.getWeight() != null ? barren.getWeight() : 0;

            if (aktuellesGewicht + barrenGewicht <= maxGewicht) {
                geplanteBarren.add(barren);
                aktuellesGewicht += barrenGewicht;
                log.debug("Barren hinzugefügt: {} ({} kg)", barren.getIngotNo(), barrenGewicht);
            }
        }

        if (!geplanteBarren.isEmpty()) {
            Integer ersteLaenge = geplanteBarren.get(0).getLength();
            langBarrenModus = ersteLaenge != null && ersteLaenge > 4000;

            Notification.show(geplanteBarren.size() + " Barren ermittelt für Abruf " + selectedCalloff.getCalloffNumber(),
                3000, Notification.Position.BOTTOM_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        } else {
            Notification.show("Keine passenden Barren gefunden (Gewichtslimit: " + maxGewicht + " kg)",
                3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_WARNING);
        }

        log.info("{} Barren ermittelt für Abruf {}", geplanteBarren.size(), selectedCalloff.getCalloffNumber());
    }

    private void starteBeladung() {
        if (geplanteBarren.isEmpty()) {
            Notification.show("Keine Barren zum Laden!", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_WARNING);
            return;
        }

        boolean craneAvailable = plcService.isConnected() || plcService.isSimulatorMode();
        if (!craneAvailable) {
            starteBeladungOhneKran();
            return;
        }

        beladungLaeuft = true;
        kranKommandoGesendet = false;
        startenBtn.setEnabled(false);
        pauseBtn.setEnabled(true);

        // Status speichern BEVOR der Processor gestartet wird
        saveStateToService();

        // Calloff-Info an Processor übergeben für Lieferschein-Erstellung
        String destination = lieferortCombo.getValue();
        processorService.setCalloffInfo(selectedCalloff, destination);

        // Hintergrund-Processor starten (läuft auch bei View-Wechsel weiter!)
        log.info("=== STARTE BELADUNG MIT HINTERGRUND-PROCESSOR ===");
        processorService.start();

        // UI-Update Scheduler starten (nur für lokale UI-Updates)
        beladungsTask = scheduler.scheduleAtFixedRate(this::pollUIStatus, 500, 500, TimeUnit.MILLISECONDS);

        Notification.show("Beladung gestartet", 2000, Notification.Position.BOTTOM_CENTER);
    }

    private void starteBeladungOhneKran() {
        beladungLaeuft = true;
        startenBtn.setEnabled(false);
        pauseBtn.setEnabled(true);

        beladungsTask = scheduler.scheduleAtFixedRate(this::pollBeladungOhneKran, 500, 800, TimeUnit.MILLISECONDS);

        Notification.show("Beladung gestartet (ohne Kran)", 2000, Notification.Position.BOTTOM_CENTER);
    }

    private void pollBeladungStatus() {
        // WICHTIG: Gesamte Methode in try-catch wrappen!
        // ScheduledExecutorService stoppt den Task bei uncaught Exceptions!
        try {
            if (!beladungLaeuft) {
                return;
            }

            var plcStatus = plcService.getCurrentStatus();
            final JobState jobState = plcStatus != null ? plcStatus.getJobState() : null;

            // Nur alle 10 Aufrufe loggen um Spam zu reduzieren
            if (System.currentTimeMillis() % 5000 < 500) {
                log.debug("pollBeladungStatus: jobState={}, kranKommandoGesendet={}, geplant={}, geladen={}",
                    jobState, kranKommandoGesendet, geplanteBarren.size(), geladeneBarren.size());
            }

            getUI().ifPresent(ui -> {
                try {
                    if (!ui.isAttached()) {
                        return;
                    }

                    ui.access(() -> {
                        try {
                            if (jobState == JobState.IDLE && kranKommandoGesendet) {
                                log.info(">>> Kran IDLE erkannt! Verarbeite abgelegten Barren...");
                                if (!geplanteBarren.isEmpty()) {
                                    IngotDTO barren = geplanteBarren.remove(0);
                                    geladeneBarren.add(barren);
                                    kranKommandoGesendet = false;
                                    saveStateToService();
                                    log.info(">>> Barren {} als geladen markiert", barren.getIngotNo());

                                    updateLadeflaeche();
                                    updateAnzeigen();
                                    updateTransportGrid();
                                    broadcastStatus();

                                    if (!geplanteBarren.isEmpty()) {
                                        IngotDTO naechsterBarren = geplanteBarren.get(0);
                                        log.info(">>> Sende Kommando für nächsten Barren: {}", naechsterBarren.getIngotNo());
                                        sendeCranKommando(naechsterBarren);
                                        kranKommandoGesendet = true;
                                    } else {
                                        log.info(">>> Alle Barren geladen - beladungFertig()");
                                        beladungFertig();
                                    }
                                }
                            }
                        } catch (Exception e) {
                            log.error("Fehler in ui.access: {}", e.getMessage(), e);
                        }
                    });
                } catch (Exception e) {
                    log.error("Fehler bei UI-Zugriff: {}", e.getMessage(), e);
                }
            });
        } catch (Exception e) {
            // NIEMALS eine Exception aus dem Scheduler-Task werfen!
            log.error("KRITISCH - Exception in pollBeladungStatus: {}", e.getMessage(), e);
        }
    }

    private void pollBeladungOhneKran() {
        if (!beladungLaeuft || geplanteBarren.isEmpty()) return;

        getUI().ifPresent(ui -> {
            if (!ui.isAttached()) return;

            ui.access(() -> {
                try {
                    IngotDTO barren = geplanteBarren.remove(0);
                    geladeneBarren.add(barren);
                    saveStateToService();

                    updateLadeflaeche();
                    updateAnzeigen();
                    updateTransportGrid();
                    broadcastStatus();

                    if (geplanteBarren.isEmpty()) {
                        beladungFertig();
                    }
                    // ui.push() nicht nötig - PushMode.AUTOMATIC macht das automatisch
                } catch (Exception e) {
                    log.error("Fehler: {}", e.getMessage());
                }
            });
        });
    }

    /**
     * Polling für UI-Updates - synchronisiert lokale UI mit dem StateService.
     * Die eigentliche Beladungslogik läuft im BeladungProcessorService.
     */
    private void pollUIStatus() {
        try {
            getUI().ifPresent(ui -> {
                if (!ui.isAttached()) return;

                ui.access(() -> {
                    try {
                        // Status vom StateService holen
                        boolean serverBeladungLaeuft = stateService.isBeladungLaeuft();
                        int serverGeladen = stateService.getGeladeneCount();
                        int localGeladen = geladeneBarren.size();

                        // Prüfen ob sich etwas geändert hat
                        if (serverGeladen != localGeladen) {
                            log.info("UI-Update: Server hat {} geladen, lokal {}", serverGeladen, localGeladen);

                            // Lokale Listen vom StateService synchronisieren
                            geplanteBarren = new ArrayList<>(stateService.getGeplanteBarren());
                            geladeneBarren = new ArrayList<>(stateService.getGeladeneBarren());
                            kranKommandoGesendet = stateService.isKranKommandoGesendet();

                            // UI aktualisieren
                            updateLadeflaeche();
                            updateAnzeigen();
                            updateTransportGrid();
                        }

                        // Beladung beendet?
                        // WICHTIG: Prüfe ob Server sagt "fertig" UND wir lokal noch "läuft" haben
                        if (!serverBeladungLaeuft && beladungLaeuft) {
                            log.info(">>> BELADUNG BEENDET ERKANNT! serverLaeuft={}, lokalLaeuft={}, geladen={}",
                                serverBeladungLaeuft, beladungLaeuft, serverGeladen);

                            // Synchronisiere Listen BEVOR beladungFertig() aufgerufen wird
                            geplanteBarren = new ArrayList<>(stateService.getGeplanteBarren());
                            geladeneBarren = new ArrayList<>(stateService.getGeladeneBarren());

                            log.info(">>> Listen synchronisiert: geplant={}, geladen={}",
                                geplanteBarren.size(), geladeneBarren.size());

                            beladungLaeuft = false;
                            beladungFertig();
                        }

                    } catch (Exception e) {
                        log.error("Fehler in pollUIStatus: {}", e.getMessage(), e);
                    }
                });
            });
        } catch (Exception e) {
            log.error("Kritischer Fehler in pollUIStatus: {}", e.getMessage(), e);
        }
    }

    private void sendeCranKommando(IngotDTO barren) {
        try {
            int[] pickupPos = getPickupPosition(barren);

            log.info("=== BELADUNG KRAN-KOMMANDO ===");
            log.info("  Barren: {} von Lagerplatz {} (ID={})",
                barren.getIngotNo(), barren.getStockyardNo(), barren.getStockyardId());
            log.info("  PICKUP:  X={}, Y={}, Z={}", pickupPos[0], pickupPos[1], pickupPos[2]);
            log.info("  RELEASE: X={}, Y={}, Z={} (TRAILER)", TRAILER_X, TRAILER_Y, TRAILER_Z);

            PlcCommand cmd = PlcCommand.builder()
                .pickupPosition(pickupPos[0], pickupPos[1], pickupPos[2])
                .releasePosition(TRAILER_X, TRAILER_Y, TRAILER_Z)
                .dimensions(
                    barren.getLength() != null ? barren.getLength() : 1800,
                    barren.getWidth() != null ? barren.getWidth() : 500,
                    barren.getThickness() != null ? barren.getThickness() : 200
                )
                .weight(barren.getWeight() != null ? barren.getWeight() : 8500)
                .longIngot(langBarrenModus)
                .rotate(false)
                .build();

            plcService.sendCommand(cmd);
            log.info("  Kommando gesendet: {}", cmd);
        } catch (Exception e) {
            log.error("Kran-Fehler: {}", e.getMessage(), e);
        }
    }

    private int[] getPickupPosition(IngotDTO barren) {
        if (barren.getStockyardId() != null) {
            try {
                var stockyardOpt = stockyardService.findById(barren.getStockyardId());
                if (stockyardOpt.isPresent()) {
                    var stockyard = stockyardOpt.get();
                    return new int[]{
                        stockyard.getXPosition() > 0 ? stockyard.getXPosition() : 27000,
                        stockyard.getYPosition() > 0 ? stockyard.getYPosition() : 15000,
                        stockyard.getZPosition() > 0 ? stockyard.getZPosition() : 2000
                    };
                }
            } catch (Exception e) {
                log.warn("Konnte Stockyard nicht laden: {}", e.getMessage());
            }
        }
        return new int[]{27000, 15000, 2000};
    }

    private void pauseBeladung() {
        if (beladungLaeuft) {
            // Pausieren
            beladungLaeuft = false;
            stateService.setBeladungLaeuft(false);

            // Hintergrund-Processor stoppen
            processorService.stop();

            // Lokalen UI-Task stoppen
            if (beladungsTask != null) {
                beladungsTask.cancel(false);
            }
            pauseBtn.setText("Fortsetzen");
            pauseBtn.setIcon(VaadinIcon.PLAY.create());
            Notification.show("Beladung pausiert", 2000, Notification.Position.BOTTOM_CENTER);
        } else {
            // Fortsetzen
            beladungLaeuft = true;
            stateService.setBeladungLaeuft(true);
            saveStateToService();

            boolean craneAvailable = plcService.isConnected() || plcService.isSimulatorMode();
            if (craneAvailable) {
                // Hintergrund-Processor starten
                processorService.start();
                // UI-Update Task starten
                beladungsTask = scheduler.scheduleAtFixedRate(this::pollUIStatus, 500, 500, TimeUnit.MILLISECONDS);
            } else {
                beladungsTask = scheduler.scheduleAtFixedRate(this::pollBeladungOhneKran, 500, 800, TimeUnit.MILLISECONDS);
            }
            pauseBtn.setText("Pause");
            pauseBtn.setIcon(VaadinIcon.PAUSE.create());
            Notification.show("Beladung fortgesetzt", 2000, Notification.Position.BOTTOM_CENTER);
        }
    }

    private void abbrechenBeladung() {
        beladungLaeuft = false;
        beladungAktiv = false;
        kranKommandoGesendet = false;

        // Hintergrund-Processor stoppen
        processorService.stop();

        // Lokalen UI-Task stoppen
        if (beladungsTask != null) {
            beladungsTask.cancel(false);
            beladungsTask = null;
        }

        geplanteBarren.clear();
        geladeneBarren.clear();
        selectedCalloff = null;
        beladungsNrField.clear();

        // StateService zurücksetzen
        stateService.reset();

        updateAnzeigen();
        updateLadeflaeche();
        updateTransportGrid();
        broadcastStatus();

        startenBtn.setEnabled(false);
        pauseBtn.setEnabled(false);

        Notification.show("Beladung abgebrochen", 2000, Notification.Position.BOTTOM_CENTER);
    }

    private void beladungFertig() {
        log.info("=== BELADUNG FERTIG (View-Local) ===");
        log.info("  geladeneBarren={}, geplanteBarren={}", geladeneBarren.size(), geplanteBarren.size());

        beladungLaeuft = false;
        kranKommandoGesendet = false;

        if (beladungsTask != null) {
            beladungsTask.cancel(false);
            beladungsTask = null;
        }

        pauseBtn.setEnabled(false);
        broadcastStatus();

        // Notification anzeigen - Dialog wird über BELADUNG_ENDED Broadcast geöffnet
        Notification.show("Beladung fertig! " + geladeneBarren.size() + " Barren geladen.",
            3000, Notification.Position.BOTTOM_CENTER)
            .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

        // HINWEIS: Lieferschein wird jetzt vom BeladungProcessorService erstellt
        // und über BELADUNG_ENDED Broadcast an alle Views verteilt
        log.info("  Warte auf BELADUNG_ENDED Broadcast für Lieferschein-Dialog...");
    }

    private void erstelleLieferschein() {
        log.info("erstelleLieferschein() aufgerufen, geladeneBarren={}", geladeneBarren.size());

        // Hole geladene Barren vom StateService falls lokal leer
        List<IngotDTO> barrenFuerLieferschein = geladeneBarren;
        if (barrenFuerLieferschein.isEmpty()) {
            barrenFuerLieferschein = new ArrayList<>(stateService.getGeladeneBarren());
            log.info("  Lokale Liste leer, vom StateService geholt: {} Barren", barrenFuerLieferschein.size());
        }

        if (barrenFuerLieferschein.isEmpty()) {
            log.warn("  Keine geladenen Barren fuer Lieferschein!");
            Notification.show("Keine geladenen Barren fuer Lieferschein",
                3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_WARNING);
            return;
        }

        try {
            String orderNumber = selectedCalloff != null ? selectedCalloff.getOrderNumber() : null;
            String destination = selectedCalloff != null ? selectedCalloff.getDestination() : lieferortCombo.getValue();
            String customerNumber = selectedCalloff != null ? selectedCalloff.getCustomerNumber() : null;
            String customerAddress = selectedCalloff != null ? selectedCalloff.getCustomerAddress() : null;

            log.info("  Erstelle Shipment: orderNumber={}, destination={}, barren={}",
                orderNumber, destination, barrenFuerLieferschein.size());

            Shipment shipment = shipmentService.createShipment(
                orderNumber, destination, customerNumber, customerAddress,
                new ArrayList<>(barrenFuerLieferschein)
            );

            log.info("  Shipment erstellt: id={}, number={}", shipment.getId(), shipment.getShipmentNumber());

            if (selectedCalloff != null) {
                calloffService.addDeliveredAmount(selectedCalloff.getId(), barrenFuerLieferschein.size());
            }

            DeliveryNoteDTO deliveryNote = new DeliveryNoteDTO();
            deliveryNote.setId(shipment.getId());
            deliveryNote.setDeliveryNoteNumber(shipment.getShipmentNumber());
            deliveryNote.setCreatedAt(shipment.getDelivered());
            if (selectedCalloff != null) {
                deliveryNote.setCalloffNumber(selectedCalloff.getCalloffNumber());
                deliveryNote.setOrderNumber(selectedCalloff.getOrderNumber());
                deliveryNote.setCustomerNumber(selectedCalloff.getCustomerNumber());
                deliveryNote.setCustomerAddress(selectedCalloff.getCustomerAddress());
                deliveryNote.setDestination(selectedCalloff.getDestination());
            }
            deliveryNote.setDeliveredIngots(new ArrayList<>(barrenFuerLieferschein));
            deliveryNote.calculateTotals();

            log.info("  Oeffne LieferungBestaetigenDialog...");
            LieferungBestaetigenDialog dialog = new LieferungBestaetigenDialog(deliveryNote, pdfService, shipmentService);
            dialog.open();
            log.info("  Dialog geoeffnet!");

        } catch (Exception e) {
            log.error("Fehler beim Lieferschein: {}", e.getMessage(), e);
            Notification.show("Fehler: " + e.getMessage(), 5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    /**
     * Öffnet den Lieferschein-Dialog für eine bestimmte Shipment-ID
     * (wird vom BELADUNG_ENDED Broadcast aufgerufen)
     */
    private void openLieferscheinDialogById(Long shipmentId, String shipmentNumber) {
        try {
            log.info("openLieferscheinDialogById: id={}, nr={}", shipmentId, shipmentNumber);

            Shipment shipment = shipmentService.findById(shipmentId).orElse(null);
            if (shipment == null) {
                log.error("Shipment nicht gefunden: {}", shipmentId);
                Notification.show("Lieferschein nicht gefunden!", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            // ShipmentLines laden und zu IngotDTOs konvertieren
            var lines = shipmentService.findLinesByShipmentId(shipmentId);
            List<IngotDTO> ingots = lines.stream()
                .map(line -> {
                    IngotDTO dto = new IngotDTO();
                    dto.setIngotNo(line.getIngotNumber());
                    dto.setProductNo(line.getProductNumber());
                    dto.setWeight(line.getWeight());
                    return dto;
                })
                .toList();

            // DeliveryNoteDTO erstellen
            DeliveryNoteDTO deliveryNote = new DeliveryNoteDTO();
            deliveryNote.setId(shipment.getId());
            deliveryNote.setDeliveryNoteNumber(shipment.getShipmentNumber());
            deliveryNote.setCreatedAt(shipment.getDelivered());
            deliveryNote.setOrderNumber(shipment.getOrderNumber());
            deliveryNote.setCustomerNumber(shipment.getCustomerNumber());
            deliveryNote.setCustomerAddress(shipment.getAddress());
            deliveryNote.setDestination(shipment.getDestination());
            if (selectedCalloff != null) {
                deliveryNote.setCalloffNumber(selectedCalloff.getCalloffNumber());
            }
            deliveryNote.setDeliveredIngots(new ArrayList<>(ingots));
            deliveryNote.calculateTotals();

            log.info("Öffne LieferungBestaetigenDialog für Shipment {}", shipmentNumber);
            LieferungBestaetigenDialog dialog = new LieferungBestaetigenDialog(deliveryNote, pdfService, shipmentService);
            dialog.open();

        } catch (Exception e) {
            log.error("Fehler beim Öffnen des Lieferschein-Dialogs: {}", e.getMessage(), e);
            Notification.show("Fehler: " + e.getMessage(), 5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void updateLadeflaeche() {
        if (ladeflaeche == null) return;

        // Alle Kinder außer Rädern entfernen
        ladeflaeche.getChildren()
            .filter(c -> !c.getElement().getClassList().contains("wheel"))
            .forEach(ladeflaeche::remove);

        // Alle Barren sammeln (geladen + geplant)
        List<IngotDTO> alleBarren = new ArrayList<>();
        alleBarren.addAll(geladeneBarren);
        alleBarren.addAll(geplanteBarren);

        if (alleBarren.isEmpty()) return;

        // Stapel-Container: Schichten von unten nach oben (2 Barren pro Schicht)
        Div stapelContainer = new Div();
        stapelContainer.getStyle()
            .set("position", "absolute")
            .set("bottom", "100%")
            .set("left", "50%")
            .set("transform", "translateX(-50%)")
            .set("display", "flex")
            .set("flex-direction", "column-reverse")  // Von unten nach oben stapeln
            .set("align-items", "center")
            .set("gap", "2px")
            .set("padding-bottom", "3px");

        // Barren in Schichten aufteilen (2 pro Schicht)
        for (int i = 0; i < alleBarren.size(); i += 2) {
            Div schicht = new Div();
            schicht.getStyle()
                .set("display", "flex")
                .set("flex-direction", "row")
                .set("gap", "4px");

            // Erster Barren der Schicht
            IngotDTO barren1 = alleBarren.get(i);
            boolean geladen1 = i < geladeneBarren.size();
            schicht.add(createBarrenDiv(barren1, geladen1));

            // Zweiter Barren der Schicht (falls vorhanden)
            if (i + 1 < alleBarren.size()) {
                IngotDTO barren2 = alleBarren.get(i + 1);
                boolean geladen2 = (i + 1) < geladeneBarren.size();
                schicht.add(createBarrenDiv(barren2, geladen2));
            }

            stapelContainer.add(schicht);
        }

        ladeflaeche.add(stapelContainer);
    }

    private Div createBarrenDiv(IngotDTO barren, boolean geladen) {
        Div div = new Div();

        String farbe = geladen ? FARBE_GELADEN : FARBE_GEPLANT;
        String border = geladen ? BORDER_GELADEN : BORDER_GEPLANT;

        // Breite Barren um alle Infos anzuzeigen (Längsformat)
        int breite = 250;
        int hoehe = 50;

        div.getStyle()
            .set("width", breite + "px")
            .set("height", hoehe + "px")
            .set("background", farbe)
            .set("border", "2px solid " + border)
            .set("border-radius", "4px")
            .set("display", "flex")
            .set("flex-direction", "row")
            .set("align-items", "center")
            .set("justify-content", "space-between")
            .set("padding", "0 8px")
            .set("font-size", "10px")
            .set("box-shadow", "2px 2px 4px rgba(0,0,0,0.3)");

        // Alle Werte auslesen
        String ingotNo = barren.getIngotNo() != null ? barren.getIngotNo() : "?";
        int length = barren.getLength() != null ? barren.getLength() : 0;
        int width = barren.getWidth() != null ? barren.getWidth() : 0;
        int thickness = barren.getThickness() != null ? barren.getThickness() : 0;
        int weight = barren.getWeight() != null ? barren.getWeight() : 0;
        String stockyard = barren.getStockyardNo() != null ? barren.getStockyardNo() : "-";

        // Barren-Nummer (links)
        Span nrSpan = new Span(ingotNo);
        nrSpan.getStyle()
            .set("font-weight", "bold")
            .set("font-size", "11px")
            .set("color", "#263238")
            .set("min-width", "70px");
        div.add(nrSpan);

        // Maße (Mitte)
        Span masseSpan = new Span(String.format("%d×%d×%d", length, width, thickness));
        masseSpan.getStyle()
            .set("font-size", "9px")
            .set("color", "#455A64")
            .set("min-width", "80px")
            .set("text-align", "center");
        div.add(masseSpan);

        // Gewicht (rechts-mitte)
        Span gewichtSpan = new Span(weight + " kg");
        gewichtSpan.getStyle()
            .set("font-weight", "bold")
            .set("font-size", "10px")
            .set("color", "#263238")
            .set("min-width", "55px")
            .set("text-align", "right");
        div.add(gewichtSpan);

        // Lagerplatz (rechts)
        Span lagerSpan = new Span(stockyard);
        lagerSpan.getStyle()
            .set("font-size", "9px")
            .set("color", "#607D8B")
            .set("min-width", "35px")
            .set("text-align", "right");
        div.add(lagerSpan);

        return div;
    }

    private void updateAnzeigen() {
        int gesamtGewicht = geladeneBarren.stream()
            .mapToInt(b -> b.getWeight() != null ? b.getWeight() : 0)
            .sum();

        int gewichtVorne = 0;
        int gewichtHinten = 0;
        for (int i = 0; i < geladeneBarren.size(); i++) {
            int weight = geladeneBarren.get(i).getWeight() != null ? geladeneBarren.get(i).getWeight() : 0;
            if (i < geladeneBarren.size() / 2 || geladeneBarren.size() == 1) {
                gewichtVorne += weight;
            } else {
                gewichtHinten += weight;
            }
        }

        gewichtVorneField.setValue(String.valueOf(gewichtVorne));
        gewichtHintenField.setValue(String.valueOf(gewichtHinten));
        gesamtGewichtField.setValue(String.valueOf(gesamtGewicht));
    }

    private void broadcastStatus() {
        if (broadcaster == null) return;
        broadcaster.broadcastStatusUpdate(geladeneBarren.size(),
            geplanteBarren.size() + geladeneBarren.size(), beladungLaeuft);
    }

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
    }

    private void restoreStateFromService() {
        beladungAktiv = stateService.isBeladungAktiv();
        beladungLaeuft = stateService.isBeladungLaeuft();
        langBarrenModus = stateService.isLangBarrenModus();
        kranKommandoGesendet = stateService.isKranKommandoGesendet();
        geplanteBarren = new ArrayList<>(stateService.getGeplanteBarren());
        geladeneBarren = new ArrayList<>(stateService.getGeladeneBarren());
        beladungsNummer = stateService.getBeladungsNummer();

        log.info("=== BELADUNG STATE RESTORED ===");
        log.info("  beladungAktiv={}, beladungLaeuft={}", beladungAktiv, beladungLaeuft);
        log.info("  kranKommandoGesendet={}", kranKommandoGesendet);
        log.info("  geplanteBarren={}, geladeneBarren={}", geplanteBarren.size(), geladeneBarren.size());

        if (beladungsNrField != null && !stateService.getBeladungsNr().isEmpty()) {
            beladungsNrField.setValue(stateService.getBeladungsNr());
        }

        updateAnzeigen();
        updateLadeflaeche();
        updateTransportGrid();

        if (beladungLaeuft && !geplanteBarren.isEmpty()) {
            boolean craneAvailable = plcService.isConnected() || plcService.isSimulatorMode();
            log.info("  >>> BELADUNG LÄUFT, processor aktiv={}, craneAvailable={}",
                processorService.isProcessing(), craneAvailable);

            // Starte Hintergrund-Processor falls nicht bereits aktiv
            if (craneAvailable && !processorService.isProcessing()) {
                log.info("  >>> STARTE HINTERGRUND-PROCESSOR");
                processorService.start();
            }

            // Starte UI-Update Scheduler (nur für lokale UI-Updates)
            if (craneAvailable) {
                beladungsTask = scheduler.scheduleAtFixedRate(this::pollUIStatus, 500, 500, TimeUnit.MILLISECONDS);
            } else {
                beladungsTask = scheduler.scheduleAtFixedRate(this::pollBeladungOhneKran, 500, 800, TimeUnit.MILLISECONDS);
            }
        } else {
            log.info("  >>> KEIN SCHEDULER GESTARTET (beladungLaeuft={}, geplant={})",
                beladungLaeuft, geplanteBarren.size());
        }

        startenBtn.setEnabled(beladungAktiv && !beladungLaeuft && !geplanteBarren.isEmpty());
        pauseBtn.setEnabled(beladungLaeuft);
    }

    // Footer-Button Aktionen
    private void erstelleTestDaten() {
        try {
            // 1. Test-Barren erstellen (falls keine vorhanden)
            int barrenErstellt = ingotService.createTestIngots();

            // 2. Test-Abrufe erstellen
            calloffService.createTestCalloff();

            // Zähle verfügbare Daten
            int barrenCount = ingotService.findAllInStock().size();
            var criteria = new CalloffSearchCriteria();
            criteria.setIncompleteOnly(true);
            criteria.setApprovedOnly(true);
            int abrufCount = calloffService.searchByCriteria(criteria).size();

            String msg = String.format("Test-Daten: %d Abrufe, %d Barren im Lager", abrufCount, barrenCount);
            if (barrenErstellt > 0) {
                msg += String.format(" (%d neue Barren erstellt)", barrenErstellt);
            }

            Notification.show(msg, 5000, Notification.Position.BOTTOM_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            log.info("Test-Daten erstellt: {} Abrufe, {} Barren (davon {} neu)", abrufCount, barrenCount, barrenErstellt);
        } catch (Exception e) {
            log.error("Fehler beim Erstellen der Test-Daten: {}", e.getMessage(), e);
            Notification.show("Fehler: " + e.getMessage(), 5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void showBaum() {
        Notification.show("Baum-Ansicht nicht implementiert", 2000, Notification.Position.MIDDLE);
    }

    private void drucken() {
        if (geladeneBarren.isEmpty()) {
            Notification.show("Keine Barren zum Drucken", 2000, Notification.Position.MIDDLE);
            return;
        }
        erstelleLieferschein();
    }

    private void alsGeliefertMarkieren() {
        if (selectedCalloff != null && !geladeneBarren.isEmpty()) {
            calloffService.addDelivered(selectedCalloff.getId(), geladeneBarren.size());
            Notification.show(geladeneBarren.size() + " Barren als geliefert markiert",
                3000, Notification.Position.BOTTOM_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        } else {
            Notification.show("Bitte zuerst Beladung durchführen", 2000, Notification.Position.MIDDLE);
        }
    }

    private void loeschen() {
        TransportAuftragZeile selected = transportGrid.asSingleSelect().getValue();
        if (selected != null) {
            Notification.show("Löschen nicht implementiert", 2000, Notification.Position.MIDDLE);
        } else {
            Notification.show("Bitte Eintrag auswählen", 2000, Notification.Position.MIDDLE);
        }
    }
}
