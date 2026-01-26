package com.hydro.plsbl.ui.dialog;

import com.hydro.plsbl.dto.IngotDTO;
import com.hydro.plsbl.dto.StockyardDTO;
import com.hydro.plsbl.entity.transdata.Shipment;
import com.hydro.plsbl.service.IngotService;
import com.hydro.plsbl.service.ShipmentService;
import com.hydro.plsbl.service.StockyardService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
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
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Dialog zum Erstellen eines neuen Lieferscheins
 * Ermöglicht die Auswahl von Barren aus allen Lagerplätzen (ohne Kran/SPS)
 */
public class NeuerLieferscheinDialog extends Dialog {

    private final ShipmentService shipmentService;
    private final IngotService ingotService;
    private final StockyardService stockyardService;

    private Consumer<Shipment> onCreated;

    // Kopfdaten
    private TextField orderNoField;
    private TextField destinationField;
    private TextField customerNoField;
    private TextArea addressField;

    // Barren-Auswahl
    private ComboBox<StockyardDTO> stockyardComboBox;
    private Grid<IngotDTO> availableIngotsGrid;
    private Grid<IngotDTO> selectedIngotsGrid;
    private Set<IngotDTO> selectedIngots = new HashSet<>();

    // Info
    private Span totalWeightLabel;
    private Span countLabel;

    public NeuerLieferscheinDialog(ShipmentService shipmentService,
                                   IngotService ingotService,
                                   StockyardService stockyardService) {
        this.shipmentService = shipmentService;
        this.ingotService = ingotService;
        this.stockyardService = stockyardService;

        setHeaderTitle("Neuer Lieferschein");
        setWidth("1000px");
        setHeight("700px");
        setModal(true);
        setDraggable(true);
        setResizable(true);
        setCloseOnOutsideClick(false);

        createContent();
        createFooter();
        loadStockyards();
    }

    private void createContent() {
        VerticalLayout content = new VerticalLayout();
        content.setPadding(true);
        content.setSpacing(true);
        content.setSizeFull();

        // Kopfdaten
        content.add(new H4("Lieferschein-Daten"));
        content.add(createHeaderSection());

        content.add(new Hr());

        // Barren-Auswahl
        content.add(new H4("Barren auswählen (Lieferung ohne Kran)"));
        content.add(createIngotSelectionSection());

        add(content);
    }

    private HorizontalLayout createHeaderSection() {
        HorizontalLayout layout = new HorizontalLayout();
        layout.setWidthFull();

        orderNoField = new TextField("Auftrags-Nr.");
        orderNoField.setWidth("150px");

        destinationField = new TextField("Lieferort");
        destinationField.setWidth("100px");
        destinationField.setRequired(true);

        customerNoField = new TextField("Kunden-Nr.");
        customerNoField.setWidth("150px");

        addressField = new TextArea("Adresse");
        addressField.setWidth("300px");
        addressField.setHeight("60px");

        layout.add(orderNoField, destinationField, customerNoField, addressField);
        return layout;
    }

    private VerticalLayout createIngotSelectionSection() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(true);
        layout.setSizeFull();

        // Lagerplatz-Auswahl
        HorizontalLayout stockyardRow = new HorizontalLayout();
        stockyardRow.setAlignItems(FlexComponent.Alignment.END);

        stockyardComboBox = new ComboBox<>("Lagerplatz");
        stockyardComboBox.setWidth("250px");
        stockyardComboBox.setItemLabelGenerator(yard -> {
            String typeLabel = yard.getType() != null ? " [" + yard.getType().getDisplayName() + "]" : "";
            int count = yard.getStatus() != null ? yard.getStatus().getIngotsCount() : 0;
            return yard.getYardNumber() + typeLabel + " (" + count + " Barren)";
        });
        stockyardComboBox.addValueChangeListener(e -> loadIngotsForStockyard(e.getValue()));

        stockyardRow.add(stockyardComboBox);
        layout.add(stockyardRow);

        // Zwei-Grid-Layout
        HorizontalLayout gridsLayout = new HorizontalLayout();
        gridsLayout.setSizeFull();
        gridsLayout.setSpacing(true);

