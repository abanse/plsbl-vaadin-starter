package com.hydro.plsbl.ui.dialog;

import com.hydro.plsbl.dto.IngotDTO;
import com.hydro.plsbl.dto.StockyardDTO;
import com.hydro.plsbl.dto.TransportOrderDTO;
import com.hydro.plsbl.service.IngotService;
import com.hydro.plsbl.service.StockyardService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;

import java.util.List;
import java.util.function.Consumer;

/**
 * Edit-Dialog für Transportaufträge
 * Zeigt automatisch den Quell-Lagerplatz des gewählten Barrens und schlägt Ziele vor.
 */
public class TransportOrderEditDialog extends Dialog {

    private final TransportOrderDTO order;
    private final boolean isNew;
    private final IngotService ingotService;
    private final StockyardService stockyardService;
    private Consumer<TransportOrderDTO> onSave;

    // Formular-Felder
    private TextField transportNoField;
    private TextField normTextField;
    private ComboBox<IngotDTO> ingotComboBox;
    private TextField fromYardField;
    private IntegerField fromPilePositionField;
    private ComboBox<StockyardDTO> toYardComboBox;
    private IntegerField toPilePositionField;

    // Aktuell ausgewählter Barren
    private IngotDTO selectedIngot;

    public TransportOrderEditDialog(TransportOrderDTO order, boolean isNew,
                                    IngotService ingotService, StockyardService stockyardService) {
        this.order = order != null ? order : new TransportOrderDTO();
        this.isNew = isNew;
        this.ingotService = ingotService;
        this.stockyardService = stockyardService;

        setHeaderTitle(isNew ? "Neuer Transportauftrag" : "Transportauftrag #" + this.order.getId() + " bearbeiten");
        setWidth("600px");
        setModal(true);
        setDraggable(true);
        setCloseOnOutsideClick(false);

        createContent();
        createFooter();

        if (!isNew) {
            populateFields();
        }
    }

    private void createContent() {
        VerticalLayout content = new VerticalLayout();
        content.setPadding(true);
        content.setSpacing(true);

        FormLayout form = new FormLayout();
        form.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("400px", 2)
        );

        // === Allgemeine Daten ===
        transportNoField = new TextField("Transport-Nr");
        transportNoField.setMaxLength(10);
        transportNoField.setWidthFull();
        transportNoField.setHelperText("Max. 10 Zeichen");

        normTextField = new TextField("Norm");
        normTextField.setWidthFull();

        form.add(transportNoField, normTextField);

        // Trenner
        content.add(form, new Hr());

