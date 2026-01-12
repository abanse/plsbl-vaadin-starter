package com.hydro.plsbl.ui.view;

import com.hydro.plsbl.dto.CraneStatusDTO;
import com.hydro.plsbl.dto.IngotDTO;
import com.hydro.plsbl.dto.StockyardDTO;
import com.hydro.plsbl.plc.PlcService;
import com.hydro.plsbl.plc.dto.PlcCommand;
import com.hydro.plsbl.service.CraneStatusService;
import com.hydro.plsbl.service.IngotService;
import com.hydro.plsbl.service.SettingsService;
import com.hydro.plsbl.service.StockyardService;
import com.hydro.plsbl.simulator.CraneSimulatorCommand;
import com.hydro.plsbl.simulator.CraneSimulatorService;
import com.hydro.plsbl.ui.MainLayout;
import com.hydro.plsbl.ui.component.LagerGrid;
import com.hydro.plsbl.ui.dialog.IngotEditDialog;
import com.hydro.plsbl.ui.dialog.StockyardEditDialog;
import com.hydro.plsbl.ui.dialog.StockyardInfoDialog;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Lager-Ansicht (Stock View)
 * 
 * Zeigt das Lager-Grid mit allen Stockyards und deren Status.
 * Entspricht der StockView aus der JavaFX-Version.
 */
@Route(value = "lager", layout = MainLayout.class)
@RouteAlias(value = "", layout = MainLayout.class)  // Startseite
@PageTitle("Lager | PLSBL")
public class LagerView extends VerticalLayout {

    private static final Logger log = LoggerFactory.getLogger(LagerView.class);

    // Kran-Update-Intervall in Sekunden
    private static final int CRANE_UPDATE_INTERVAL = 1;

    private final StockyardService stockyardService;
    private final IngotService ingotService;
    private final CraneStatusService craneStatusService;
    private final CraneSimulatorService simulatorService;
    private final SettingsService settingsService;
    private final PlcService plcService;
    private LagerGrid lagerGrid;
    private TextField searchField;
    private Map<Long, StockyardDTO> allStockyards = new LinkedHashMap<>();

    // Kran-Polling
    private ScheduledExecutorService craneUpdateExecutor;
    private ScheduledFuture<?> craneUpdateFuture;

    // Simulator-Umlagern Modus
    private boolean relocateMode = false;
    private StockyardDTO relocateSource = null;
    private Button relocateButton;

    public LagerView(StockyardService stockyardService, IngotService ingotService,
                     CraneStatusService craneStatusService, CraneSimulatorService simulatorService,
                     SettingsService settingsService, PlcService plcService) {
        this.stockyardService = stockyardService;
        this.ingotService = ingotService;
        this.craneStatusService = craneStatusService;
        this.simulatorService = simulatorService;
        this.settingsService = settingsService;
        this.plcService = plcService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        createHeader();
        createLagerGrid();
        createLegend();

        loadData();
        loadCraneStatus();  // Initial laden
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        // Kran-Updates starten
        startCraneUpdates(attachEvent.getUI());
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        // Kran-Updates stoppen
        stopCraneUpdates();
        super.onDetach(detachEvent);
    }
    
    private void createHeader() {
        H3 title = new H3("Lagerübersicht");
        title.getStyle().set("margin", "0");

        Span info = new Span("Klicken Sie auf einen Lagerplatz für Details");
        info.getStyle().set("color", "gray");

        // Suchfeld
        searchField = new TextField();
        searchField.setPlaceholder("Suche (Platz, Produkt...)");
        searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchField.setClearButtonVisible(true);
        searchField.setWidth("200px");
        searchField.setValueChangeMode(ValueChangeMode.LAZY);
        searchField.addValueChangeListener(e -> filterStockyards(e.getValue()));

        // Spacer
        Span spacer = new Span();
        spacer.getStyle().set("flex-grow", "1");

        // Aktualisieren-Button
        Button refreshButton = new Button("Aktualisieren", VaadinIcon.REFRESH.create());
        refreshButton.addClickListener(e -> {
            searchField.clear();
            loadData();
            com.vaadin.flow.component.notification.Notification
                .show("Daten aktualisiert", 2000, com.vaadin.flow.component.notification.Notification.Position.BOTTOM_CENTER);
        });

        // Neu-Button
        Button newButton = new Button("Neuer Lagerplatz", VaadinIcon.PLUS.create());
        newButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        newButton.addClickListener(e -> openNewStockyardDialog());

        // Kran-Umlagern Button (funktioniert mit Simulator oder echter SPS)
        relocateButton = new Button("Umlagern", VaadinIcon.ARROWS_LONG_H.create());
        relocateButton.addClickListener(e -> toggleRelocateMode());
        boolean craneAvailable = plcService.isConnected() || plcService.isSimulatorMode();
        relocateButton.setEnabled(craneAvailable);

        HorizontalLayout header = new HorizontalLayout(title, info, spacer, searchField, refreshButton, relocateButton, newButton);
        header.setAlignItems(Alignment.BASELINE);
        header.setWidthFull();

        add(header);
    }