        // Verfügbare Barren
        VerticalLayout leftPanel = new VerticalLayout();
        leftPanel.setPadding(false);
        leftPanel.add(new Span("Verfügbare Barren:"));
        availableIngotsGrid = createIngotGrid();
        availableIngotsGrid.setSelectionMode(Grid.SelectionMode.MULTI);
        leftPanel.add(availableIngotsGrid);
        leftPanel.setFlexGrow(1, availableIngotsGrid);

        // Buttons für Hinzufügen/Entfernen
        VerticalLayout buttonPanel = new VerticalLayout();
        buttonPanel.setAlignItems(FlexComponent.Alignment.CENTER);
        buttonPanel.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        buttonPanel.setWidth("80px");

        Button addButton = new Button(VaadinIcon.ANGLE_RIGHT.create());
        addButton.addClickListener(e -> addSelectedIngots());
        addButton.setTooltipText("Hinzufügen");

        Button addAllButton = new Button(VaadinIcon.ANGLE_DOUBLE_RIGHT.create());
        addAllButton.addClickListener(e -> addAllIngots());
        addAllButton.setTooltipText("Alle hinzufügen");

        Button removeButton = new Button(VaadinIcon.ANGLE_LEFT.create());
        removeButton.addClickListener(e -> removeSelectedIngots());
        removeButton.setTooltipText("Entfernen");

        Button removeAllButton = new Button(VaadinIcon.ANGLE_DOUBLE_LEFT.create());
        removeAllButton.addClickListener(e -> removeAllIngots());
        removeAllButton.setTooltipText("Alle entfernen");

        buttonPanel.add(addButton, addAllButton, removeButton, removeAllButton);

        // Ausgewählte Barren
        VerticalLayout rightPanel = new VerticalLayout();
        rightPanel.setPadding(false);
        rightPanel.add(new Span("Ausgewählte Barren (für Lieferschein):"));
        selectedIngotsGrid = createIngotGrid();
        selectedIngotsGrid.setSelectionMode(Grid.SelectionMode.MULTI);
        rightPanel.add(selectedIngotsGrid);
        rightPanel.setFlexGrow(1, selectedIngotsGrid);

        gridsLayout.add(leftPanel, buttonPanel, rightPanel);
        gridsLayout.setFlexGrow(1, leftPanel);
        gridsLayout.setFlexGrow(1, rightPanel);
        layout.add(gridsLayout);
        layout.setFlexGrow(1, gridsLayout);

        // Info-Zeile
        HorizontalLayout infoRow = new HorizontalLayout();
        countLabel = new Span("0 Barren ausgewählt");
        totalWeightLabel = new Span("Gesamtgewicht: 0 kg");
        totalWeightLabel.getStyle().set("font-weight", "bold");
        infoRow.add(countLabel, totalWeightLabel);
        infoRow.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        infoRow.setWidthFull();
        layout.add(infoRow);

