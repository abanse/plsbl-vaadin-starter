package com.hydro.plsbl.ui.dialog;

import com.hydro.plsbl.dto.IngotDTO;
import com.hydro.plsbl.dto.StockyardDTO;
import com.hydro.plsbl.plc.PlcException;
import com.hydro.plsbl.plc.PlcService;
import com.hydro.plsbl.plc.dto.PlcCommand;
import com.hydro.plsbl.service.IngotService;
import com.hydro.plsbl.service.StockyardService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Dialog zum Umlagern von Barren zwischen Lagerplätzen
 */
public class UmlagernDialog extends Dialog {

    private static final String MODE_CRANE = "Mit Kran (SPS/Simulator)";
    private static final String MODE_DATABASE = "Nur Datenbank";

    private final StockyardDTO sourceStockyard;
    private final IngotService ingotService;
    private final StockyardService stockyardService;
    private final PlcService plcService;

    private Grid<IngotDTO> ingotGrid;
    private ComboBox<StockyardDTO> destinationComboBox;
    private RadioButtonGroup<String> modeSelector;
    private Set<IngotDTO> selectedIngots = new HashSet<>();
    private IngotDTO topIngot; // Oberster Barren der umgelagert wird
    private Span selectedCountLabel;
    private Span modeInfoLabel;
    private Button relocateButton;

    private Consumer<Void> onRelocated;

    public UmlagernDialog(StockyardDTO sourceStockyard,
                          IngotService ingotService,
                          StockyardService stockyardService) {
        this(sourceStockyard, ingotService, stockyardService, null);
    }

    public UmlagernDialog(StockyardDTO sourceStockyard,
                          IngotService ingotService,
                          StockyardService stockyardService,
                          PlcService plcService) {
        this.sourceStockyard = sourceStockyard;
        this.ingotService = ingotService;
        this.stockyardService = stockyardService;
        this.plcService = plcService;

        setHeaderTitle("Umlagern von " + sourceStockyard.getYardNumber());
        setWidth("700px");
        setHeight("620px");
        setModal(true);
        setDraggable(true);
        setCloseOnOutsideClick(false);

        createContent();
        createFooter();
    }

    private void createContent() {
        VerticalLayout content = new VerticalLayout();
        content.setPadding(true);
        content.setSpacing(true);

        // Quelle
        content.add(new H4("Quelle: " + sourceStockyard.getYardNumber()));

        // Barren auf diesem Platz anzeigen
        Span infoSpan = new Span("Der Kran nimmt immer den obersten Barren (nur dieser kann umgelagert werden):");
        infoSpan.getStyle().set("font-style", "italic");
        content.add(infoSpan);
        content.add(createIngotGrid());

        // Info welcher Barren umgelagert wird
        selectedCountLabel = new Span();
        selectedCountLabel.getStyle().set("font-weight", "bold");
        updateTopIngotLabel();
        content.add(selectedCountLabel);

        content.add(new Hr());

        // Ziel-Auswahl
        content.add(new H4("Ziel auswählen"));
        content.add(createDestinationComboBox());

        content.add(new Hr());

        // Modus-Auswahl (Kran oder nur Datenbank)
        content.add(new H4("Umlagern-Modus"));
        content.add(createModeSelector());

        add(content);
    }

    private VerticalLayout createModeSelector() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(true);

        modeSelector = new RadioButtonGroup<>();
        modeSelector.setItems(MODE_CRANE, MODE_DATABASE);

        // Standard: Kran wenn PlcService verfügbar und verbunden/Simulator aktiv
        boolean craneAvailable = plcService != null &&
            (plcService.isConnected() || plcService.isSimulatorMode());

        if (craneAvailable) {
            modeSelector.setValue(MODE_CRANE);
        } else {
            modeSelector.setValue(MODE_DATABASE);
            modeSelector.setItemEnabledProvider(item ->
                !MODE_CRANE.equals(item) || craneAvailable);
        }

