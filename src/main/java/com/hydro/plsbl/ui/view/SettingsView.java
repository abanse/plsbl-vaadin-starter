package com.hydro.plsbl.ui.view;

import com.hydro.plsbl.service.IngotService;
import com.hydro.plsbl.service.IngotTypeService;
import com.hydro.plsbl.service.LieferscheinPdfService;
import com.hydro.plsbl.service.ProductService;
import com.hydro.plsbl.service.SettingsService;
import com.hydro.plsbl.service.ShipmentService;
import com.hydro.plsbl.service.StockyardService;
import com.hydro.plsbl.simulator.CraneSimulatorConfig;
import com.hydro.plsbl.simulator.CraneSimulatorService;
import com.hydro.plsbl.ui.MainLayout;
import com.hydro.plsbl.ui.dialog.IngotTypeDialog;
import com.hydro.plsbl.ui.dialog.LieferscheinDialog;
import com.hydro.plsbl.ui.dialog.ProductDialog;
import com.hydro.plsbl.ui.dialog.StockyardManagementDialog;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Einstellungen-Ansicht
 *
 * Ermöglicht die Konfiguration von Kran, Säge und Lager-Parametern.
 */
@Route(value = "einstellungen", layout = MainLayout.class)
@PageTitle("Einstellungen | PLSBL")
public class SettingsView extends VerticalLayout {

    private static final Logger log = LoggerFactory.getLogger(SettingsView.class);

    private final SettingsService settingsService;
    private final CraneSimulatorConfig simulatorConfig;
    private final CraneSimulatorService simulatorService;
    private final ProductService productService;
    private final IngotTypeService ingotTypeService;
    private final StockyardService stockyardService;
    private final ShipmentService shipmentService;
    private final IngotService ingotService;
    private final LieferscheinPdfService pdfService;

    // Tabs
    private VerticalLayout sawContent;
    private VerticalLayout craneContent;
    private VerticalLayout warehouseContent;
    private VerticalLayout simulatorContent;
    private VerticalLayout spsContent;
    private VerticalLayout loadingContent;
    private VerticalLayout generalContent;
    private VerticalLayout colorsContent;
    private VerticalLayout kafkaContent;
    private VerticalLayout masterDataContent;

    // Säge-Felder
    private IntegerField sawXField;
    private IntegerField sawYField;
    private IntegerField sawZField;
    private IntegerField sawToleranzXField;
    private IntegerField sawToleranzYField;
    private IntegerField sawToleranzZField;

    // Lager-Felder
    private IntegerField lagerMinXField;
    private IntegerField lagerMinYField;
    private IntegerField lagerMaxXField;
    private IntegerField lagerMaxYField;
    private IntegerField gridColsField;
    private IntegerField gridRowsField;

    // Kran-Felder
    private IntegerField kranZielposMaxDXField;
    private IntegerField kranZielposMaxDYField;
    private IntegerField kranZielposMaxDZField;
    private IntegerField kranToleranzDXField;
    private IntegerField kranToleranzDYField;
    private IntegerField kranToleranzDZField;
    private IntegerField phasenerkennungDXField;
    private IntegerField phasenerkennungDYField;
    private IntegerField phasenerkennungDZField;

    // Simulator-Felder
    private IntegerField simIntervalField;
    private IntegerField simDeltaXField;
    private IntegerField simDeltaYField;
    private IntegerField simDeltaZField;
    private IntegerField simDefaultZField;
    private IntegerField simParkXField;
    private IntegerField simParkYField;

    // SPS-Felder
    private TextField spsUrlField;
    private IntegerField spsTimeoutField;
    private IntegerField spsRetryCountField;
    private IntegerField spsRetryDelayField;
    private Checkbox spsEnabledCheckbox;

    // Verladung-Felder
    private IntegerField loadingZoneFrontXField;
    private IntegerField loadingZoneRearXField;
    private IntegerField loadingZoneMiddleXField;
    private IntegerField loadingZoneYField;
    private IntegerField loadingZoneZField;
    private IntegerField loadingMaxIngotsField;
    private IntegerField loadingMaxWeightField;

    // Allgemein-Felder
    private IntegerField tempProductTimeoutField;
    private IntegerField retireDaysField;
    private TextField helpUrlField;

    // Farben-Felder
    private TextField colorYardEmptyField;
    private TextField colorYardInUseField;
    private TextField colorYardFullField;
    private TextField colorYardLockedField;
    private TextField colorYardNoSwapInField;
    private TextField colorYardNoSwapOutField;
    private TextField colorYardNoSwapInOutField;

    // Kafka-Felder
    private Checkbox kafkaEnabledCheckbox;
    private TextField kafkaBootstrapServersField;
    private TextField kafkaGroupIdField;
    private TextField kafkaClientIdField;
    private TextField kafkaTopicCalloffField;
    private TextField kafkaTopicPickupOrderField;
    private TextField kafkaTopicProductRestrictionField;
    private TextField kafkaTopicIngotPickedUpField;
    private TextField kafkaTopicIngotMovedField;
    private TextField kafkaTopicIngotModifiedField;
    private TextField kafkaTopicShipmentCompletedField;
    private TextField kafkaTopicSawFeedbackField;

    public SettingsView(SettingsService settingsService,
                        CraneSimulatorConfig simulatorConfig,
                        CraneSimulatorService simulatorService,
                        ProductService productService,
                        IngotTypeService ingotTypeService,
                        StockyardService stockyardService,
                        ShipmentService shipmentService,
                        IngotService ingotService,
                        LieferscheinPdfService pdfService) {
        this.settingsService = settingsService;
        this.simulatorConfig = simulatorConfig;
        this.simulatorService = simulatorService;
        this.productService = productService;
        this.ingotTypeService = ingotTypeService;
        this.stockyardService = stockyardService;
        this.shipmentService = shipmentService;
        this.ingotService = ingotService;
        this.pdfService = pdfService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        createHeader();
        createTabs();
        loadValues();
    }