        return layout;
    }

    private Grid<IngotDTO> createIngotGrid() {
        Grid<IngotDTO> grid = new Grid<>();
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        grid.setHeight("200px");

        grid.addColumn(IngotDTO::getIngotNo)
            .setHeader("Barren-Nr")
            .setAutoWidth(true)
            .setSortable(true);

        grid.addColumn(IngotDTO::getProductNo)
            .setHeader("Produkt")
            .setWidth("150px");

        grid.addColumn(i -> i.getWeight() != null ? i.getWeight() + " kg" : "-")
            .setHeader("Gewicht")
            .setWidth("80px");

        grid.addColumn(i -> i.getLength() != null ? i.getLength() + " mm" : "-")
            .setHeader("Länge")
            .setWidth("80px");

        return grid;
    }

    private void loadStockyards() {
        try {
            // Lade alle Lagerplätze mit Barren (für Lieferung ohne Kran)
            List<StockyardDTO> yardsWithIngots = stockyardService.findAvailableDestinationsIncludingExternal()
                .stream()
                .filter(yard -> yard.getStatus() != null && yard.getStatus().getIngotsCount() > 0)
                .collect(Collectors.toList());

            stockyardComboBox.setItems(yardsWithIngots);

            if (yardsWithIngots.isEmpty()) {
                Notification.show("Keine Lagerplätze mit Barren gefunden",
                    3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_WARNING);
            }
        } catch (Exception e) {
            Notification.show("Fehler beim Laden der Lagerplätze: " + e.getMessage(),
                5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void loadIngotsForStockyard(StockyardDTO stockyard) {
        if (stockyard == null) {
            availableIngotsGrid.setItems(List.of());
            return;
        }

        try {
            List<IngotDTO> ingots = ingotService.findByStockyardId(stockyard.getId());
            // Bereits ausgewählte entfernen
            ingots.removeIf(selectedIngots::contains);
            availableIngotsGrid.setItems(ingots);
        } catch (Exception e) {
            Notification.show("Fehler beim Laden der Barren: " + e.getMessage(),
                5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void addSelectedIngots() {
        Set<IngotDTO> toAdd = availableIngotsGrid.getSelectedItems();
        if (toAdd.isEmpty()) return;

        selectedIngots.addAll(toAdd);
        updateGrids();
    }

    private void addAllIngots() {
        List<IngotDTO> allItems = availableIngotsGrid.getListDataView().getItems()
            .collect(Collectors.toList());
        selectedIngots.addAll(allItems);
        updateGrids();
    }

    private void removeSelectedIngots() {
        Set<IngotDTO> toRemove = selectedIngotsGrid.getSelectedItems();
        if (toRemove.isEmpty()) return;

        selectedIngots.removeAll(toRemove);
        updateGrids();
    }

    private void removeAllIngots() {
        selectedIngots.clear();
        updateGrids();
    }

    private void updateGrids() {
        // Aktualisiere ausgewählte Barren Grid
        selectedIngotsGrid.setItems(new ArrayList<>(selectedIngots));

        // Aktualisiere verfügbare Barren (entferne bereits ausgewählte)
        StockyardDTO selectedYard = stockyardComboBox.getValue();
        if (selectedYard != null) {
            loadIngotsForStockyard(selectedYard);
        }

        // Aktualisiere Info
        int count = selectedIngots.size();
        int totalWeight = selectedIngots.stream()
            .mapToInt(i -> i.getWeight() != null ? i.getWeight() : 0)
            .sum();

        countLabel.setText(count + " Barren ausgewählt");
        totalWeightLabel.setText("Gesamtgewicht: " + totalWeight + " kg");
    }

    private void createFooter() {
        Button createButton = new Button("Lieferschein erstellen", VaadinIcon.CHECK.create());
        createButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        createButton.addClickListener(e -> createShipment());

        Button cancelButton = new Button("Abbrechen", VaadinIcon.CLOSE.create());
        cancelButton.addClickListener(e -> close());

        HorizontalLayout footer = new HorizontalLayout(cancelButton, createButton);
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        footer.setWidthFull();
        getFooter().add(footer);
    }

    private void createShipment() {
        // Validierung
        if (destinationField.isEmpty()) {
            Notification.show("Bitte geben Sie einen Lieferort an",
                3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_WARNING);
            destinationField.focus();
            return;
        }

        if (selectedIngots.isEmpty()) {
            Notification.show("Bitte wählen Sie mindestens einen Barren aus",
                3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_WARNING);
            return;
        }

        try {
            // Lieferschein erstellen
            Shipment shipment = shipmentService.createShipment(
                orderNoField.getValue(),
                destinationField.getValue(),
                customerNoField.getValue(),
                addressField.getValue(),
                new ArrayList<>(selectedIngots)
            );

            // Callback aufrufen
            if (onCreated != null) {
                onCreated.accept(shipment);
            }

            close();

        } catch (Exception e) {
            Notification.show("Fehler beim Erstellen: " + e.getMessage(),
                5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            e.printStackTrace();
        }
    }

    public void setOnCreated(Consumer<Shipment> onCreated) {
        this.onCreated = onCreated;
    }
}