        modeSelector.addValueChangeListener(e -> updateModeInfo());

        // Info-Label
        modeInfoLabel = new Span();
        modeInfoLabel.getStyle().set("font-size", "12px");
        updateModeInfo();

        layout.add(modeSelector, modeInfoLabel);
        return layout;
    }

    private void updateModeInfo() {
        if (modeSelector == null || modeInfoLabel == null) return;

        String mode = modeSelector.getValue();
        if (MODE_CRANE.equals(mode)) {
            if (plcService != null && plcService.isSimulatorMode()) {
                modeInfoLabel.setText("Der Kran-Simulator bewegt den Barren physisch.");
                modeInfoLabel.getStyle().set("color", "#FF9800");
            } else if (plcService != null && plcService.isConnected()) {
                modeInfoLabel.setText("Der echte Kran bewegt den Barren physisch.");
                modeInfoLabel.getStyle().set("color", "#4CAF50");
            } else {
                modeInfoLabel.setText("Kran nicht verfügbar - bitte Simulator oder SPS aktivieren.");
                modeInfoLabel.getStyle().set("color", "#F44336");
            }
        } else {
            modeInfoLabel.setText("Nur Datenbank-Änderung, kein Kran-Befehl.");
            modeInfoLabel.getStyle().set("color", "gray");
        }
    }

    private Grid<IngotDTO> createIngotGrid() {
        ingotGrid = new Grid<>();
        ingotGrid.setHeight("150px");
        ingotGrid.setWidthFull();
        ingotGrid.addThemeVariants(GridVariant.LUMO_COMPACT);
        ingotGrid.setSelectionMode(Grid.SelectionMode.NONE); // Keine Auswahl - nur Anzeige

        // Alle Barren laden und nach Position sortieren (höchste zuerst)
        List<IngotDTO> ingots = ingotService.findByStockyardId(sourceStockyard.getId());
        ingots.sort((a, b) -> {
            int posA = a.getPilePosition() != null ? a.getPilePosition() : 0;
            int posB = b.getPilePosition() != null ? b.getPilePosition() : 0;
            return Integer.compare(posB, posA); // Absteigend
        });

        // Nur obersten Barren als "ausgewählt" markieren
        if (!ingots.isEmpty()) {
            topIngot = ingots.get(0); // Oberster Barren (höchste Position)
            selectedIngots.add(topIngot);
        }

        // Spalten
        // Status-Spalte: zeigt an welcher Barren genommen wird
        ingotGrid.addComponentColumn(ingot -> {
            if (topIngot != null && ingot.getId().equals(topIngot.getId())) {
                Span badge = new Span("⬆ OBEN");
                badge.getStyle()
                    .set("background-color", "#4CAF50")
                    .set("color", "white")
                    .set("padding", "2px 6px")
                    .set("border-radius", "4px")
                    .set("font-size", "10px")
                    .set("font-weight", "bold");
                return badge;
            } else {
                Span badge = new Span("gesperrt");
                badge.getStyle()
                    .set("color", "#9E9E9E")
                    .set("font-size", "10px");
                return badge;
            }
        }).setHeader("")
          .setWidth("70px")
          .setFlexGrow(0);

        ingotGrid.addColumn(IngotDTO::getPilePosition)
            .setHeader("Pos")
            .setWidth("50px")
            .setFlexGrow(0);

        ingotGrid.addColumn(IngotDTO::getIngotNo)
            .setHeader("Barren-Nr")
            .setAutoWidth(true)
            .setFlexGrow(1);

        ingotGrid.addColumn(IngotDTO::getProductNo)
            .setHeader("Produkt")
            .setAutoWidth(true)
            .setFlexGrow(0);

        ingotGrid.addColumn(ingot -> ingot.getWeight() != null ? ingot.getWeight() + " kg" : "-")
            .setHeader("Gewicht")
            .setAutoWidth(true)
            .setFlexGrow(0);

        ingotGrid.setItems(ingots);

        // Row-Styling: Oberster Barren grün, andere ausgegraut
        ingotGrid.setClassNameGenerator(ingot -> {
            if (topIngot != null && ingot.getId().equals(topIngot.getId())) {
                return "top-ingot-row";
            } else {
                return "blocked-ingot-row";
            }
        });

        // CSS für Zeilen-Styling hinzufügen
        ingotGrid.getElement().executeJs(
            "const style = document.createElement('style');" +
            "style.textContent = `" +
            "  vaadin-grid::part(row) {}" +
            "  vaadin-grid tr.top-ingot-row { background-color: #E8F5E9 !important; }" +
            "  vaadin-grid tr.blocked-ingot-row { background-color: #F5F5F5 !important; color: #9E9E9E !important; }" +
            "`;" +
            "this.shadowRoot.appendChild(style);"
        );

        return ingotGrid;
    }

    private ComboBox<StockyardDTO> createDestinationComboBox() {
        destinationComboBox = new ComboBox<>("Ziel-Lagerplatz");
        destinationComboBox.setWidthFull();
        destinationComboBox.setPlaceholder("Lagerplatz auswählen...");
        destinationComboBox.setItemLabelGenerator(this::getStockyardLabel);
        destinationComboBox.setClearButtonVisible(true);

        // Verfügbare Ziele laden (ohne Quelle)
        try {
            List<StockyardDTO> destinations = stockyardService.findAvailableDestinations();
            // Quelle ausfiltern
            destinations.removeIf(d -> d.getId().equals(sourceStockyard.getId()));
            destinationComboBox.setItems(destinations);

            if (destinations.isEmpty()) {
                Notification.show("Keine verfügbaren Ziel-Lagerplätze gefunden",
                    3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_WARNING);
            }
        } catch (Exception e) {
            Notification.show("Fehler beim Laden der Ziele: " + e.getMessage(),
                5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            e.printStackTrace();
        }

        destinationComboBox.addValueChangeListener(e -> updateRelocateButton());

        return destinationComboBox;
    }

    private String getStockyardLabel(StockyardDTO yard) {
        StringBuilder sb = new StringBuilder();
        sb.append(yard.getYardNumber());

        if (yard.getStatus() != null) {
            sb.append(" (").append(yard.getStatus().getIngotsCount());
            sb.append("/").append(yard.getMaxIngots()).append(")");
        } else {
            sb.append(" (leer)");
        }

        return sb.toString();
    }

    private void updateTopIngotLabel() {
        if (topIngot != null) {
            selectedCountLabel.setText("Wird umgelagert: " + topIngot.getIngotNo() +
                " (Position " + topIngot.getPilePosition() + " - oberster Barren)");
            selectedCountLabel.getStyle().set("color", "#1565c0");
        } else {
            selectedCountLabel.setText("Keine Barren zum Umlagern vorhanden");
            selectedCountLabel.getStyle().set("color", "gray");
        }
    }

    private void updateSelectedCount() {
        updateTopIngotLabel();
        updateRelocateButton();
    }

    private void updateRelocateButton() {
        // Null-Checks für Komponenten die noch nicht erstellt wurden
        if (relocateButton == null || destinationComboBox == null) {
            return;
        }

        boolean canRelocate = topIngot != null && destinationComboBox.getValue() != null;

        // Prüfen ob genug Platz am Ziel (nur 1 Barren wird umgelagert)
        if (canRelocate) {
            StockyardDTO dest = destinationComboBox.getValue();
            int currentCount = dest.getStatus() != null ? dest.getStatus().getIngotsCount() : 0;
            int available = dest.getMaxIngots() - currentCount;

            if (available < 1) {
                canRelocate = false;
                selectedCountLabel.setText("Ziel-Lagerplatz ist voll!");
                selectedCountLabel.getStyle().set("color", "red");
            } else {
                updateTopIngotLabel();
            }
        }

        relocateButton.setEnabled(canRelocate);
    }

    private void createFooter() {
        relocateButton = new Button("Umlagern", VaadinIcon.EXCHANGE.create());
        relocateButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        relocateButton.setEnabled(false);
        relocateButton.addClickListener(e -> executeRelocation());

        Button cancelButton = new Button("Abbrechen", VaadinIcon.CLOSE.create());
        cancelButton.addClickListener(e -> close());

        HorizontalLayout footer = new HorizontalLayout(cancelButton, relocateButton);
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        footer.setWidthFull();
        getFooter().add(footer);
    }

    private void executeRelocation() {
        StockyardDTO destination = destinationComboBox.getValue();

        if (topIngot == null) {
            Notification.show("Kein Barren zum Umlagern", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_WARNING);
            return;
        }

        if (destination == null) {
            Notification.show("Kein Ziel ausgewählt", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_WARNING);
            return;
        }

        String mode = modeSelector.getValue();

        try {
            if (MODE_CRANE.equals(mode) && plcService != null) {
                // Kran-Umlagern: Befehl an SPS/Simulator senden
                executeCraneRelocation(destination);
            } else {
                // Nur Datenbank-Umlagern
                executeDatabaseRelocation(destination);
            }
        } catch (Exception e) {
            Notification.show("Fehler beim Umlagern: " + e.getMessage(),
                5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            e.printStackTrace();
        }
    }

    /**
     * Sendet einen Kran-Befehl zum physischen Umlagern
     */
    private void executeCraneRelocation(StockyardDTO destination) throws PlcException {
        // Positionen ermitteln (mm)
        int pickupX = sourceStockyard.getXPosition();
        int pickupY = sourceStockyard.getYPosition();
        int pickupZ = sourceStockyard.getZPosition();

        int releaseX = destination.getXPosition();
        int releaseY = destination.getYPosition();
        int releaseZ = destination.getZPosition();

        // Barren-Maße
        int length = topIngot.getLength() != null ? topIngot.getLength() : 5000;
        int width = topIngot.getWidth() != null ? topIngot.getWidth() : 500;
        int thickness = topIngot.getThickness() != null ? topIngot.getThickness() : 200;
        int weight = topIngot.getWeight() != null ? topIngot.getWeight() : 1500;

        // PlcCommand erstellen
        PlcCommand cmd = PlcCommand.builder()
            .pickupPosition(pickupX, pickupY, pickupZ)
            .releasePosition(releaseX, releaseY, releaseZ)
            .dimensions(length, width, thickness)
            .weight(weight)
            .longIngot(length > 6000)
            .rotate(false)
            .build();

        // An PLC senden
        plcService.sendCommand(cmd);

        // Auch in Datenbank umlagern (für Konsistenz)
        ingotService.relocate(topIngot.getId(), destination.getId());

        String modeText = plcService.isSimulatorMode() ? "Simulator" : "SPS";
        Notification.show(
            String.format("Kran-Befehl gesendet (%s): %s → %s",
                modeText, sourceStockyard.getYardNumber(), destination.getYardNumber()),
            5000, Notification.Position.BOTTOM_CENTER)
            .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

        if (onRelocated != null) {
            onRelocated.accept(null);
        }

        close();
    }

    /**
     * Lagert nur in der Datenbank um (ohne Kran)
     */
    private void executeDatabaseRelocation(StockyardDTO destination) {
        ingotService.relocate(topIngot.getId(), destination.getId());

        Notification.show(
            String.format("Barren %s nach %s umgelagert (nur DB)",
                topIngot.getIngotNo(), destination.getYardNumber()),
            3000, Notification.Position.BOTTOM_CENTER)
            .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

        if (onRelocated != null) {
            onRelocated.accept(null);
        }

        close();
    }

    public void setOnRelocated(Consumer<Void> onRelocated) {
        this.onRelocated = onRelocated;
    }
}