        // === Barren-Auswahl ===
        FormLayout ingotForm = new FormLayout();
        ingotForm.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("400px", 2)
        );

        ingotComboBox = new ComboBox<>("Barren auswählen");
        ingotComboBox.setWidthFull();
        ingotComboBox.setRequired(true);
        ingotComboBox.setItemLabelGenerator(IngotDTO::getDisplayName);
        ingotComboBox.setHelperText("Pflichtfeld - wählen Sie einen Barren");

        // Barren laden
        List<IngotDTO> ingots = ingotService.findLatest(100);
        ingotComboBox.setItems(ingots);

        // Value-Change-Listener: Bei Barren-Auswahl Quell-Lagerplatz anzeigen
        ingotComboBox.addValueChangeListener(event -> {
            selectedIngot = event.getValue();
            updateSourceStockyard();
        });

        ingotForm.add(ingotComboBox);
        ingotForm.setColspan(ingotComboBox, 2);

        content.add(ingotForm, new Hr());

        // === Quelle (Von) ===
        Span fromLabel = new Span("Quelle (Von)");
        fromLabel.getStyle().set("font-weight", "bold");
        content.add(fromLabel);

        FormLayout fromForm = new FormLayout();
        fromForm.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("400px", 2)
        );

        fromYardField = new TextField("Lagerplatz");
        fromYardField.setReadOnly(true);
        fromYardField.setWidthFull();
        fromYardField.setHelperText("Wird automatisch vom Barren übernommen");

        fromPilePositionField = new IntegerField("Stapel-Position");
        fromPilePositionField.setReadOnly(true);
        fromPilePositionField.setWidthFull();

        fromForm.add(fromYardField, fromPilePositionField);
        content.add(fromForm, new Hr());

        // === Ziel (Nach) ===
        Span toLabel = new Span("Ziel (Nach)");
        toLabel.getStyle().set("font-weight", "bold");
        content.add(toLabel);

        FormLayout toForm = new FormLayout();
        toForm.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("400px", 2)
        );

        toYardComboBox = new ComboBox<>("Ziel-Lagerplatz");
        toYardComboBox.setWidthFull();
        toYardComboBox.setItemLabelGenerator(this::getStockyardLabel);
        toYardComboBox.setHelperText("Verfügbare Ziel-Lagerplätze");

        // Ziel-Lagerplätze laden
        List<StockyardDTO> destinations = stockyardService.findAvailableDestinations();
        toYardComboBox.setItems(destinations);

        toPilePositionField = new IntegerField("Ziel-Position");
        toPilePositionField.setMin(1);
        toPilePositionField.setMax(10);
        toPilePositionField.setStep(1);
        toPilePositionField.setWidthFull();
        toPilePositionField.setHelperText("Position im Stapel");

        toForm.add(toYardComboBox, toPilePositionField);
        content.add(toForm);

        add(content);
    }

    private void createFooter() {
        Button saveButton = new Button("Speichern", VaadinIcon.CHECK.create());
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveButton.addClickListener(e -> save());

        Button cancelButton = new Button("Abbrechen", VaadinIcon.CLOSE.create());
        cancelButton.addClickListener(e -> close());

        HorizontalLayout footer = new HorizontalLayout(cancelButton, saveButton);
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        footer.setWidthFull();
        getFooter().add(footer);
    }

    private void updateSourceStockyard() {
        if (selectedIngot == null) {
            fromYardField.clear();
            fromPilePositionField.clear();
            return;
        }

        // Lagerplatz des Barrens laden
        if (selectedIngot.getStockyardId() != null) {
            stockyardService.findById(selectedIngot.getStockyardId()).ifPresent(yard -> {
                fromYardField.setValue(yard.getYardNumber());
                order.setFromYardId(yard.getId());
                order.setFromYardNo(yard.getYardNumber());
            });

            // Stapel-Position setzen
            if (selectedIngot.getPilePosition() != null) {
                fromPilePositionField.setValue(selectedIngot.getPilePosition());
                order.setFromPilePosition(selectedIngot.getPilePosition());
            }
        } else {
            fromYardField.setValue("(kein Lagerplatz)");
        }

        // Ziel-Position vorschlagen (nächste freie Position)
        if (toPilePositionField.isEmpty()) {
            toPilePositionField.setValue(1);
        }
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

        if (yard.getDescription() != null && !yard.getDescription().isEmpty()) {
            sb.append(" - ").append(yard.getDescription());
        }

        return sb.toString();
    }

    private void populateFields() {
        if (order.getTransportNo() != null) {
            transportNoField.setValue(order.getTransportNo());
        }
        if (order.getNormText() != null) {
            normTextField.setValue(order.getNormText());
        }
        if (order.getIngotId() != null) {
            ingotService.findById(order.getIngotId()).ifPresent(ingot -> {
                ingotComboBox.setValue(ingot);
                selectedIngot = ingot;
                updateSourceStockyard();
            });
        }
        if (order.getFromYardNo() != null) {
            fromYardField.setValue(order.getFromYardNo());
        }
        if (order.getFromPilePosition() != null) {
            fromPilePositionField.setValue(order.getFromPilePosition());
        }
        if (order.getToYardId() != null) {
            stockyardService.findById(order.getToYardId()).ifPresent(toYardComboBox::setValue);
        }
        if (order.getToPilePosition() != null) {
            toPilePositionField.setValue(order.getToPilePosition());
        }
    }

    private void save() {
        // Validierung
        String transportNo = transportNoField.getValue();
        if (transportNo != null && transportNo.length() > 10) {
            Notification.show("Transport-Nr darf max. 10 Zeichen haben", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        if (selectedIngot == null) {
            Notification.show("Bitte einen Barren auswählen", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        // Werte übernehmen
        order.setTransportNo(transportNoField.getValue());
        order.setNormText(normTextField.getValue());
        order.setIngotId(selectedIngot.getId());
        order.setIngotNo(selectedIngot.getIngotNo());

        // Quelle
        if (selectedIngot.getStockyardId() != null) {
            order.setFromYardId(selectedIngot.getStockyardId());
        }
        order.setFromPilePosition(fromPilePositionField.getValue());

        // Ziel
        StockyardDTO selectedDestination = toYardComboBox.getValue();
        if (selectedDestination != null) {
            order.setToYardId(selectedDestination.getId());
            order.setToYardNo(selectedDestination.getYardNumber());
        }
        order.setToPilePosition(toPilePositionField.getValue());

        // Callback aufrufen
        if (onSave != null) {
            onSave.accept(order);
        }

        Notification.show("Gespeichert", 2000, Notification.Position.BOTTOM_START)
            .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        close();
    }

    public void setOnSave(Consumer<TransportOrderDTO> onSave) {
        this.onSave = onSave;
    }
}
