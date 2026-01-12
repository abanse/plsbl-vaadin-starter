package com.hydro.plsbl.ui.dialog;

import com.hydro.plsbl.dto.IngotDTO;
import com.hydro.plsbl.dto.StockyardDTO;
import com.hydro.plsbl.service.StockyardService;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

/**
 * Edit-Dialog für Barren
 */
public class IngotEditDialog extends Dialog {

    private final IngotDTO ingot;
    private final boolean isNew;
    private final StockyardService stockyardService;
    private Consumer<IngotDTO> onSave;

    // Ursprüngliche Werte speichern um zu erkennen ob sich etwas geändert hat
    private Long originalStockyardId;
    private String originalStockyardNo;

    // Formular-Felder
    private TextField ingotNoField;
    private TextField productSuffixField;
    private ComboBox<StockyardDTO> stockyardComboBox;
    private IntegerField pilePositionField;
    private IntegerField weightField;
    private IntegerField lengthField;
    private IntegerField widthField;
    private IntegerField thicknessField;
    private Checkbox headSawnCheckbox;
    private Checkbox footSawnCheckbox;
    private Checkbox scrapCheckbox;
    private Checkbox revisedCheckbox;
    private Checkbox rotatedCheckbox;

    public IngotEditDialog(IngotDTO ingot, boolean isNew, StockyardService stockyardService) {
        this.ingot = ingot != null ? ingot : new IngotDTO();
        this.isNew = isNew;
        this.stockyardService = stockyardService;

        // Ursprüngliche Lagerort-Werte speichern
        this.originalStockyardId = this.ingot.getStockyardId();
        this.originalStockyardNo = this.ingot.getStockyardNo();

        setHeaderTitle(isNew ? "Neuer Barren" : "Barren " + this.ingot.getIngotNo() + " bearbeiten");
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

        // === Allgemeine Daten ===
        Span generalLabel = new Span("Allgemeine Daten");
        generalLabel.getStyle().set("font-weight", "bold");
        content.add(generalLabel);

        FormLayout generalForm = new FormLayout();
        generalForm.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("400px", 2)
        );

        ingotNoField = new TextField("Barren-Nr");
        ingotNoField.setRequired(true);
        ingotNoField.setMaxLength(20);
        ingotNoField.setWidthFull();
        ingotNoField.setHelperText("Pflichtfeld");

        productSuffixField = new TextField("Produkt-Suffix");
        productSuffixField.setMaxLength(10);
        productSuffixField.setWidthFull();

        generalForm.add(ingotNoField, productSuffixField);
        content.add(generalForm, new Hr());

        // === Lagerort ===
        Span locationLabel = new Span("Lagerort");
        locationLabel.getStyle().set("font-weight", "bold");
        content.add(locationLabel);

