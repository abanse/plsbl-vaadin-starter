package com.hydro.plsbl.ui.dialog;

import com.hydro.plsbl.dto.StockyardDTO;
import com.hydro.plsbl.entity.enums.StockyardType;
import com.hydro.plsbl.entity.enums.StockyardUsage;
import com.hydro.plsbl.entity.masterdata.Stockyard;
import com.hydro.plsbl.service.StockyardService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Dialog zur Verwaltung von Lagerplätzen.
 * Ermöglicht Suchen, Filtern und Bearbeiten aller Lagerplätze.
 */
public class StockyardManagementDialog extends Dialog {

    private final StockyardService stockyardService;
    private Consumer<Void> onSaved;

    // Filter-Komponenten
    private TextField searchField;
    private ComboBox<StockyardType> typeFilter;
    private ComboBox<StockyardUsage> usageFilter;
    private Checkbox onlyWithIngots;
    private Checkbox onlyEmpty;

    // Liste
    private Grid<Stockyard> stockyardGrid;
    private List<Stockyard> allStockyards;

    // Bearbeitungsformular
    private Stockyard selectedStockyard;
    private TextField yardNumberField;
    private IntegerField xCoordinateField;
    private IntegerField yCoordinateField;
    private ComboBox<StockyardType> typeField;
    private ComboBox<StockyardUsage> usageField;
    private IntegerField bottomCenterXField;
    private IntegerField bottomCenterYField;
    private IntegerField bottomCenterZField;
    private IntegerField maxIngotsField;
    private Checkbox toStockAllowedField;
    private Checkbox fromStockAllowedField;
    private Button saveButton;
    private Span statusLabel;

    public StockyardManagementDialog(StockyardService stockyardService) {
        this.stockyardService = stockyardService;

        setHeaderTitle("Lagerplätze verwalten");
        setWidth("1200px");
        setHeight("700px");
        setModal(true);
        setDraggable(true);
        setResizable(true);
        setCloseOnOutsideClick(false);

        createContent();
        createFooter();
        loadData();
    }

    private void createContent() {
        // Linke Seite: Filter und Liste
        VerticalLayout leftPanel = createLeftPanel();
        leftPanel.setWidth("600px");

        // Rechte Seite: Bearbeitungsformular
        VerticalLayout rightPanel = createRightPanel();
        rightPanel.setWidth("500px");

        SplitLayout splitLayout = new SplitLayout(leftPanel, rightPanel);
        splitLayout.setSplitterPosition(55);
        splitLayout.setSizeFull();

        add(splitLayout);
    }

    private VerticalLayout createLeftPanel() {
        VerticalLayout panel = new VerticalLayout();
        panel.setPadding(true);
        panel.setSpacing(true);
        panel.setSizeFull();

        // Filter-Bereich
        panel.add(new H3("Lagerplätze suchen"));
        panel.add(createFilterSection());
        panel.add(new Hr());

        // Grid
        stockyardGrid = createStockyardGrid();
        panel.add(stockyardGrid);
        panel.setFlexGrow(1, stockyardGrid);

        return panel;
    }

    private HorizontalLayout createFilterSection() {
        // Suchfeld
        searchField = new TextField();
        searchField.setPlaceholder("Lagerplatz-Nr. suchen...");
        searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchField.setWidth("150px");
        searchField.setValueChangeMode(ValueChangeMode.LAZY);
        searchField.addValueChangeListener(e -> applyFilters());

        // Typ-Filter
        typeFilter = new ComboBox<>("Typ");
        typeFilter.setItems(StockyardType.values());
        typeFilter.setItemLabelGenerator(StockyardType::getDisplayName);
        typeFilter.setClearButtonVisible(true);
        typeFilter.setWidth("120px");
        typeFilter.addValueChangeListener(e -> applyFilters());

        // Verwendung-Filter
        usageFilter = new ComboBox<>("Verwendung");
        usageFilter.setItems(StockyardUsage.values());
        usageFilter.setItemLabelGenerator(StockyardUsage::getDisplayName);
        usageFilter.setClearButtonVisible(true);
        usageFilter.setWidth("120px");
        usageFilter.addValueChangeListener(e -> applyFilters());

        // Checkboxen
        onlyWithIngots = new Checkbox("Nur belegt");
        onlyWithIngots.addValueChangeListener(e -> {
            if (e.getValue()) onlyEmpty.setValue(false);
            applyFilters();
        });

        onlyEmpty = new Checkbox("Nur leer");
        onlyEmpty.addValueChangeListener(e -> {
            if (e.getValue()) onlyWithIngots.setValue(false);
            applyFilters();
        });

        HorizontalLayout filterLayout = new HorizontalLayout(
            searchField, typeFilter, usageFilter, onlyWithIngots, onlyEmpty
        );
        filterLayout.setAlignItems(FlexComponent.Alignment.END);
        filterLayout.setSpacing(true);

        return filterLayout;
    }