    private void createHeader() {
        H3 title = new H3("Einstellungen");
        title.getStyle().set("margin", "0");

        Span info = new Span("Konfiguration von Kran, Säge und Lager-Parametern");
        info.getStyle().set("color", "gray");

        HorizontalLayout header = new HorizontalLayout(title, info);
        header.setAlignItems(Alignment.BASELINE);
        header.setWidthFull();

        add(header);
    }

    private void createTabs() {
        // Tab-Inhalte erstellen
        sawContent = createSawTab();
        craneContent = createCraneTab();
        warehouseContent = createWarehouseTab();
        simulatorContent = createSimulatorTab();
        spsContent = createSpsTab();
        loadingContent = createLoadingTab();
        generalContent = createGeneralTab();
        colorsContent = createColorsTab();
        kafkaContent = createKafkaTab();
        masterDataContent = createMasterDataTab();

        // Initial nur Säge anzeigen
        craneContent.setVisible(false);
        warehouseContent.setVisible(false);
        simulatorContent.setVisible(false);
        spsContent.setVisible(false);
        loadingContent.setVisible(false);
        generalContent.setVisible(false);
        colorsContent.setVisible(false);
        kafkaContent.setVisible(false);
        masterDataContent.setVisible(false);

        // Tabs erstellen
        Tab sawTab = new Tab(VaadinIcon.SCISSORS.create(), new Span("Säge"));
        Tab craneTab = new Tab(VaadinIcon.CONNECT.create(), new Span("Kran"));
        Tab warehouseTab = new Tab(VaadinIcon.STORAGE.create(), new Span("Lager"));
        Tab simulatorTab = new Tab(VaadinIcon.AUTOMATION.create(), new Span("Simulator"));
        Tab spsTab = new Tab(VaadinIcon.PLUG.create(), new Span("SPS"));
        Tab loadingTab = new Tab(VaadinIcon.TRUCK.create(), new Span("Verladung"));
        Tab generalTab = new Tab(VaadinIcon.COG.create(), new Span("Allgemein"));
        Tab colorsTab = new Tab(VaadinIcon.PAINT_ROLL.create(), new Span("Farben"));
        Tab kafkaTab = new Tab(VaadinIcon.ENVELOPE.create(), new Span("Kafka"));
        Tab masterDataTab = new Tab(VaadinIcon.DATABASE.create(), new Span("Stammdaten"));

        Tabs tabs = new Tabs(sawTab, craneTab, warehouseTab, simulatorTab, spsTab, loadingTab, generalTab, colorsTab, kafkaTab, masterDataTab);
        tabs.addSelectedChangeListener(event -> {
            sawContent.setVisible(event.getSelectedTab() == sawTab);
            craneContent.setVisible(event.getSelectedTab() == craneTab);
            warehouseContent.setVisible(event.getSelectedTab() == warehouseTab);
            simulatorContent.setVisible(event.getSelectedTab() == simulatorTab);
            spsContent.setVisible(event.getSelectedTab() == spsTab);
            loadingContent.setVisible(event.getSelectedTab() == loadingTab);
            generalContent.setVisible(event.getSelectedTab() == generalTab);
            colorsContent.setVisible(event.getSelectedTab() == colorsTab);
            kafkaContent.setVisible(event.getSelectedTab() == kafkaTab);
            masterDataContent.setVisible(event.getSelectedTab() == masterDataTab);
        });

        add(tabs);
        add(sawContent, craneContent, warehouseContent, simulatorContent, spsContent,
            loadingContent, generalContent, colorsContent, kafkaContent, masterDataContent);
    }

    private VerticalLayout createSawTab() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(true);

        // Abholposition
        Span sectionTitle = new Span("Abholposition");
        sectionTitle.getStyle().set("font-weight", "bold").set("font-size", "16px");
        layout.add(sectionTitle);

