package com.hydro.plsbl.ui.dialog;

import com.hydro.plsbl.dto.IngotDTO;
import com.hydro.plsbl.dto.StockyardDTO;
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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Dialog zum Umlagern von Barren zwischen Lagerplätzen
 */
public class UmlagernDialog extends Dialog {

    private final StockyardDTO sourceStockyard;
    private final IngotService ingotService;
    private final StockyardService stockyardService;

    private Grid<IngotDTO> ingotGrid;
    private ComboBox<StockyardDTO> destinationComboBox;
    private Set<IngotDTO> selectedIngots = new HashSet<>();
    private IngotDTO topIngot; // Oberster Barren der umgelagert wird
    private Span selectedCountLabel;
    private Button relocateButton;

    private Consumer<Void> onRelocated;

    public UmlagernDialog(StockyardDTO sourceStockyard,
                          IngotService ingotService,
                          StockyardService stockyardService) {
        this.sourceStockyard = sourceStockyard;
        this.ingotService = ingotService;
        this.stockyardService = stockyardService;

        setHeaderTitle("Umlagern von " + sourceStockyard.getYardNumber());
        setWidth("700px");
        setHeight("550px");
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
        content.add(new Span("Barren auf diesem Platz (sortiert nach Position):"));
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

        add(content);
    }

    private Grid<IngotDTO> createIngotGrid() {
        ingotGrid = new Grid<>();
        ingotGrid.setHeight("150px");
        ingotGrid.setWidthFull();
        ingotGrid.addThemeVariants(GridVariant.LUMO_COMPACT);
        ingotGrid.setSelectionMode(Grid.SelectionMode.NONE); // Keine Auswahl - nur Anzeige

        // Spalten
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

        // Alle Barren laden und nach Position sortieren (höchste zuerst)
        List<IngotDTO> ingots = ingotService.findByStockyardId(sourceStockyard.getId());
        ingots.sort((a, b) -> {
            int posA = a.getPilePosition() != null ? a.getPilePosition() : 0;
            int posB = b.getPilePosition() != null ? b.getPilePosition() : 0;
            return Integer.compare(posB, posA); // Absteigend
        });
        ingotGrid.setItems(ingots);

        // Nur obersten Barren als "ausgewählt" markieren
        if (!ingots.isEmpty()) {
            topIngot = ingots.get(0); // Oberster Barren (höchste Position)
            selectedIngots.add(topIngot);
        }

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

        try {
            ingotService.relocate(topIngot.getId(), destination.getId());

            Notification.show("Barren " + topIngot.getIngotNo() + " nach " + destination.getYardNumber() + " umgelagert",
                3000, Notification.Position.BOTTOM_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            if (onRelocated != null) {
                onRelocated.accept(null);
            }

            close();

        } catch (Exception e) {
            Notification.show("Fehler beim Umlagern: " + e.getMessage(),
                5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            e.printStackTrace();
        }
    }

    public void setOnRelocated(Consumer<Void> onRelocated) {
        this.onRelocated = onRelocated;
    }
}