    private Grid<Stockyard> createStockyardGrid() {
        Grid<Stockyard> grid = new Grid<>();
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        grid.setSelectionMode(Grid.SelectionMode.SINGLE);
        grid.setSizeFull();

        grid.addColumn(Stockyard::getYardNumber)
            .setHeader("Nr.")
            .setWidth("80px")
            .setFlexGrow(0)
            .setSortable(true);

        grid.addColumn(yard -> yard.getType() != null ? yard.getType().getDisplayName() : "-")
            .setHeader("Typ")
            .setWidth("100px")
            .setFlexGrow(0)
            .setSortable(true);

        grid.addColumn(yard -> yard.getUsage() != null ? yard.getUsage().getDisplayName() : "-")
            .setHeader("Verwendung")
            .setWidth("100px")
            .setFlexGrow(0)
            .setSortable(true);

        grid.addColumn(yard -> yard.getXCoordinate() + "/" + yard.getYCoordinate())
            .setHeader("Koord.")
            .setWidth("70px")
            .setFlexGrow(0);

        grid.addColumn(Stockyard::getMaxIngots)
            .setHeader("Max")
            .setWidth("50px")
            .setFlexGrow(0);

        grid.addColumn(yard -> yard.isToStockAllowed() ? "Ja" : "Nein")
            .setHeader("Einl.")
            .setWidth("60px")
            .setFlexGrow(0);

        grid.addColumn(yard -> yard.isFromStockAllowed() ? "Ja" : "Nein")
            .setHeader("Ausl.")
            .setWidth("60px")
            .setFlexGrow(0);

        grid.addColumn(yard -> formatCoord(yard.getXPosition()))
            .setHeader("X (mm)")
            .setWidth("80px")
            .setFlexGrow(0);

        grid.addColumn(yard -> formatCoord(yard.getYPosition()))
            .setHeader("Y (mm)")
            .setWidth("80px")
            .setFlexGrow(0);

        // Auswahl-Handler
        grid.asSingleSelect().addValueChangeListener(e -> {
            selectedStockyard = e.getValue();
            updateForm();
        });

        return grid;
    }

    private String formatCoord(Integer value) {
        return value != null ? String.format("%,d", value) : "-";
    }

    private VerticalLayout createRightPanel() {
        VerticalLayout panel = new VerticalLayout();
        panel.setPadding(true);
        panel.setSpacing(true);

        panel.add(new H3("Lagerplatz bearbeiten"));

        statusLabel = new Span("Wählen Sie einen Lagerplatz aus der Liste");
        statusLabel.getStyle().set("color", "gray").set("font-style", "italic");
        panel.add(statusLabel);

        panel.add(createEditForm());

        return panel;
    }

    private FormLayout createEditForm() {
        FormLayout form = new FormLayout();
        form.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("400px", 2)
        );

        // Grunddaten
        yardNumberField = new TextField("Lagerplatz-Nr.");
        yardNumberField.setReadOnly(true);
        form.add(yardNumberField);

        typeField = new ComboBox<>("Typ");
        typeField.setItems(StockyardType.values());
        typeField.setItemLabelGenerator(StockyardType::getDisplayName);
        form.add(typeField);

        usageField = new ComboBox<>("Verwendung");
        usageField.setItems(StockyardUsage.values());
        usageField.setItemLabelGenerator(StockyardUsage::getDisplayName);
        form.add(usageField);

