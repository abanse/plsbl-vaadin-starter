package com.hydro.plsbl.ui.dialog;

import com.hydro.plsbl.dto.StockyardDTO;
import com.hydro.plsbl.entity.enums.StockyardType;
import com.hydro.plsbl.entity.enums.StockyardUsage;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
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

import java.util.function.Consumer;

/**
 * Edit-Dialog für Lagerplätze
 */
public class StockyardEditDialog extends Dialog {

    private final StockyardDTO stockyard;
    private final boolean isNew;
    private Consumer<StockyardDTO> onSave;

    // Formular-Felder
    private TextField yardNumberField;
    private TextField descriptionField;
    private ComboBox<StockyardType> typeComboBox;
    private ComboBox<StockyardUsage> usageComboBox;
    private IntegerField xCoordinateField;
    private IntegerField yCoordinateField;
    private IntegerField maxIngotsField;
    private IntegerField xPositionField;
    private IntegerField yPositionField;
    private IntegerField zPositionField;
    private IntegerField lengthField;
    private IntegerField widthField;
    private IntegerField heightField;
    private Checkbox toStockAllowedCheckbox;
    private Checkbox fromStockAllowedCheckbox;

    public StockyardEditDialog(StockyardDTO stockyard, boolean isNew) {
        this.stockyard = stockyard != null ? stockyard : new StockyardDTO();
        this.isNew = isNew;

        setHeaderTitle(isNew ? "Neuer Lagerplatz" : "Lagerplatz " + this.stockyard.getYardNumber() + " bearbeiten");
        setWidth("650px");
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

        // === Allgemeine Daten ===
        Span generalLabel = new Span("Allgemeine Daten");
        generalLabel.getStyle().set("font-weight", "bold");
        content.add(generalLabel);

        FormLayout generalForm = new FormLayout();
        generalForm.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("400px", 2)
        );

        yardNumberField = new TextField("Platznummer");
        yardNumberField.setRequired(true);
        yardNumberField.setMaxLength(10);
        yardNumberField.setWidthFull();
        yardNumberField.setHelperText("Pflichtfeld");

        descriptionField = new TextField("Beschreibung");
        descriptionField.setWidthFull();

        typeComboBox = new ComboBox<>("Typ");
        typeComboBox.setItems(StockyardType.values());
        typeComboBox.setItemLabelGenerator(StockyardType::getDisplayName);
        typeComboBox.setWidthFull();
        typeComboBox.setRequired(true);

        usageComboBox = new ComboBox<>("Verwendung");
        usageComboBox.setItems(StockyardUsage.values());
        usageComboBox.setItemLabelGenerator(StockyardUsage::getDisplayName);
        usageComboBox.setWidthFull();

        generalForm.add(yardNumberField, descriptionField, typeComboBox, usageComboBox);
        content.add(generalForm, new Hr());

        // === Koordinaten ===
        Span coordLabel = new Span("Grid-Koordinaten");
        coordLabel.getStyle().set("font-weight", "bold");
        content.add(coordLabel);