        FormLayout form1 = new FormLayout();
        form1.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("400px", 3)
        );

        sawXField = createIntegerField("Mitte X [mm]", 0, 200000);
        sawYField = createIntegerField("Vorderkante Y [mm]", 0, 200000);
        sawZField = createIntegerField("Auflagefläche Z [mm]", 0, 10000);

        form1.add(sawXField, sawYField, sawZField);
        layout.add(form1);

        layout.add(new Hr());

        // Toleranzen
        Span toleranzTitle = new Span("Toleranzen");
        toleranzTitle.getStyle().set("font-weight", "bold").set("font-size", "16px");
        layout.add(toleranzTitle);

        FormLayout form2 = new FormLayout();
        form2.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("400px", 3)
        );

        sawToleranzXField = createIntegerField("Toleranz X [mm]", 0, 5000);
        sawToleranzYField = createIntegerField("Toleranz Y [mm]", 0, 5000);
        sawToleranzZField = createIntegerField("Toleranz Z [mm]", 0, 5000);

        form2.add(sawToleranzXField, sawToleranzYField, sawToleranzZField);
        layout.add(form2);

        // Speichern Button
        layout.add(new Hr());
        layout.add(createSaveButton(() -> saveSawSettings()));

        return layout;
    }

    private VerticalLayout createCraneTab() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(true);

        // Zielposition max
        Span section1 = new Span("Kran Zielposition max.");
        section1.getStyle().set("font-weight", "bold").set("font-size", "16px");
        layout.add(section1);

        FormLayout form1 = new FormLayout();
        form1.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("400px", 3)
        );

        kranZielposMaxDXField = createIntegerField("dX [mm]", 0, 5000);
        kranZielposMaxDYField = createIntegerField("dY [mm]", 0, 5000);
        kranZielposMaxDZField = createIntegerField("dZ [mm]", 0, 5000);

        form1.add(kranZielposMaxDXField, kranZielposMaxDYField, kranZielposMaxDZField);
        layout.add(form1);

        layout.add(new Hr());

        // Toleranzen
        Span section2 = new Span("Kran Toleranz");
        section2.getStyle().set("font-weight", "bold").set("font-size", "16px");
        layout.add(section2);

        FormLayout form2 = new FormLayout();
        form2.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("400px", 3)
        );

        kranToleranzDXField = createIntegerField("dX [mm]", 0, 1000);
        kranToleranzDYField = createIntegerField("dY [mm]", 0, 1000);
        kranToleranzDZField = createIntegerField("dZ [mm]", 0, 1000);

        form2.add(kranToleranzDXField, kranToleranzDYField, kranToleranzDZField);
        layout.add(form2);

        layout.add(new Hr());

        // Phasenerkennung
        Span section3 = new Span("Phasenerkennung");
        section3.getStyle().set("font-weight", "bold").set("font-size", "16px");
        layout.add(section3);

        FormLayout form3 = new FormLayout();
        form3.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("400px", 3)
        );

        phasenerkennungDXField = createIntegerField("dX [mm]", 0, 2000);
        phasenerkennungDYField = createIntegerField("dY [mm]", 0, 2000);
        phasenerkennungDZField = createIntegerField("dZ [mm]", 0, 2000);

        form3.add(phasenerkennungDXField, phasenerkennungDYField, phasenerkennungDZField);
        layout.add(form3);

        // Speichern Button
        layout.add(new Hr());
        layout.add(createSaveButton(() -> saveCraneSettings()));

        return layout;
    }

    private VerticalLayout createWarehouseTab() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(true);

        // Lager-Dimensionen
        Span section1 = new Span("Internes Lager - Dimensionen");
        section1.getStyle().set("font-weight", "bold").set("font-size", "16px");
        layout.add(section1);

        FormLayout form1 = new FormLayout();
        form1.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("400px", 2)
        );

        lagerMinXField = createIntegerField("Min. X [mm]", 0, 200000);
        lagerMaxXField = createIntegerField("Max. X [mm]", 0, 200000);
        lagerMinYField = createIntegerField("Min. Y [mm]", 0, 200000);
        lagerMaxYField = createIntegerField("Max. Y [mm]", 0, 200000);

        form1.add(lagerMinXField, lagerMaxXField, lagerMinYField, lagerMaxYField);
        layout.add(form1);

        layout.add(new Hr());

        // Grid-Einstellungen
        Span section2 = new Span("Grid-Anzeige");
        section2.getStyle().set("font-weight", "bold").set("font-size", "16px");
        layout.add(section2);

        FormLayout form2 = new FormLayout();
        form2.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("400px", 2)
        );

        gridColsField = createIntegerField("Spalten", 1, 50);
        gridRowsField = createIntegerField("Zeilen", 1, 50);

        form2.add(gridColsField, gridRowsField);
        layout.add(form2);

        // Speichern Button
        layout.add(new Hr());
        layout.add(createSaveButton(() -> saveWarehouseSettings()));

        return layout;
    }

    private VerticalLayout createSimulatorTab() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(true);

        // Status-Anzeige
        Span statusTitle = new Span("Simulator-Status");
        statusTitle.getStyle().set("font-weight", "bold").set("font-size", "16px");
        layout.add(statusTitle);

        HorizontalLayout statusLayout = new HorizontalLayout();
        statusLayout.setAlignItems(Alignment.CENTER);

        Span statusLabel = new Span(simulatorService.isRunning() ? "Läuft" : "Gestoppt");
        statusLabel.getStyle()
            .set("padding", "5px 10px")
            .set("border-radius", "4px")
            .set("background-color", simulatorService.isRunning() ? "#4caf50" : "#f44336")
            .set("color", "white");

        Button toggleButton = new Button(simulatorService.isRunning() ? "Stoppen" : "Starten");
        toggleButton.addClickListener(e -> {
            if (simulatorService.isRunning()) {
                simulatorService.stop();
                statusLabel.setText("Gestoppt");
                statusLabel.getStyle().set("background-color", "#f44336");
                toggleButton.setText("Starten");
            } else {
                simulatorService.start();
                statusLabel.setText("Läuft");
                statusLabel.getStyle().set("background-color", "#4caf50");
                toggleButton.setText("Stoppen");
            }
        });

        Button resetButton = new Button("Reset", VaadinIcon.REFRESH.create());
        resetButton.addClickListener(e -> {
            simulatorService.reset();
            Notification.show("Simulator zurückgesetzt", 2000, Notification.Position.BOTTOM_CENTER);
        });

        statusLayout.add(statusLabel, toggleButton, resetButton);
        layout.add(statusLayout);

        layout.add(new Hr());

        // Timing
        Span section1 = new Span("Timing");
        section1.getStyle().set("font-weight", "bold").set("font-size", "16px");
        layout.add(section1);

        FormLayout form1 = new FormLayout();
        form1.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        simIntervalField = createIntegerField("Intervall [ms]", 100, 5000);
        form1.add(simIntervalField);
        layout.add(form1);

        layout.add(new Hr());

        // Bewegung
        Span section2 = new Span("Bewegungsschrittweite pro Intervall");
        section2.getStyle().set("font-weight", "bold").set("font-size", "16px");
        layout.add(section2);

        FormLayout form2 = new FormLayout();
        form2.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("400px", 3)
        );

        simDeltaXField = createIntegerField("Delta X [mm]", 10, 10000);
        simDeltaYField = createIntegerField("Delta Y [mm]", 10, 10000);
        simDeltaZField = createIntegerField("Delta Z [mm]", 10, 5000);

        form2.add(simDeltaXField, simDeltaYField, simDeltaZField);
        layout.add(form2);

        layout.add(new Hr());

        // Positionen
        Span section3 = new Span("Positionen");
        section3.getStyle().set("font-weight", "bold").set("font-size", "16px");
        layout.add(section3);

        FormLayout form3 = new FormLayout();
        form3.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("400px", 3)
        );

        simDefaultZField = createIntegerField("Standard-Höhe Z [mm]", 0, 10000);
        simParkXField = createIntegerField("Park-Position X [mm]", 0, 200000);
        simParkYField = createIntegerField("Park-Position Y [mm]", 0, 200000);

        form3.add(simDefaultZField, simParkXField, simParkYField);
        layout.add(form3);

        // Speichern Button
        layout.add(new Hr());
        layout.add(createSaveButton(() -> saveSimulatorSettings()));

        return layout;
    }

    private VerticalLayout createSpsTab() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(true);

        // Verbindung
        Span section1 = new Span("SPS-Verbindung");
        section1.getStyle().set("font-weight", "bold").set("font-size", "16px");
        layout.add(section1);

        // Aktiviert Checkbox
        spsEnabledCheckbox = new Checkbox("SPS-Verbindung aktiviert");
        layout.add(spsEnabledCheckbox);

        FormLayout form1 = new FormLayout();
        form1.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        spsUrlField = new TextField("PLC4X Verbindungs-URL");
        spsUrlField.setWidthFull();
        spsUrlField.setPlaceholder("s7://IP:PORT?remote-slot=N");
        form1.add(spsUrlField);

        layout.add(form1);

        layout.add(new Hr());

        // Timing
        Span section2 = new Span("Timing & Wiederholung");
        section2.getStyle().set("font-weight", "bold").set("font-size", "16px");
        layout.add(section2);

        FormLayout form2 = new FormLayout();
        form2.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("400px", 3)
        );

        spsTimeoutField = createIntegerField("Timeout [s]", 1, 120);
        spsRetryCountField = createIntegerField("Wiederholungen", 0, 10);
        spsRetryDelayField = createIntegerField("Wartezeit [ms]", 100, 10000);

        form2.add(spsTimeoutField, spsRetryCountField, spsRetryDelayField);
        layout.add(form2);

        layout.add(new Hr());

        // Hinweis
        Span hint = new Span("Hinweis: Änderungen an der SPS-Verbindung erfordern einen Neustart der Anwendung.");
        hint.getStyle().set("color", "gray").set("font-style", "italic");
        layout.add(hint);

        // Speichern Button
        layout.add(new Hr());
        layout.add(createSaveButton(() -> saveSpsSettings()));

        return layout;
    }

    private VerticalLayout createLoadingTab() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(true);

        // Ladezonen-Positionen
        Span section1 = new Span("Ladezonen-Positionen");
        section1.getStyle().set("font-weight", "bold").set("font-size", "16px");
        layout.add(section1);

        FormLayout form1 = new FormLayout();
        form1.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("400px", 3)
        );

        loadingZoneFrontXField = createIntegerField("Vorne X [mm]", 0, 200000);
        loadingZoneRearXField = createIntegerField("Hinten X [mm]", 0, 200000);
        loadingZoneMiddleXField = createIntegerField("Mitte X [mm]", 0, 200000);

        form1.add(loadingZoneFrontXField, loadingZoneRearXField, loadingZoneMiddleXField);
        layout.add(form1);

        FormLayout form2 = new FormLayout();
        form2.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("400px", 2)
        );

        loadingZoneYField = createIntegerField("Y [mm]", 0, 200000);
        loadingZoneZField = createIntegerField("Z [mm]", 0, 10000);

        form2.add(loadingZoneYField, loadingZoneZField);
        layout.add(form2);

        layout.add(new Hr());

        // Limits
        Span section2 = new Span("Maximale Beladung");
        section2.getStyle().set("font-weight", "bold").set("font-size", "16px");
        layout.add(section2);

        FormLayout form3 = new FormLayout();
        form3.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("400px", 2)
        );

        loadingMaxIngotsField = createIntegerField("Max. Ingots", 1, 100);
        loadingMaxWeightField = createIntegerField("Max. Gewicht [kg]", 1000, 100000);

        form3.add(loadingMaxIngotsField, loadingMaxWeightField);
        layout.add(form3);

        // Speichern Button
        layout.add(new Hr());
        layout.add(createSaveButton(() -> saveLoadingSettings()));

        return layout;
    }

    private VerticalLayout createGeneralTab() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(true);

        // Timeouts
        Span section1 = new Span("Zeiteinstellungen");
        section1.getStyle().set("font-weight", "bold").set("font-size", "16px");
        layout.add(section1);

        FormLayout form1 = new FormLayout();
        form1.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("400px", 2)
        );

        tempProductTimeoutField = createIntegerField("Temp. Produkt Timeout [min]", 1, 1440);
        retireDaysField = createIntegerField("Ruhestandstage", 1, 9999);

        form1.add(tempProductTimeoutField, retireDaysField);
        layout.add(form1);

        layout.add(new Hr());

        // URLs
        Span section2 = new Span("URLs");
        section2.getStyle().set("font-weight", "bold").set("font-size", "16px");
        layout.add(section2);

        FormLayout form2 = new FormLayout();
        form2.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        helpUrlField = new TextField("Hilfe-URL");
        helpUrlField.setWidthFull();
        helpUrlField.setPlaceholder("https://help.example.com");

        form2.add(helpUrlField);
        layout.add(form2);

        // Speichern Button
        layout.add(new Hr());
        layout.add(createSaveButton(() -> saveGeneralSettings()));

        return layout;
    }

    private VerticalLayout createColorsTab() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(true);

        // Lagerplatz-Status
        Span section1 = new Span("Lagerplatz-Farben nach Status");
        section1.getStyle().set("font-weight", "bold").set("font-size", "16px");
        layout.add(section1);

        // Grid für die Farbfelder
        VerticalLayout colorGrid = new VerticalLayout();
        colorGrid.setPadding(false);
        colorGrid.setSpacing(true);

        colorYardEmptyField = createColorField("Leer");
        colorYardInUseField = createColorField("Belegt");
        colorYardFullField = createColorField("Voll");
        colorYardLockedField = createColorField("Gesperrt");

        colorGrid.add(
            createColorRow("Leer", colorYardEmptyField, "Freie Lagerplaetze"),
            createColorRow("Belegt", colorYardInUseField, "Lagerplaetze mit Bestand"),
            createColorRow("Voll", colorYardFullField, "Volle Lagerplaetze"),
            createColorRow("Gesperrt", colorYardLockedField, "Gesperrte Lagerplaetze")
        );
        layout.add(colorGrid);

        layout.add(new Hr());

        // Swap-Restriktionen
        Span section2 = new Span("Lagerplatz-Farben nach Restriktionen");
        section2.getStyle().set("font-weight", "bold").set("font-size", "16px");
        layout.add(section2);

        VerticalLayout restrictionGrid = new VerticalLayout();
        restrictionGrid.setPadding(false);
        restrictionGrid.setSpacing(true);

        colorYardNoSwapInField = createColorField("Keine Einlagerung");
        colorYardNoSwapOutField = createColorField("Keine Auslagerung");
        colorYardNoSwapInOutField = createColorField("Keine Ein-/Auslagerung");

        restrictionGrid.add(
            createColorRow("Keine Einlagerung", colorYardNoSwapInField, "Einlagerung gesperrt"),
            createColorRow("Keine Auslagerung", colorYardNoSwapOutField, "Auslagerung gesperrt"),
            createColorRow("Keine Ein-/Auslagerung", colorYardNoSwapInOutField, "Komplett gesperrt")
        );
        layout.add(restrictionGrid);

        // Speichern Button
        layout.add(new Hr());
        layout.add(createSaveButton(() -> saveColorSettings()));

        return layout;
    }

    private TextField createColorField(String label) {
        TextField field = new TextField();
        field.setWidth("80px");
        field.setMaxLength(7);
        field.setPattern("#[0-9A-Fa-f]{6}");
        field.setPlaceholder("#RRGGBB");
        // HTML5 Color Input als Prefix
        field.getElement().executeJs(
            "const colorInput = document.createElement('input');" +
            "colorInput.type = 'color';" +
            "colorInput.style.width = '30px';" +
            "colorInput.style.height = '30px';" +
            "colorInput.style.border = 'none';" +
            "colorInput.style.padding = '0';" +
            "colorInput.style.cursor = 'pointer';" +
            "colorInput.addEventListener('input', (e) => {" +
            "  this.value = e.target.value.toUpperCase();" +
            "  this.dispatchEvent(new Event('change'));" +
            "});" +
            "this.addEventListener('change', (e) => {" +
            "  if (this.value && this.value.match(/^#[0-9A-Fa-f]{6}$/)) {" +
            "    colorInput.value = this.value;" +
            "  }" +
            "});" +
            "this.shadowRoot.querySelector('[part=\"input-field\"]').prepend(colorInput);" +
            "this._colorInput = colorInput;"
        );
        return field;
    }

    private HorizontalLayout createColorRow(String label, TextField colorField, String description) {
        HorizontalLayout row = new HorizontalLayout();
        row.setAlignItems(Alignment.CENTER);
        row.setSpacing(true);

        // Farbvorschau-Box
        Span previewBox = new Span();
        previewBox.setWidth("40px");
        previewBox.setHeight("30px");
        previewBox.getStyle()
            .set("border", "1px solid #ccc")
            .set("border-radius", "4px")
            .set("display", "inline-block");

        // Label
        Span labelSpan = new Span(label);
        labelSpan.setWidth("180px");
        labelSpan.getStyle().set("font-weight", "500");

        // Beschreibung
        Span descSpan = new Span(description);
        descSpan.getStyle().set("color", "gray").set("font-size", "14px");

        // Vorschau aktualisieren bei Änderung
        colorField.addValueChangeListener(e -> {
            String color = e.getValue();
            if (color != null && color.matches("#[0-9A-Fa-f]{6}")) {
                previewBox.getStyle().set("background-color", color);
                // Aktualisiere auch den nativen ColorPicker
                colorField.getElement().executeJs(
                    "if (this._colorInput) this._colorInput.value = $0", color);
            }
        });

        row.add(previewBox, labelSpan, colorField, descSpan);
        return row;
    }

    private IntegerField createIntegerField(String label, int min, int max) {
        IntegerField field = new IntegerField(label);
        field.setMin(min);
        field.setMax(max);
        field.setStepButtonsVisible(true);
        field.setWidth("150px");
        return field;
    }

    private HorizontalLayout createSaveButton(Runnable saveAction) {
        Button saveButton = new Button("Speichern", VaadinIcon.CHECK.create());
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveButton.addClickListener(e -> {
            saveAction.run();
            Notification.show("Einstellungen gespeichert", 3000, Notification.Position.BOTTOM_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });

        HorizontalLayout layout = new HorizontalLayout(saveButton);
        layout.setJustifyContentMode(JustifyContentMode.END);
        layout.setWidthFull();
        return layout;
    }

    private void loadValues() {
        // Säge
        sawXField.setValue(settingsService.getSawX());
        sawYField.setValue(settingsService.getSawY());
        sawZField.setValue(settingsService.getSawZ());
        sawToleranzXField.setValue(settingsService.getSawToleranzX());
        sawToleranzYField.setValue(settingsService.getSawToleranzY());
        sawToleranzZField.setValue(settingsService.getSawToleranzZ());

        // Lager
        lagerMinXField.setValue(settingsService.getLagerMinX());
        lagerMinYField.setValue(settingsService.getLagerMinY());
        lagerMaxXField.setValue(settingsService.getLagerMaxX());
        lagerMaxYField.setValue(settingsService.getLagerMaxY());
        gridColsField.setValue(settingsService.getGridCols());
        gridRowsField.setValue(settingsService.getGridRows());

        // Kran
        kranZielposMaxDXField.setValue(settingsService.getKranZielposMaxDX());
        kranZielposMaxDYField.setValue(settingsService.getKranZielposMaxDY());
        kranZielposMaxDZField.setValue(settingsService.getKranZielposMaxDZ());
        kranToleranzDXField.setValue(settingsService.getKranToleranzDX());
        kranToleranzDYField.setValue(settingsService.getKranToleranzDY());
        kranToleranzDZField.setValue(settingsService.getKranToleranzDZ());
        phasenerkennungDXField.setValue(settingsService.getPhasenerkennungDX());
        phasenerkennungDYField.setValue(settingsService.getPhasenerkennungDY());
        phasenerkennungDZField.setValue(settingsService.getPhasenerkennungDZ());

        // Simulator
        simIntervalField.setValue(simulatorConfig.getIntervalMs());
        simDeltaXField.setValue(simulatorConfig.getDeltaX());
        simDeltaYField.setValue(simulatorConfig.getDeltaY());
        simDeltaZField.setValue(simulatorConfig.getDeltaZ());
        simDefaultZField.setValue(simulatorConfig.getDefaultZ());
        simParkXField.setValue(simulatorConfig.getParkX());
        simParkYField.setValue(simulatorConfig.getParkY());

        // SPS
        spsUrlField.setValue(settingsService.getSpsUrl());
        spsTimeoutField.setValue(settingsService.getSpsTimeout());
        spsRetryCountField.setValue(settingsService.getSpsRetryCount());
        spsRetryDelayField.setValue(settingsService.getSpsRetryDelay());
        spsEnabledCheckbox.setValue(settingsService.isSpsEnabled());

        // Verladung
        loadingZoneFrontXField.setValue(settingsService.getLoadingZoneFrontX());
        loadingZoneRearXField.setValue(settingsService.getLoadingZoneRearX());
        loadingZoneMiddleXField.setValue(settingsService.getLoadingZoneMiddleX());
        loadingZoneYField.setValue(settingsService.getLoadingZoneY());
        loadingZoneZField.setValue(settingsService.getLoadingZoneZ());
        loadingMaxIngotsField.setValue(settingsService.getLoadingMaxIngots());
        loadingMaxWeightField.setValue(settingsService.getLoadingMaxWeight());

        // Allgemein
        tempProductTimeoutField.setValue(settingsService.getTemporaryProductTimeout());
        retireDaysField.setValue(settingsService.getRetireDays());
        helpUrlField.setValue(settingsService.getHelpUrl());

        // Farben
        colorYardEmptyField.setValue(settingsService.getColorYardEmpty());
        colorYardInUseField.setValue(settingsService.getColorYardInUse());
        colorYardFullField.setValue(settingsService.getColorYardFull());
        colorYardLockedField.setValue(settingsService.getColorYardLocked());
        colorYardNoSwapInField.setValue(settingsService.getColorYardNoSwapIn());
        colorYardNoSwapOutField.setValue(settingsService.getColorYardNoSwapOut());
        colorYardNoSwapInOutField.setValue(settingsService.getColorYardNoSwapInOut());

        // Kafka
        kafkaEnabledCheckbox.setValue(settingsService.isKafkaEnabled());
        kafkaBootstrapServersField.setValue(settingsService.getKafkaBootstrapServers());
        kafkaGroupIdField.setValue(settingsService.getKafkaGroupId());
        kafkaClientIdField.setValue(settingsService.getKafkaClientId());
        kafkaTopicCalloffField.setValue(settingsService.getKafkaTopicCalloff());
        kafkaTopicPickupOrderField.setValue(settingsService.getKafkaTopicPickupOrder());
        kafkaTopicProductRestrictionField.setValue(settingsService.getKafkaTopicProductRestriction());
        kafkaTopicIngotPickedUpField.setValue(settingsService.getKafkaTopicIngotPickedUp());
        kafkaTopicIngotMovedField.setValue(settingsService.getKafkaTopicIngotMoved());
        kafkaTopicIngotModifiedField.setValue(settingsService.getKafkaTopicIngotModified());
        kafkaTopicShipmentCompletedField.setValue(settingsService.getKafkaTopicShipmentCompleted());
        kafkaTopicSawFeedbackField.setValue(settingsService.getKafkaTopicSawFeedback());
    }

    private void saveSawSettings() {
        settingsService.setSawX(sawXField.getValue());
        settingsService.setSawY(sawYField.getValue());
        settingsService.setSawZ(sawZField.getValue());
        settingsService.setSawToleranzX(sawToleranzXField.getValue());
        settingsService.setSawToleranzY(sawToleranzYField.getValue());
        settingsService.setSawToleranzZ(sawToleranzZField.getValue());
        log.info("Säge-Einstellungen gespeichert");
    }

    private void saveCraneSettings() {
        settingsService.setKranZielposMaxDX(kranZielposMaxDXField.getValue());
        settingsService.setKranZielposMaxDY(kranZielposMaxDYField.getValue());
        settingsService.setKranZielposMaxDZ(kranZielposMaxDZField.getValue());
        settingsService.setKranToleranzDX(kranToleranzDXField.getValue());
        settingsService.setKranToleranzDY(kranToleranzDYField.getValue());
        settingsService.setKranToleranzDZ(kranToleranzDZField.getValue());
        settingsService.setPhasenerkennungDX(phasenerkennungDXField.getValue());
        settingsService.setPhasenerkennungDY(phasenerkennungDYField.getValue());
        settingsService.setPhasenerkennungDZ(phasenerkennungDZField.getValue());
        log.info("Kran-Einstellungen gespeichert");
    }

    private void saveWarehouseSettings() {
        settingsService.setLagerMinX(lagerMinXField.getValue());
        settingsService.setLagerMinY(lagerMinYField.getValue());
        settingsService.setLagerMaxX(lagerMaxXField.getValue());
        settingsService.setLagerMaxY(lagerMaxYField.getValue());
        settingsService.setGridCols(gridColsField.getValue());
        settingsService.setGridRows(gridRowsField.getValue());
        log.info("Lager-Einstellungen gespeichert");
    }

    private void saveSimulatorSettings() {
        simulatorConfig.setIntervalMs(simIntervalField.getValue());
        simulatorConfig.setDeltaX(simDeltaXField.getValue());
        simulatorConfig.setDeltaY(simDeltaYField.getValue());
        simulatorConfig.setDeltaZ(simDeltaZField.getValue());
        simulatorConfig.setDefaultZ(simDefaultZField.getValue());
        simulatorConfig.setParkX(simParkXField.getValue());
        simulatorConfig.setParkY(simParkYField.getValue());
        log.info("Simulator-Einstellungen gespeichert");

        // Hinweis: Simulator muss neu gestartet werden für Intervall-Änderung
        Notification.show("Hinweis: Für Intervall-Änderung Simulator neu starten",
            5000, Notification.Position.BOTTOM_CENTER);
    }

    private void saveSpsSettings() {
        settingsService.setSpsUrl(spsUrlField.getValue());
        settingsService.setSpsTimeout(spsTimeoutField.getValue());
        settingsService.setSpsRetryCount(spsRetryCountField.getValue());
        settingsService.setSpsRetryDelay(spsRetryDelayField.getValue());
        settingsService.setSpsEnabled(spsEnabledCheckbox.getValue());
        log.info("SPS-Einstellungen gespeichert: URL={}", spsUrlField.getValue());
    }

    private void saveLoadingSettings() {
        settingsService.setLoadingZoneFrontX(loadingZoneFrontXField.getValue());
        settingsService.setLoadingZoneRearX(loadingZoneRearXField.getValue());
        settingsService.setLoadingZoneMiddleX(loadingZoneMiddleXField.getValue());
        settingsService.setLoadingZoneY(loadingZoneYField.getValue());
        settingsService.setLoadingZoneZ(loadingZoneZField.getValue());
        settingsService.setLoadingMaxIngots(loadingMaxIngotsField.getValue());
        settingsService.setLoadingMaxWeight(loadingMaxWeightField.getValue());
        log.info("Verladung-Einstellungen gespeichert");
    }

    private void saveGeneralSettings() {
        settingsService.setTemporaryProductTimeout(tempProductTimeoutField.getValue());
        settingsService.setRetireDays(retireDaysField.getValue());
        settingsService.setHelpUrl(helpUrlField.getValue());
        log.info("Allgemein-Einstellungen gespeichert");
    }

    private void saveColorSettings() {
        settingsService.setColorYardEmpty(colorYardEmptyField.getValue());
        settingsService.setColorYardInUse(colorYardInUseField.getValue());
        settingsService.setColorYardFull(colorYardFullField.getValue());
        settingsService.setColorYardLocked(colorYardLockedField.getValue());
        settingsService.setColorYardNoSwapIn(colorYardNoSwapInField.getValue());
        settingsService.setColorYardNoSwapOut(colorYardNoSwapOutField.getValue());
        settingsService.setColorYardNoSwapInOut(colorYardNoSwapInOutField.getValue());
        log.info("Farben-Einstellungen gespeichert");
    }

    private VerticalLayout createKafkaTab() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(true);

        // Aktivierung
        Span section1 = new Span("Kafka-Verbindung");
        section1.getStyle().set("font-weight", "bold").set("font-size", "16px");
        layout.add(section1);

        kafkaEnabledCheckbox = new Checkbox("Kafka aktiviert");
        layout.add(kafkaEnabledCheckbox);

        FormLayout form1 = new FormLayout();
        form1.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        kafkaBootstrapServersField = new TextField("Bootstrap Servers");
        kafkaBootstrapServersField.setWidthFull();
        kafkaBootstrapServersField.setPlaceholder("localhost:9092");

        kafkaGroupIdField = new TextField("Group ID");
        kafkaGroupIdField.setWidth("300px");

        kafkaClientIdField = new TextField("Client ID");
        kafkaClientIdField.setWidth("300px");

        form1.add(kafkaBootstrapServersField, kafkaGroupIdField, kafkaClientIdField);
        layout.add(form1);

        layout.add(new Hr());

        // Eingehende Topics
        Span section2 = new Span("Eingehende Topics (von SAP/Saege)");
        section2.getStyle().set("font-weight", "bold").set("font-size", "16px");
        layout.add(section2);

        FormLayout form2 = new FormLayout();
        form2.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("600px", 3)
        );

        kafkaTopicCalloffField = new TextField("Abruf-Telegramme");
        kafkaTopicCalloffField.setWidth("200px");

        kafkaTopicPickupOrderField = new TextField("Abholauftraege");
        kafkaTopicPickupOrderField.setWidth("200px");

        kafkaTopicProductRestrictionField = new TextField("Produkt-Restriktionen");
        kafkaTopicProductRestrictionField.setWidth("200px");

        form2.add(kafkaTopicCalloffField, kafkaTopicPickupOrderField, kafkaTopicProductRestrictionField);
        layout.add(form2);

        layout.add(new Hr());

        // Ausgehende Topics
        Span section3 = new Span("Ausgehende Topics (an SAP/Saege)");
        section3.getStyle().set("font-weight", "bold").set("font-size", "16px");
        layout.add(section3);

        FormLayout form3 = new FormLayout();
        form3.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("600px", 3)
        );

        kafkaTopicIngotPickedUpField = new TextField("Barren abgeholt");
        kafkaTopicIngotPickedUpField.setWidth("200px");

        kafkaTopicIngotMovedField = new TextField("Barren umgelagert");
        kafkaTopicIngotMovedField.setWidth("200px");

        kafkaTopicIngotModifiedField = new TextField("Barren modifiziert");
        kafkaTopicIngotModifiedField.setWidth("200px");

        kafkaTopicShipmentCompletedField = new TextField("Lieferung abgeschlossen");
        kafkaTopicShipmentCompletedField.setWidth("200px");

        kafkaTopicSawFeedbackField = new TextField("Saege-Rueckmeldung");
        kafkaTopicSawFeedbackField.setWidth("200px");

        form3.add(kafkaTopicIngotPickedUpField, kafkaTopicIngotMovedField, kafkaTopicIngotModifiedField,
            kafkaTopicShipmentCompletedField, kafkaTopicSawFeedbackField);
        layout.add(form3);

        layout.add(new Hr());

        // Hinweis
        Span hint = new Span("Hinweis: Aenderungen erfordern einen Neustart der Anwendung.");
        hint.getStyle().set("color", "gray").set("font-style", "italic");
        layout.add(hint);

        // Speichern Button
        layout.add(new Hr());
        layout.add(createSaveButton(() -> saveKafkaSettings()));

        return layout;
    }

    private VerticalLayout createMasterDataTab() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(true);

        // Produkte/Artikel
        Span section1 = new Span("Produkte / Artikel");
        section1.getStyle().set("font-weight", "bold").set("font-size", "16px");
        layout.add(section1);

        Span productInfo = new Span("Verwalten Sie die Artikel-Stammdaten (Produktnummern, Beschreibungen, Max. pro Lagerplatz)");
        productInfo.getStyle().set("color", "gray");
        layout.add(productInfo);

        Button productButton = new Button("Artikel verwalten", VaadinIcon.PACKAGE.create());
        productButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        productButton.addClickListener(e -> {
            ProductDialog dialog = new ProductDialog(productService);
            dialog.open();
        });
        layout.add(productButton);

        layout.add(new Hr());

        // Barrentypen
        Span section2 = new Span("Barrentypen");
        section2.getStyle().set("font-weight", "bold").set("font-size", "16px");
        layout.add(section2);

        Span ingotTypeInfo = new Span("Konfigurieren Sie Barrentypen mit Längenbereichen, Gewichtslimits und Lagerberechtigungen");
        ingotTypeInfo.getStyle().set("color", "gray");
        layout.add(ingotTypeInfo);

        Button ingotTypeButton = new Button("Barrentypen verwalten", VaadinIcon.CUBES.create());
        ingotTypeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        ingotTypeButton.addClickListener(e -> {
            IngotTypeDialog dialog = new IngotTypeDialog(ingotTypeService);
            dialog.open();
        });
        layout.add(ingotTypeButton);

        layout.add(new Hr());

        // Lagerplätze
        Span section3 = new Span("Lagerplätze");
        section3.getStyle().set("font-weight", "bold").set("font-size", "16px");
        layout.add(section3);

        Span stockyardInfo = new Span("Verwalten Sie alle Lagerplätze (Intern, Extern, SWAPOUT, Verladung). Filtern Sie nach Typ und bearbeiten Sie Koordinaten und Berechtigungen.");
        stockyardInfo.getStyle().set("color", "gray");
        layout.add(stockyardInfo);

        Button stockyardButton = new Button("Lagerplätze verwalten", VaadinIcon.STORAGE.create());
        stockyardButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        stockyardButton.addClickListener(e -> {
            StockyardManagementDialog dialog = new StockyardManagementDialog(stockyardService);
            dialog.open();
        });
        layout.add(stockyardButton);

        layout.add(new Hr());

        // Lieferscheine
        Span section4 = new Span("Lieferscheine");
        section4.getStyle().set("font-weight", "bold").set("font-size", "16px");
        layout.add(section4);

        Span shipmentInfo = new Span("Verwalten Sie Lieferscheine für ausgelieferte Barren von externen Lagerplätzen.");
        shipmentInfo.getStyle().set("color", "gray");
        layout.add(shipmentInfo);

        Button shipmentButton = new Button("Lieferscheine verwalten", VaadinIcon.CLIPBOARD_TEXT.create());
        shipmentButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        shipmentButton.addClickListener(e -> {
            LieferscheinDialog dialog = new LieferscheinDialog(shipmentService, ingotService, stockyardService, pdfService);
            dialog.open();
        });
        layout.add(shipmentButton);

        layout.add(new Hr());

        // Hinweis
        Span hint = new Span("Änderungen an Lagerplätzen werden sofort wirksam.");
        hint.getStyle().set("color", "gray").set("font-style", "italic");
        layout.add(hint);

        return layout;
    }

    private void saveKafkaSettings() {
        settingsService.setKafkaEnabled(kafkaEnabledCheckbox.getValue());
        settingsService.setKafkaBootstrapServers(kafkaBootstrapServersField.getValue());
        settingsService.setKafkaGroupId(kafkaGroupIdField.getValue());
        settingsService.setKafkaClientId(kafkaClientIdField.getValue());
        settingsService.setKafkaTopicCalloff(kafkaTopicCalloffField.getValue());
        settingsService.setKafkaTopicPickupOrder(kafkaTopicPickupOrderField.getValue());
        settingsService.setKafkaTopicProductRestriction(kafkaTopicProductRestrictionField.getValue());
        settingsService.setKafkaTopicIngotPickedUp(kafkaTopicIngotPickedUpField.getValue());
        settingsService.setKafkaTopicIngotMoved(kafkaTopicIngotMovedField.getValue());
        settingsService.setKafkaTopicIngotModified(kafkaTopicIngotModifiedField.getValue());
        settingsService.setKafkaTopicShipmentCompleted(kafkaTopicShipmentCompletedField.getValue());
        settingsService.setKafkaTopicSawFeedback(kafkaTopicSawFeedbackField.getValue());
        log.info("Kafka-Einstellungen gespeichert: enabled={}, servers={}",
            kafkaEnabledCheckbox.getValue(), kafkaBootstrapServersField.getValue());
    }
}