        FormLayout locationForm = new FormLayout();
        locationForm.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("400px", 2)
        );

        stockyardComboBox = new ComboBox<>("Lagerplatz");
        stockyardComboBox.setWidthFull();
        stockyardComboBox.setItemLabelGenerator(this::getStockyardLabel);
        stockyardComboBox.setClearButtonVisible(true);
        stockyardComboBox.setHelperText("Leer lassen = nicht auf Lager");

        // Lagerplatz-Auswahl deaktivieren - Lagerort wird über Umlagern geändert, nicht hier
        stockyardComboBox.setEnabled(false);
        stockyardComboBox.setHelperText("Lagerort: " + (originalStockyardNo != null ? originalStockyardNo : "nicht auf Lager"));

        pilePositionField = new IntegerField("Stapel-Position");
        pilePositionField.setMin(1);
        pilePositionField.setMax(10);
        pilePositionField.setStep(1);
        pilePositionField.setWidthFull();

        locationForm.add(stockyardComboBox, pilePositionField);
        content.add(locationForm, new Hr());

        // === Abmessungen ===
        Span dimensionsLabel = new Span("Abmessungen");
        dimensionsLabel.getStyle().set("font-weight", "bold");
        content.add(dimensionsLabel);

        FormLayout dimensionsForm = new FormLayout();
        dimensionsForm.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("400px", 2)
        );

        weightField = new IntegerField("Gewicht (kg)");
        weightField.setMin(0);
        weightField.setWidthFull();

        lengthField = new IntegerField("Länge (mm)");
        lengthField.setMin(0);
        lengthField.setWidthFull();

        widthField = new IntegerField("Breite (mm)");
        widthField.setMin(0);
        widthField.setWidthFull();

        thicknessField = new IntegerField("Dicke (mm)");
        thicknessField.setMin(0);
        thicknessField.setWidthFull();

        dimensionsForm.add(weightField, lengthField, widthField, thicknessField);
        content.add(dimensionsForm, new Hr());

        // === Status-Flags ===
        Span statusLabel = new Span("Status");
        statusLabel.getStyle().set("font-weight", "bold");
        content.add(statusLabel);

        HorizontalLayout statusFlags = new HorizontalLayout();
        statusFlags.setSpacing(true);
        statusFlags.getStyle().set("flex-wrap", "wrap");

        headSawnCheckbox = new Checkbox("Kopf gesägt");
        footSawnCheckbox = new Checkbox("Fuß gesägt");
        scrapCheckbox = new Checkbox("Schrott");
        revisedCheckbox = new Checkbox("Korrigiert");
        rotatedCheckbox = new Checkbox("Gedreht");

        statusFlags.add(headSawnCheckbox, footSawnCheckbox, scrapCheckbox, revisedCheckbox, rotatedCheckbox);
        content.add(statusFlags);

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

    private void populateFields() {
        if (ingot.getIngotNo() != null) {
            ingotNoField.setValue(ingot.getIngotNo());
        }
        if (ingot.getProductSuffix() != null) {
            productSuffixField.setValue(ingot.getProductSuffix());
        }
        // Lagerplatz wird nur angezeigt, nicht geändert - siehe Helper-Text
        if (ingot.getPilePosition() != null) {
            pilePositionField.setValue(ingot.getPilePosition());
        }
        if (ingot.getWeight() != null) {
            weightField.setValue(ingot.getWeight());
        }
        if (ingot.getLength() != null) {
            lengthField.setValue(ingot.getLength());
        }
        if (ingot.getWidth() != null) {
            widthField.setValue(ingot.getWidth());
        }
        if (ingot.getThickness() != null) {
            thicknessField.setValue(ingot.getThickness());
        }
        headSawnCheckbox.setValue(Boolean.TRUE.equals(ingot.getHeadSawn()));
        footSawnCheckbox.setValue(Boolean.TRUE.equals(ingot.getFootSawn()));
        scrapCheckbox.setValue(Boolean.TRUE.equals(ingot.getScrap()));
        revisedCheckbox.setValue(Boolean.TRUE.equals(ingot.getRevised()));
        rotatedCheckbox.setValue(Boolean.TRUE.equals(ingot.getRotated()));
    }

    private void save() {
        // Validierung
        String ingotNo = ingotNoField.getValue();
        if (ingotNo == null || ingotNo.trim().isEmpty()) {
            Notification.show("Bitte Barren-Nr eingeben", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        // Werte übernehmen
        ingot.setIngotNo(ingotNo.trim());
        ingot.setProductSuffix(productSuffixField.getValue());

        // Lagerort NICHT ändern - bleibt wie ursprünglich
        // Lagerort-Änderungen erfolgen über die Umlagern-Funktion
        ingot.setStockyardId(originalStockyardId);
        ingot.setStockyardNo(originalStockyardNo);
        ingot.setPilePosition(pilePositionField.getValue());

        // Abmessungen
        ingot.setWeight(weightField.getValue());
        ingot.setLength(lengthField.getValue());
        ingot.setWidth(widthField.getValue());
        ingot.setThickness(thicknessField.getValue());

        // Status-Flags
        ingot.setHeadSawn(headSawnCheckbox.getValue());
        ingot.setFootSawn(footSawnCheckbox.getValue());
        ingot.setScrap(scrapCheckbox.getValue());
        ingot.setRevised(revisedCheckbox.getValue());
        ingot.setRotated(rotatedCheckbox.getValue());

        // Callback aufrufen
        if (onSave != null) {
            onSave.accept(ingot);
        }

        Notification.show("Gespeichert", 2000, Notification.Position.BOTTOM_START)
            .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        close();
    }

    public void setOnSave(Consumer<IngotDTO> onSave) {
        this.onSave = onSave;
    }
}