    private void filterStockyards(String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            // Alle anzeigen
            lagerGrid.setStockyards(allStockyards);
            return;
        }

        String search = searchText.toLowerCase().trim();

        // Filtern nach Platznummer oder Produkt
        Map<Long, StockyardDTO> filtered = new LinkedHashMap<>();
        for (Map.Entry<Long, StockyardDTO> entry : allStockyards.entrySet()) {
            StockyardDTO yard = entry.getValue();

            boolean matches = false;

            // Platznummer prüfen
            if (yard.getYardNumber() != null && yard.getYardNumber().toLowerCase().contains(search)) {
                matches = true;
            }

            // Produkt-Nummer prüfen (wenn Status vorhanden)
            if (!matches && yard.getStatus() != null && yard.getStatus().getProductNumber() != null) {
                if (yard.getStatus().getProductNumber().toLowerCase().contains(search)) {
                    matches = true;
                }
            }

            // Koordinaten prüfen (z.B. "15/08" oder "15,08")
            if (!matches) {
                String coords = yard.getXCoordinate() + "/" + yard.getYCoordinate();
                String coords2 = yard.getXCoordinate() + "," + yard.getYCoordinate();
                if (coords.contains(search) || coords2.contains(search)) {
                    matches = true;
                }
            }

            if (matches) {
                filtered.put(entry.getKey(), yard);
            }
        }

        lagerGrid.setStockyards(filtered);