        FormLayout coordForm = new FormLayout();
        coordForm.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("400px", 2)
        );

        xCoordinateField = new IntegerField("X-Koordinate");
        xCoordinateField.setMin(0);
        xCoordinateField.setWidthFull();

        yCoordinateField = new IntegerField("Y-Koordinate");
        yCoordinateField.setMin(0);
        yCoordinateField.setWidthFull();

        maxIngotsField = new IntegerField("Max. Barren");
        maxIngotsField.setMin(1);
        maxIngotsField.setMax(20);
        maxIngotsField.setWidthFull();
        maxIngotsField.setHelperText("Maximale Stapelhöhe");

        coordForm.add(xCoordinateField, yCoordinateField, maxIngotsField);
        content.add(coordForm, new Hr());

        // === Position (mm) ===
        Span posLabel = new Span("Position (mm)");
        posLabel.getStyle().set("font-weight", "bold");
        content.add(posLabel);

        FormLayout posForm = new FormLayout();
        posForm.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("300px", 3)
        );

        xPositionField = new IntegerField("X");
        xPositionField.setWidthFull();

        yPositionField = new IntegerField("Y");
        yPositionField.setWidthFull();

        zPositionField = new IntegerField("Z");
        zPositionField.setWidthFull();

        posForm.add(xPositionField, yPositionField, zPositionField);
        content.add(posForm, new Hr());

        // === Abmessungen (mm) ===
        Span dimLabel = new Span("Abmessungen (mm)");
        dimLabel.getStyle().set("font-weight", "bold");
        content.add(dimLabel);

        FormLayout dimForm = new FormLayout();
        dimForm.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("300px", 3)
        );

        lengthField = new IntegerField("Länge");
        lengthField.setMin(0);
        lengthField.setWidthFull();

        widthField = new IntegerField("Breite");
        widthField.setMin(0);
        widthField.setWidthFull();

        heightField = new IntegerField("Höhe");
        heightField.setMin(0);
        heightField.setWidthFull();

        dimForm.add(lengthField, widthField, heightField);
        content.add(dimForm, new Hr());

        // === Einschränkungen ===
        Span restrictLabel = new Span("Berechtigungen");
        restrictLabel.getStyle().set("font-weight", "bold");
        content.add(restrictLabel);

        HorizontalLayout restrictLayout = new HorizontalLayout();
        restrictLayout.setSpacing(true);

        toStockAllowedCheckbox = new Checkbox("Einlagern erlaubt");
        fromStockAllowedCheckbox = new Checkbox("Auslagern erlaubt");

        restrictLayout.add(toStockAllowedCheckbox, fromStockAllowedCheckbox);
        content.add(restrictLayout);

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

    private void populateFields() {
        if (stockyard.getYardNumber() != null) {
            yardNumberField.setValue(stockyard.getYardNumber());
        }
        if (stockyard.getDescription() != null) {
            descriptionField.setValue(stockyard.getDescription());
        }
        if (stockyard.getType() != null) {
            typeComboBox.setValue(stockyard.getType());
        }
        if (stockyard.getUsage() != null) {
            usageComboBox.setValue(stockyard.getUsage());
        }
        xCoordinateField.setValue(stockyard.getXCoordinate());
        yCoordinateField.setValue(stockyard.getYCoordinate());
        maxIngotsField.setValue(stockyard.getMaxIngots());
        xPositionField.setValue(stockyard.getXPosition());
        yPositionField.setValue(stockyard.getYPosition());
        zPositionField.setValue(stockyard.getZPosition());
        lengthField.setValue(stockyard.getLength());
        widthField.setValue(stockyard.getWidth());
        heightField.setValue(stockyard.getHeight());
        toStockAllowedCheckbox.setValue(stockyard.isToStockAllowed());
        fromStockAllowedCheckbox.setValue(stockyard.isFromStockAllowed());
    }

    private void save() {
        // Validierung
        String yardNumber = yardNumberField.getValue();
        if (yardNumber == null || yardNumber.trim().isEmpty()) {
            Notification.show("Bitte Platznummer eingeben", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        if (typeComboBox.getValue() == null) {
            Notification.show("Bitte Typ auswählen", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        // Werte übernehmen
        stockyard.setYardNumber(yardNumber.trim());
        stockyard.setDescription(descriptionField.getValue());
        stockyard.setType(typeComboBox.getValue());
        stockyard.setUsage(usageComboBox.getValue());
        stockyard.setXCoordinate(xCoordinateField.getValue() != null ? xCoordinateField.getValue() : 0);
        stockyard.setYCoordinate(yCoordinateField.getValue() != null ? yCoordinateField.getValue() : 0);
        stockyard.setMaxIngots(maxIngotsField.getValue() != null ? maxIngotsField.getValue() : 1);
        stockyard.setXPosition(xPositionField.getValue() != null ? xPositionField.getValue() : 0);
        stockyard.setYPosition(yPositionField.getValue() != null ? yPositionField.getValue() : 0);
        stockyard.setZPosition(zPositionField.getValue() != null ? zPositionField.getValue() : 0);
        stockyard.setLength(lengthField.getValue() != null ? lengthField.getValue() : 0);
        stockyard.setWidth(widthField.getValue() != null ? widthField.getValue() : 0);
        stockyard.setHeight(heightField.getValue() != null ? heightField.getValue() : 0);
        stockyard.setToStockAllowed(toStockAllowedCheckbox.getValue());
        stockyard.setFromStockAllowed(fromStockAllowedCheckbox.getValue());

        // Callback aufrufen
        if (onSave != null) {
            onSave.accept(stockyard);
        }

        Notification.show("Gespeichert", 2000, Notification.Position.BOTTOM_START)
            .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        close();
    }

    public void setOnSave(Consumer<StockyardDTO> onSave) {
        this.onSave = onSave;
    }
}