        // Koordinaten (Grid)
        xCoordinateField = new IntegerField("X-Koordinate (Grid)");
        xCoordinateField.setMin(0);
        xCoordinateField.setMax(99);
        form.add(xCoordinateField);

        yCoordinateField = new IntegerField("Y-Koordinate (Grid)");
        yCoordinateField.setMin(0);
        yCoordinateField.setMax(99);
        form.add(yCoordinateField);

        // Kran-Koordinaten (mm)
        form.add(new Hr(), 2);
        Span craneLabel = new Span("Kran-Koordinaten (mm):");
        craneLabel.getStyle().set("font-weight", "bold");
        form.add(craneLabel, 2);

        bottomCenterXField = new IntegerField("X-Position");
        bottomCenterXField.setStep(100);
        form.add(bottomCenterXField);

        bottomCenterYField = new IntegerField("Y-Position");
        bottomCenterYField.setStep(100);
        form.add(bottomCenterYField);

        bottomCenterZField = new IntegerField("Z-Position (Höhe)");
        bottomCenterZField.setStep(100);
        form.add(bottomCenterZField);

        // Kapazität
        form.add(new Hr(), 2);

        maxIngotsField = new IntegerField("Max. Barren");
        maxIngotsField.setMin(1);
        maxIngotsField.setMax(20);
        form.add(maxIngotsField);

        // Berechtigungen
        form.add(new Hr(), 2);
        Span permLabel = new Span("Berechtigungen:");
        permLabel.getStyle().set("font-weight", "bold");
        form.add(permLabel, 2);

        toStockAllowedField = new Checkbox("Einlagern erlaubt");
        form.add(toStockAllowedField);

        fromStockAllowedField = new Checkbox("Auslagern erlaubt");
        form.add(fromStockAllowedField);

        // Speichern-Button
        form.add(new Hr(), 2);

        saveButton = new Button("Speichern", VaadinIcon.CHECK.create());
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveButton.setEnabled(false);
        saveButton.addClickListener(e -> saveStockyard());
        form.add(saveButton, 2);

        // Alle Felder initial deaktivieren
        setFormEnabled(false);