        // Info anzeigen
        if (filtered.isEmpty()) {
            com.vaadin.flow.component.notification.Notification
                .show("Keine Treffer für '" + searchText + "'", 2000,
                    com.vaadin.flow.component.notification.Notification.Position.BOTTOM_CENTER);
        }
    }

    private void openNewStockyardDialog() {
        StockyardEditDialog dialog = new StockyardEditDialog(null, true);
        dialog.setOnSave(dto -> {
            try {
                stockyardService.save(dto);
                loadData();
                log.info("New stockyard created: {}", dto.getYardNumber());

                com.vaadin.flow.component.notification.Notification
                    .show("Lagerplatz " + dto.getYardNumber() + " wurde erstellt",
                        3000, com.vaadin.flow.component.notification.Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_SUCCESS);
            } catch (Exception e) {
                log.error("Error creating stockyard", e);
                com.vaadin.flow.component.notification.Notification
                    .show("Fehler beim Erstellen: " + e.getMessage(),
                        5000, com.vaadin.flow.component.notification.Notification.Position.MIDDLE)
                    .addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_ERROR);
            }
        });
        dialog.open();
    }
    
    private void createLagerGrid() {
        lagerGrid = new LagerGrid(settingsService);
        lagerGrid.setSizeFull();

        // Click-Handler für Stockyards
        lagerGrid.addStockyardClickListener(this::onStockyardClicked);

        // Container mit Scroll
        Div container = new Div(lagerGrid);
        container.setSizeFull();
        container.getStyle()
            .set("overflow", "auto")
            .set("border", "1px solid #e0e0e0")
            .set("border-radius", "4px")
            .set("background-color", "#fafafa");
        
        add(container);
        setFlexGrow(1, container);
    }
    
    private void createLegend() {
        HorizontalLayout legend = new HorizontalLayout();
        legend.setSpacing(true);
        legend.getStyle().set("flex-wrap", "wrap");

        // Farben aus SettingsService
        String colorEmpty = settingsService.getColorYardEmpty();
        String colorInUse = settingsService.getColorYardInUse();
        String colorFull = settingsService.getColorYardFull();
        String colorNoSwapIn = settingsService.getColorYardNoSwapIn();
        String colorNoSwapOut = settingsService.getColorYardNoSwapOut();
        String colorNoSwapInOut = settingsService.getColorYardNoSwapInOut();

        legend.add(createLegendItem(colorEmpty, "Leer"));
        legend.add(createLegendItem(colorInUse, "Teilweise belegt"));
        legend.add(createLegendItem(colorFull, "Voll"));
        legend.add(createLegendItem(colorInUse, "Einlagern gesperrt", colorNoSwapIn));
        legend.add(createLegendItem(colorInUse, "Auslagern gesperrt", colorNoSwapOut));
        legend.add(createLegendItem(colorInUse, "Komplett gesperrt", colorNoSwapInOut));

        add(legend);
    }
    
    private Div createLegendItem(String bgColor, String text) {
        return createLegendItem(bgColor, text, null);
    }
    
    private Div createLegendItem(String bgColor, String text, String borderColor) {
        Div colorBox = new Div();
        colorBox.getStyle()
            .set("width", "20px")
            .set("height", "20px")
            .set("background-color", bgColor)
            .set("border-radius", "3px");
        
        if (borderColor != null) {
            colorBox.getStyle().set("border", "2px solid " + borderColor);
        }
        
        Span label = new Span(text);
        label.getStyle()
            .set("font-size", "12px")
            .set("color", "#666");
        
        HorizontalLayout item = new HorizontalLayout(colorBox, label);
        item.setAlignItems(Alignment.CENTER);
        item.setSpacing(true);
        item.getStyle().set("margin-right", "15px");
        
        Div wrapper = new Div(item);
        return wrapper;
    }
    
    private void loadData() {
        log.info("Loading stockyard data...");

        try {
            allStockyards = stockyardService.findAllForStockView();
            log.info("Loaded {} stockyards", allStockyards.size());

            lagerGrid.setStockyards(allStockyards);
        } catch (Exception e) {
            log.error("Error loading stockyards", e);

            // Fehler anzeigen
            Span error = new Span("Fehler beim Laden der Daten: " + e.getMessage());
            error.getStyle().set("color", "red");
            add(error);
        }
    }
    
    private void onStockyardClicked(StockyardDTO stockyard) {
        log.debug("Stockyard clicked: {}", stockyard.getYardNumber());

        // Wenn im Umlagern-Modus
        if (relocateMode) {
            handleRelocateClick(stockyard);
            return;
        }

        // Info-Dialog öffnen mit IngotService, StockyardService und PlcService
        StockyardInfoDialog dialog = new StockyardInfoDialog(stockyard, ingotService, stockyardService, plcService);
        dialog.setOnEdit(this::openEditDialog);
        dialog.setOnDelete(this::deleteStockyard);
        dialog.setOnForceDelete(this::forceDeleteStockyard);
        dialog.setOnIngotEdit(this::openIngotEditDialog);
        dialog.setOnRelocated(v -> loadData()); // Nach Umlagern Grid aktualisieren
        dialog.open();
    }

    /**
     * Schaltet den Umlagern-Modus um
     */
    private void toggleRelocateMode() {
        boolean craneAvailable = plcService.isConnected() || plcService.isSimulatorMode();
        if (!craneAvailable) {
            com.vaadin.flow.component.notification.Notification
                .show("Kran nicht verfügbar! Bitte SPS oder Simulator aktivieren.", 3000,
                    com.vaadin.flow.component.notification.Notification.Position.MIDDLE);
            return;
        }

        relocateMode = !relocateMode;
        relocateSource = null;

        if (relocateMode) {
            relocateButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            relocateButton.setText("Umlagern: Quelle wählen");
            String modeText = plcService.isSimulatorMode() ? "(Simulator)" : "(SPS)";
            com.vaadin.flow.component.notification.Notification
                .show("Umlagern-Modus " + modeText + ": Klicken Sie auf den Quell-Lagerplatz", 3000,
                    com.vaadin.flow.component.notification.Notification.Position.BOTTOM_CENTER);
        } else {
            relocateButton.removeThemeVariants(ButtonVariant.LUMO_PRIMARY);
            relocateButton.setText("Umlagern");
            com.vaadin.flow.component.notification.Notification
                .show("Umlagern-Modus beendet", 2000,
                    com.vaadin.flow.component.notification.Notification.Position.BOTTOM_CENTER);
        }
    }

    /**
     * Verarbeitet Klicks im Umlagern-Modus
     */
    private void handleRelocateClick(StockyardDTO stockyard) {
        if (relocateSource == null) {
            // Erster Klick - Quelle auswählen
            if (stockyard.getStatus() == null || stockyard.getStatus().getIngotsCount() == 0) {
                com.vaadin.flow.component.notification.Notification
                    .show("Lagerplatz " + stockyard.getYardNumber() + " ist leer!", 3000,
                        com.vaadin.flow.component.notification.Notification.Position.MIDDLE);
                return;
            }

            relocateSource = stockyard;
            relocateButton.setText("Umlagern: Ziel wählen (" + stockyard.getYardNumber() + ")");
            com.vaadin.flow.component.notification.Notification
                .show("Quelle: " + stockyard.getYardNumber() + " - Jetzt Ziel wählen", 3000,
                    com.vaadin.flow.component.notification.Notification.Position.BOTTOM_CENTER);

        } else {
            // Zweiter Klick - Ziel auswählen und Kommando senden
            if (stockyard.getId().equals(relocateSource.getId())) {
                com.vaadin.flow.component.notification.Notification
                    .show("Quelle und Ziel dürfen nicht gleich sein!", 3000,
                        com.vaadin.flow.component.notification.Notification.Position.MIDDLE);
                return;
            }

            // Simulator-Kommando erstellen und senden
            sendSimulatorRelocateCommand(relocateSource, stockyard);

            // Modus beenden
            relocateMode = false;
            relocateSource = null;
            relocateButton.removeThemeVariants(ButtonVariant.LUMO_PRIMARY);
            relocateButton.setText("Umlagern (Sim)");
        }
    }

    /**
     * Sendet einen Umlager-Befehl an den Kran (SPS oder Simulator)
     */
    private void sendSimulatorRelocateCommand(StockyardDTO source, StockyardDTO destination) {
        String modeText = plcService.isSimulatorMode() ? "Simulator" : "SPS";
        log.info("{} relocate: {} -> {}", modeText, source.getYardNumber(), destination.getYardNumber());

        // Positionen in mm - bevorzuge gespeicherte Position, sonst Grid-Umrechnung
        int pickupX = source.getXPosition() > 0 ? source.getXPosition() : gridToMmX(source.getXCoordinate());
        int pickupY = source.getYPosition() > 0 ? source.getYPosition() : gridToMmY(source.getYCoordinate());
        int pickupZ = source.getZPosition() > 0 ? source.getZPosition() : 2000;

        int releaseX = destination.getXPosition() > 0 ? destination.getXPosition() : gridToMmX(destination.getXCoordinate());
        int releaseY = destination.getYPosition() > 0 ? destination.getYPosition() : gridToMmY(destination.getYCoordinate());
        int releaseZ = destination.getZPosition() > 0 ? destination.getZPosition() : 2000;

        // PlcCommand erstellen
        PlcCommand cmd = PlcCommand.builder()
            .pickupPosition(pickupX, pickupY, pickupZ)
            .releasePosition(releaseX, releaseY, releaseZ)
            .dimensions(5000, 500, 200)  // Default Barren-Maße (L x B x H)
            .weight(1500)
            .longIngot(false)
            .rotate(false)
            .build();

        try {
            // An PlcService senden (verteilt an SPS oder Simulator)
            plcService.sendCommand(cmd);

            com.vaadin.flow.component.notification.Notification
                .show(String.format("Umlagern gestartet (%s): %s → %s",
                    modeText, source.getYardNumber(), destination.getYardNumber()),
                    5000, com.vaadin.flow.component.notification.Notification.Position.BOTTOM_CENTER)
                .addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_SUCCESS);
        } catch (Exception e) {
            log.error("Fehler beim Senden des Umlagern-Befehls", e);
            com.vaadin.flow.component.notification.Notification
                .show("Fehler: " + e.getMessage(), 5000,
                    com.vaadin.flow.component.notification.Notification.Position.MIDDLE)
                .addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_ERROR);
        }
    }

    private void deleteStockyard(StockyardDTO stockyard) {
        try {
            stockyardService.delete(stockyard.getId());
            loadData(); // Grid aktualisieren
            log.info("Stockyard deleted: {}", stockyard.getYardNumber());

            com.vaadin.flow.component.notification.Notification
                .show("Lagerplatz " + stockyard.getYardNumber() + " wurde gelöscht",
                    3000, com.vaadin.flow.component.notification.Notification.Position.BOTTOM_CENTER)
                .addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_SUCCESS);
        } catch (Exception e) {
            log.error("Error deleting stockyard", e);
            com.vaadin.flow.component.notification.Notification
                .show("Fehler beim Löschen: " + e.getMessage(),
                    5000, com.vaadin.flow.component.notification.Notification.Position.MIDDLE)
                .addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_ERROR);
        }
    }

    private void forceDeleteStockyard(StockyardDTO stockyard) {
        try {
            stockyardService.forceDelete(stockyard.getId());
            loadData(); // Grid aktualisieren
            log.info("Stockyard force deleted: {}", stockyard.getYardNumber());

            com.vaadin.flow.component.notification.Notification
                .show("Lagerplatz " + stockyard.getYardNumber() + " wurde forciert gelöscht",
                    3000, com.vaadin.flow.component.notification.Notification.Position.BOTTOM_CENTER)
                .addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_SUCCESS);
        } catch (Exception e) {
            log.error("Error force deleting stockyard", e);
            com.vaadin.flow.component.notification.Notification
                .show("Fehler beim Löschen: " + e.getMessage(),
                    5000, com.vaadin.flow.component.notification.Notification.Position.MIDDLE)
                .addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_ERROR);
        }
    }

    private void openIngotEditDialog(IngotDTO ingot) {
        log.debug("Opening ingot edit dialog for: {}", ingot.getIngotNo());
        IngotEditDialog editDialog = new IngotEditDialog(ingot, false, stockyardService);
        editDialog.setOnSave(dto -> {
            try {
                ingotService.save(dto);
                loadData();
                log.info("Ingot saved: {}", dto.getIngotNo());
            } catch (Exception e) {
                log.error("Error saving ingot", e);
            }
        });
        editDialog.open();
    }

    private void openEditDialog(StockyardDTO stockyard) {
        StockyardEditDialog editDialog = new StockyardEditDialog(stockyard, false);
        editDialog.setOnSave(dto -> {
            try {
                stockyardService.save(dto);
                loadData();
                log.info("Stockyard saved: {}", dto.getYardNumber());
            } catch (Exception e) {
                log.error("Error saving stockyard", e);
            }
        });
        editDialog.open();
    }

    // ========================================================================
    // Kran-Methoden
    // ========================================================================

    /**
     * Startet die periodischen Kran-Updates
     */
    private void startCraneUpdates(UI ui) {
        if (craneUpdateExecutor != null) {
            return;  // Bereits gestartet
        }

        craneUpdateExecutor = Executors.newSingleThreadScheduledExecutor();
        craneUpdateFuture = craneUpdateExecutor.scheduleAtFixedRate(() -> {
            try {
                ui.access(this::loadCraneStatus);
            } catch (Exception e) {
                log.debug("Crane update skipped: {}", e.getMessage());
            }
        }, CRANE_UPDATE_INTERVAL, CRANE_UPDATE_INTERVAL, TimeUnit.SECONDS);

        log.info("Crane updates started (interval: {}s)", CRANE_UPDATE_INTERVAL);
    }

    /**
     * Stoppt die periodischen Kran-Updates
     */
    private void stopCraneUpdates() {
        if (craneUpdateFuture != null) {
            craneUpdateFuture.cancel(true);
            craneUpdateFuture = null;
        }
        if (craneUpdateExecutor != null) {
            craneUpdateExecutor.shutdownNow();
            craneUpdateExecutor = null;
        }
        log.info("Crane updates stopped");
    }

    /**
     * Lädt den aktuellen Kran-Status und aktualisiert die Anzeige.
     * Verwendet PlcService (SPS oder Simulator) oder die Datenbank als Fallback.
     */
    private void loadCraneStatus() {
        try {
            boolean craneAvailable = plcService.isConnected() || plcService.isSimulatorMode();

            // Button-Status aktualisieren
            if (relocateButton != null && !relocateMode) {
                relocateButton.setEnabled(craneAvailable);
            }

            // Wenn PlcService im Simulator-Modus, Simulator-Status verwenden
            if (plcService.isSimulatorMode()) {
                updateCraneFromSimulator();
            } else if (plcService.isConnected()) {
                // Echte SPS - Status vom PlcService holen
                var plcStatus = plcService.getCurrentStatus();
                if (plcStatus != null) {
                    updateCraneFromPlcStatus(plcStatus);
                }
            } else {
                // Fallback: Datenbank-Status verwenden
                Optional<CraneStatusDTO> statusOpt = craneStatusService.getCurrentStatus();
                if (statusOpt.isPresent()) {
                    updateCraneDisplay(statusOpt.get());
                } else {
                    log.debug("No crane status available");
                }
            }
        } catch (Exception e) {
            log.debug("Error loading crane status: {}", e.getMessage());
        }
    }

    /**
     * Aktualisiert die Kran-Anzeige aus dem PlcStatus
     */
    private void updateCraneFromPlcStatus(com.hydro.plsbl.plc.dto.PlcStatus plcStatus) {
        if (lagerGrid == null) return;

        int xMm = plcStatus.getXPosition();
        int yMm = plcStatus.getYPosition();
        int zMm = plcStatus.getZPosition();

        // Status-Display aktualisieren
        var posDisplay = lagerGrid.getCranePositionDisplay();
        if (posDisplay != null) {
            posDisplay.updateStatus(
                xMm, yMm, zMm,
                plcStatus.getCraneMode() != null ? plcStatus.getCraneMode().getDisplayName() : "?",
                plcStatus.getJobState() != null ? plcStatus.getJobState().getDisplayName() : "?",
                plcStatus.getGripperState() != null ? plcStatus.getGripperState().getDisplayName() : "?",
                null, null, null, null, null
            );
        }

        // Greifer-Höhe setzen
        var craneOverlay = lagerGrid.getCraneOverlay();
        if (craneOverlay != null) {
            int gripperHeight = Math.max(0, Math.min(100, zMm / 100));
            craneOverlay.setGripperHeight(gripperHeight);

            // Barren anzeigen wenn geladen
            if (plcStatus.getGripperState() == com.hydro.plsbl.plc.dto.GripperState.LOADED) {
                craneOverlay.setIngot("PLC", "SPS-Barren", 40, 30);
            } else {
                craneOverlay.clearIngot();
            }
        }

        // mm zu Grid-Koordinaten konvertieren
        int gridX = mmToGridX(xMm);
        int gridY = mmToGridY(yMm);

        // Grid-Position setzen (ohne Begrenzung, erlaubt Positionen außerhalb für Säge etc.)
        lagerGrid.setCraneGridPosition(gridX, gridY);

        log.debug("Crane (PLC): Phase={}, Pos=({},{},{})mm -> Grid({},{})",
                plcStatus.getWorkPhase(), xMm, yMm, zMm, gridX, gridY);
    }

    /**
     * Aktualisiert die Kran-Anzeige aus dem Simulator
     */
    private void updateCraneFromSimulator() {
        CraneSimulatorService.SimulatorStatus simStatus = simulatorService.getSimulatorStatus();

        if (lagerGrid == null) return;

        int xMm = simStatus.xPosition();
        int yMm = simStatus.yPosition();
        int zMm = simStatus.zPosition();

        // Status-Display aktualisieren
        var posDisplay = lagerGrid.getCranePositionDisplay();
        if (posDisplay != null) {
            posDisplay.updateStatus(
                xMm, yMm, zMm,
                simStatus.craneMode().name(),
                simStatus.jobState().name(),
                simStatus.gripperState().name(),
                null, // fromStockyardNo
                null, // toStockyardNo
                null, // ingotNo
                null, // ingotProductNo
                null  // incident
            );
        }

        // Greifer-Höhe setzen
        var craneOverlay = lagerGrid.getCraneOverlay();
        if (craneOverlay != null) {
            int gripperHeight = Math.max(0, Math.min(100, zMm / 100));
            craneOverlay.setGripperHeight(gripperHeight);

            // Barren anzeigen wenn geladen
            if (simStatus.gripperState() == com.hydro.plsbl.simulator.GripperState.LOADED) {
                craneOverlay.setIngot("SIM", "Simulator", 40, 30);
            } else {
                craneOverlay.clearIngot();
            }
        }

        // mm zu Grid-Koordinaten konvertieren
        int gridX = mmToGridX(xMm);
        int gridY = mmToGridY(yMm);

        // Grid-Position setzen (ohne Begrenzung, erlaubt Positionen außerhalb für Säge etc.)
        lagerGrid.setCraneGridPosition(gridX, gridY);

        log.debug("Crane (Simulator): Phase={}, Pos=({},{},{})mm -> Grid({},{})",
                simStatus.workPhase(), xMm, yMm, zMm, gridX, gridY);
    }

    /**
     * Aktualisiert die Kran-Anzeige mit den neuen Daten
     */
    private void updateCraneDisplay(CraneStatusDTO status) {
        if (lagerGrid == null) return;

        // Position in mm
        int xMm = status.getXPosition() != null ? status.getXPosition() : 0;
        int yMm = status.getYPosition() != null ? status.getYPosition() : 0;
        int zMm = status.getZPosition() != null ? status.getZPosition() : 0;

        // Alle Status-Daten zur Anzeige setzen
        var posDisplay = lagerGrid.getCranePositionDisplay();
        if (posDisplay != null) {
            posDisplay.updateStatus(
                xMm, yMm, zMm,
                status.getCraneMode(),
                status.getJobState(),
                status.getGripperState(),
                status.getFromStockyardNo(),
                status.getToStockyardNo(),
                status.getIngotNo(),
                status.getIngotProductNo(),
                status.getIncident()
            );
        }

        // Greifer-Höhe und Barren am Kran-Overlay setzen
        var craneOverlay = lagerGrid.getCraneOverlay();
        if (craneOverlay != null) {
            int gripperHeight = Math.max(0, Math.min(100, zMm / 100));
            craneOverlay.setGripperHeight(gripperHeight);

            // Barren anzeigen wenn Greifer einen hält
            if (status.hasIngot()) {
                int length = status.getIngotLength() != null ? status.getIngotLength() / 50 : 40;
                int width = status.getIngotWidth() != null ? status.getIngotWidth() / 50 : 30;
                craneOverlay.setIngot(status.getIngotNo(), status.getIngotProductNo(), length, width);
            } else {
                craneOverlay.clearIngot();
            }
        }

        // mm zu Grid-Koordinaten konvertieren
        int gridX = mmToGridX(xMm);
        int gridY = mmToGridY(yMm);

        // Grid-Position setzen (ohne Begrenzung, erlaubt Positionen außerhalb für Säge etc.)
        lagerGrid.setCraneGridPosition(gridX, gridY);

        log.debug("Crane updated: Mode={}, State={}, Pos=({},{},{})mm -> Grid({},{})",
                status.getCraneMode(), status.getJobState(),
                xMm, yMm, zMm, gridX, gridY);
    }

    /**
     * Konvertiert mm-X-Position zu Grid-X-Koordinate (via SettingsService)
     */
    private int mmToGridX(int mmX) {
        return settingsService.mmToGridX(mmX);
    }

    /**
     * Konvertiert mm-Y-Position zu Grid-Y-Koordinate (via SettingsService)
     */
    private int mmToGridY(int mmY) {
        return settingsService.mmToGridY(mmY);
    }

    /**
     * Konvertiert Grid-X-Koordinate zu mm-Position (via SettingsService)
     */
    private int gridToMmX(int gridX) {
        return settingsService.gridToMmX(gridX);
    }

    /**
     * Konvertiert Grid-Y-Koordinate zu mm-Position (via SettingsService)
     */
    private int gridToMmY(int gridY) {
        return settingsService.gridToMmY(gridY);
    }
}