        return form;
    }

    private void setFormEnabled(boolean enabled) {
        typeField.setEnabled(enabled);
        usageField.setEnabled(enabled);
        xCoordinateField.setEnabled(enabled);
        yCoordinateField.setEnabled(enabled);
        bottomCenterXField.setEnabled(enabled);
        bottomCenterYField.setEnabled(enabled);
        bottomCenterZField.setEnabled(enabled);
        maxIngotsField.setEnabled(enabled);
        toStockAllowedField.setEnabled(enabled);
        fromStockAllowedField.setEnabled(enabled);
        saveButton.setEnabled(enabled);
    }

    private void updateForm() {
        if (selectedStockyard == null) {
            clearForm();
            setFormEnabled(false);
            statusLabel.setText("Wählen Sie einen Lagerplatz aus der Liste");
            return;
        }

        setFormEnabled(true);
        statusLabel.setText("Bearbeiten: " + selectedStockyard.getYardNumber());

        yardNumberField.setValue(selectedStockyard.getYardNumber() != null ? selectedStockyard.getYardNumber() : "");
        typeField.setValue(selectedStockyard.getType());
        usageField.setValue(selectedStockyard.getUsage());
        xCoordinateField.setValue(selectedStockyard.getXCoordinate());
        yCoordinateField.setValue(selectedStockyard.getYCoordinate());
        bottomCenterXField.setValue(selectedStockyard.getXPosition());
        bottomCenterYField.setValue(selectedStockyard.getYPosition());
        bottomCenterZField.setValue(selectedStockyard.getZPosition());
        maxIngotsField.setValue(selectedStockyard.getMaxIngots());
        toStockAllowedField.setValue(selectedStockyard.isToStockAllowed());
        fromStockAllowedField.setValue(selectedStockyard.isFromStockAllowed());
    }

    private void clearForm() {
        yardNumberField.clear();
        typeField.clear();
        usageField.clear();
        xCoordinateField.clear();
        yCoordinateField.clear();
        bottomCenterXField.clear();
        bottomCenterYField.clear();
        bottomCenterZField.clear();
        maxIngotsField.clear();
        toStockAllowedField.setValue(false);
        fromStockAllowedField.setValue(false);
    }

    private void saveStockyard() {
        if (selectedStockyard == null) return;

        try {
            // Werte aus Formular übernehmen
            selectedStockyard.setType(typeField.getValue());
            selectedStockyard.setUsage(usageField.getValue());
            selectedStockyard.setXCoordinate(xCoordinateField.getValue() != null ? xCoordinateField.getValue() : 0);
            selectedStockyard.setYCoordinate(yCoordinateField.getValue() != null ? yCoordinateField.getValue() : 0);
            selectedStockyard.setXPosition(bottomCenterXField.getValue() != null ? bottomCenterXField.getValue() : 0);
            selectedStockyard.setYPosition(bottomCenterYField.getValue() != null ? bottomCenterYField.getValue() : 0);
            selectedStockyard.setZPosition(bottomCenterZField.getValue() != null ? bottomCenterZField.getValue() : 0);
            selectedStockyard.setMaxIngots(maxIngotsField.getValue() != null ? maxIngotsField.getValue() : 1);
            selectedStockyard.setToStockAllowed(toStockAllowedField.getValue());
            selectedStockyard.setFromStockAllowed(fromStockAllowedField.getValue());

            // Speichern
            stockyardService.save(selectedStockyard);

            Notification.show("Lagerplatz " + selectedStockyard.getYardNumber() + " gespeichert",
                3000, Notification.Position.BOTTOM_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            // Liste neu laden
            loadData();

            if (onSaved != null) {
                onSaved.accept(null);
            }

        } catch (Exception e) {
            Notification.show("Fehler beim Speichern: " + e.getMessage(),
                5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            e.printStackTrace();
        }
    }

    private void loadData() {
        try {
            allStockyards = stockyardService.findAll();
            applyFilters();
        } catch (Exception e) {
            Notification.show("Fehler beim Laden: " + e.getMessage(),
                5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void applyFilters() {
        if (allStockyards == null) return;

        String searchText = searchField.getValue() != null ? searchField.getValue().toLowerCase() : "";
        StockyardType selectedType = typeFilter.getValue();
        StockyardUsage selectedUsage = usageFilter.getValue();
        boolean showOnlyWithIngots = onlyWithIngots.getValue();
        boolean showOnlyEmpty = onlyEmpty.getValue();

        List<Stockyard> filtered = allStockyards.stream()
            .filter(yard -> {
                // Text-Suche
                if (!searchText.isEmpty()) {
                    String yardNo = yard.getYardNumber() != null ? yard.getYardNumber().toLowerCase() : "";
                    if (!yardNo.contains(searchText)) {
                        return false;
                    }
                }

                // Typ-Filter
                if (selectedType != null && yard.getType() != selectedType) {
                    return false;
                }

                // Verwendung-Filter
                if (selectedUsage != null && yard.getUsage() != selectedUsage) {
                    return false;
                }

                // Belegt/Leer-Filter - hier müssten wir den Status abfragen
                // Für jetzt lassen wir das weg, da wir keine Status-Info haben

                return true;
            })
            .collect(Collectors.toList());

        stockyardGrid.setItems(filtered);

        // Status aktualisieren
        Span countLabel = new Span(filtered.size() + " von " + allStockyards.size() + " Lagerplätzen");
        countLabel.getStyle().set("font-size", "12px").set("color", "gray");
    }

    private void createFooter() {
        Button closeButton = new Button("Schließen", VaadinIcon.CLOSE.create());
        closeButton.addClickListener(e -> close());

        Button refreshButton = new Button("Aktualisieren", VaadinIcon.REFRESH.create());
        refreshButton.addClickListener(e -> loadData());

        HorizontalLayout footer = new HorizontalLayout(refreshButton, closeButton);
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        footer.setWidthFull();
        getFooter().add(footer);
    }

    public void setOnSaved(Consumer<Void> onSaved) {
        this.onSaved = onSaved;
    }
}
