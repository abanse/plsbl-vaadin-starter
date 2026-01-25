package com.hydro.plsbl.ui.dialog;

import com.hydro.plsbl.dto.IngotTypeDTO;
import com.hydro.plsbl.entity.enums.LengthType;
import com.hydro.plsbl.service.IngotTypeService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
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

/**
 * Dialog zur Verwaltung von Barrentypen
 */
public class IngotTypeDialog extends Dialog {

    private final IngotTypeService ingotTypeService;

    private Grid<IngotTypeDTO> grid;

    // Formular-Felder
    private TextField nameField;
    private TextField descriptionField;
    private ComboBox<LengthType> lengthTypeCombo;
    private Checkbox internalAllowedCheck;
    private Checkbox externalAllowedCheck;
    private Checkbox retrievalAllowedCheck;
    private Checkbox autoRetrievalCheck;
    private IntegerField minLengthField;
    private IntegerField maxLengthField;
    private IntegerField minWidthField;
    private IntegerField maxWidthField;
    private IntegerField minThicknessField;
    private IntegerField maxThicknessField;
    private IntegerField minWeightField;
    private IntegerField maxWeightField;
    private TextField productRegexField;
    private IntegerField priorityField;

    private IngotTypeDTO selectedType;

    public IngotTypeDialog(IngotTypeService ingotTypeService) {
        this.ingotTypeService = ingotTypeService;

        setHeaderTitle("Barrentypen verwalten");
        setWidth("1100px");
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
        HorizontalLayout mainLayout = new HorizontalLayout();
        mainLayout.setSizeFull();
        mainLayout.setSpacing(true);
        mainLayout.setPadding(false);

        // === Linke Seite: Liste ===
        VerticalLayout listLayout = new VerticalLayout();
        listLayout.setWidth("45%");
        listLayout.setPadding(false);
        listLayout.setSpacing(true);

        // Toolbar
        HorizontalLayout toolbar = new HorizontalLayout();
        toolbar.setWidthFull();
        toolbar.setSpacing(true);

        Button newButton = new Button("Neu", VaadinIcon.PLUS.create());
        newButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        newButton.addClickListener(e -> newType());

        toolbar.add(newButton);

        // Grid
        grid = new Grid<>(IngotTypeDTO.class, false);
        grid.addColumn(IngotTypeDTO::getName).setHeader("Name").setWidth("100px").setFlexGrow(0);
        grid.addColumn(dto -> dto.getLengthType() != null ? dto.getLengthType().getDisplayName() : "-")
            .setHeader("Typ").setWidth("80px").setFlexGrow(0);
        grid.addColumn(IngotTypeDTO::getLengthRange).setHeader("Länge").setFlexGrow(1);
        grid.addColumn(dto -> dto.getInternalAllowed() != null && dto.getInternalAllowed() ? "Ja" : "Nein")
            .setHeader("Intern").setWidth("60px").setFlexGrow(0);
        grid.addColumn(dto -> dto.getExternalAllowed() != null && dto.getExternalAllowed() ? "Ja" : "Nein")
            .setHeader("Extern").setWidth("60px").setFlexGrow(0);
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        grid.setSelectionMode(Grid.SelectionMode.SINGLE);
        grid.addSelectionListener(e -> e.getFirstSelectedItem().ifPresentOrElse(
            this::selectType,
            this::clearForm
        ));

        listLayout.add(toolbar, grid);
        listLayout.expand(grid);

        // === Rechte Seite: Formular ===
        VerticalLayout formContainer = new VerticalLayout();
        formContainer.setWidth("55%");
        formContainer.setPadding(false);
        formContainer.setSpacing(false);
        formContainer.getStyle()
            .set("background-color", "var(--lumo-contrast-5pct)")
            .set("border-radius", "var(--lumo-border-radius-m)");

        // Scrollbarer Bereich für Formularfelder
        VerticalLayout formLayout = new VerticalLayout();
        formLayout.setPadding(true);
        formLayout.setSpacing(true);
        formLayout.getStyle()
            .set("overflow-y", "auto")
            .set("flex-grow", "1");

        Span formTitle = new Span("Details");
        formTitle.getStyle().set("font-weight", "bold").set("font-size", "var(--lumo-font-size-l)");
        formLayout.add(formTitle, new Hr());

        // Allgemein
        FormLayout generalForm = new FormLayout();
        generalForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));

        nameField = new TextField("Name");
        nameField.setRequired(true);
        nameField.setMaxLength(20);

        descriptionField = new TextField("Beschreibung");

        lengthTypeCombo = new ComboBox<>("Längentyp");
        lengthTypeCombo.setItems(LengthType.values());
        lengthTypeCombo.setItemLabelGenerator(LengthType::getDisplayName);
        lengthTypeCombo.setRequired(true);

        priorityField = new IntegerField("Priorität");
        priorityField.setMin(0);
        priorityField.setMax(999);
        priorityField.setHelperText("0 = höchste");

        generalForm.add(nameField, lengthTypeCombo, descriptionField, priorityField);
        formLayout.add(generalForm, new Hr());

        // Berechtigungen
        Span permTitle = new Span("Berechtigungen");
        permTitle.getStyle().set("font-weight", "bold");
        formLayout.add(permTitle);

        HorizontalLayout permLayout = new HorizontalLayout();
        permLayout.setSpacing(true);
        permLayout.setWidthFull();

        internalAllowedCheck = new Checkbox("Intern erlaubt");
        externalAllowedCheck = new Checkbox("Extern erlaubt");
        retrievalAllowedCheck = new Checkbox("Auslagerbar");
        autoRetrievalCheck = new Checkbox("Auto-Auslagerung");

        permLayout.add(internalAllowedCheck, externalAllowedCheck, retrievalAllowedCheck, autoRetrievalCheck);
        formLayout.add(permLayout, new Hr());

        // Länge
        Span lengthTitle = new Span("Länge (mm)");
        lengthTitle.getStyle().set("font-weight", "bold");
        formLayout.add(lengthTitle);

        HorizontalLayout lengthLayout = new HorizontalLayout();
        minLengthField = new IntegerField("Min");
        minLengthField.setMin(0);
        minLengthField.setWidth("120px");
        maxLengthField = new IntegerField("Max");
        maxLengthField.setMin(0);
        maxLengthField.setWidth("120px");
        lengthLayout.add(minLengthField, maxLengthField);
        formLayout.add(lengthLayout);

        // Breite
        Span widthTitle = new Span("Breite (mm)");
        widthTitle.getStyle().set("font-weight", "bold");
        formLayout.add(widthTitle);

        HorizontalLayout widthLayout = new HorizontalLayout();
        minWidthField = new IntegerField("Min");
        minWidthField.setMin(0);
        minWidthField.setWidth("120px");
        maxWidthField = new IntegerField("Max");
        maxWidthField.setMin(0);
        maxWidthField.setWidth("120px");
        widthLayout.add(minWidthField, maxWidthField);
        formLayout.add(widthLayout);

        // Dicke
        Span thicknessTitle = new Span("Dicke (mm)");
        thicknessTitle.getStyle().set("font-weight", "bold");
        formLayout.add(thicknessTitle);

        HorizontalLayout thicknessLayout = new HorizontalLayout();
        minThicknessField = new IntegerField("Min");
        minThicknessField.setMin(0);
        minThicknessField.setWidth("120px");
        maxThicknessField = new IntegerField("Max");
        maxThicknessField.setMin(0);
        maxThicknessField.setWidth("120px");
        thicknessLayout.add(minThicknessField, maxThicknessField);
        formLayout.add(thicknessLayout);

        // Gewicht
        Span weightTitle = new Span("Gewicht (kg)");
        weightTitle.getStyle().set("font-weight", "bold");
        formLayout.add(weightTitle);

        HorizontalLayout weightLayout = new HorizontalLayout();
        minWeightField = new IntegerField("Min");
        minWeightField.setMin(0);
        minWeightField.setWidth("120px");
        maxWeightField = new IntegerField("Max");
        maxWeightField.setMin(0);
        maxWeightField.setWidth("120px");
        weightLayout.add(minWeightField, maxWeightField);
        formLayout.add(weightLayout, new Hr());

        // Regex
        productRegexField = new TextField("Produkt-Regex");
        productRegexField.setWidthFull();
        productRegexField.setHelperText("Regulärer Ausdruck für Produktnummer-Matching");
        formLayout.add(productRegexField);

        // Formular-Buttons (außerhalb des scrollbaren Bereichs)
        HorizontalLayout formButtons = new HorizontalLayout();
        formButtons.setSpacing(true);
        formButtons.setWidthFull();
        formButtons.setPadding(true);
        formButtons.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        formButtons.getStyle()
            .set("border-top", "1px solid var(--lumo-contrast-10pct)")
            .set("background-color", "var(--lumo-contrast-5pct)");

        Button saveButton = new Button("Speichern", VaadinIcon.CHECK.create());
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveButton.addClickListener(e -> save());

        Button deleteButton = new Button("Löschen", VaadinIcon.TRASH.create());
        deleteButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        deleteButton.addClickListener(e -> confirmDelete());

        Button cancelButton = new Button("Abbrechen", VaadinIcon.CLOSE.create());
        cancelButton.addClickListener(e -> clearForm());

        formButtons.add(cancelButton, deleteButton, saveButton);

        // FormContainer: Formularfelder (scrollbar) + Buttons (fixiert)
        formContainer.add(formLayout, formButtons);
        formContainer.expand(formLayout);

        mainLayout.add(listLayout, formContainer);
        add(mainLayout);

        setFormEnabled(false);
    }

    private void createFooter() {
        Button closeButton = new Button("Schließen", VaadinIcon.CLOSE.create());
        closeButton.addClickListener(e -> close());

        HorizontalLayout footer = new HorizontalLayout(closeButton);
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        footer.setWidthFull();
        getFooter().add(footer);
    }

    private void loadData() {
        grid.setItems(ingotTypeService.findAll());
    }

    private void selectType(IngotTypeDTO type) {
        this.selectedType = type;
        populateForm(type);
        setFormEnabled(true);
    }

    private void populateForm(IngotTypeDTO type) {
        nameField.setValue(type.getName() != null ? type.getName() : "");
        descriptionField.setValue(type.getDescription() != null ? type.getDescription() : "");
        lengthTypeCombo.setValue(type.getLengthType());
        priorityField.setValue(type.getPriority());
        internalAllowedCheck.setValue(type.getInternalAllowed() != null && type.getInternalAllowed());
        externalAllowedCheck.setValue(type.getExternalAllowed() != null && type.getExternalAllowed());
        retrievalAllowedCheck.setValue(type.getRetrievalAllowed() != null && type.getRetrievalAllowed());
        autoRetrievalCheck.setValue(type.getAutoRetrieval() != null && type.getAutoRetrieval());
        minLengthField.setValue(type.getMinLength());
        maxLengthField.setValue(type.getMaxLength());
        minWidthField.setValue(type.getMinWidth());
        maxWidthField.setValue(type.getMaxWidth());
        minThicknessField.setValue(type.getMinThickness());
        maxThicknessField.setValue(type.getMaxThickness());
        minWeightField.setValue(type.getMinWeight());
        maxWeightField.setValue(type.getMaxWeight());
        productRegexField.setValue(type.getProductRegex() != null ? type.getProductRegex() : "");
    }

    private void clearForm() {
        this.selectedType = null;
        nameField.clear();
        descriptionField.clear();
        lengthTypeCombo.clear();
        priorityField.clear();
        internalAllowedCheck.setValue(false);
        externalAllowedCheck.setValue(false);
        retrievalAllowedCheck.setValue(false);
        autoRetrievalCheck.setValue(false);
        minLengthField.clear();
        maxLengthField.clear();
        minWidthField.clear();
        maxWidthField.clear();
        minThicknessField.clear();
        maxThicknessField.clear();
        minWeightField.clear();
        maxWeightField.clear();
        productRegexField.clear();
        setFormEnabled(false);
        grid.deselectAll();
    }

    private void setFormEnabled(boolean enabled) {
        nameField.setEnabled(enabled);
        descriptionField.setEnabled(enabled);
        lengthTypeCombo.setEnabled(enabled);
        priorityField.setEnabled(enabled);
        internalAllowedCheck.setEnabled(enabled);
        externalAllowedCheck.setEnabled(enabled);
        retrievalAllowedCheck.setEnabled(enabled);
        autoRetrievalCheck.setEnabled(enabled);
        minLengthField.setEnabled(enabled);
        maxLengthField.setEnabled(enabled);
        minWidthField.setEnabled(enabled);
        maxWidthField.setEnabled(enabled);
        minThicknessField.setEnabled(enabled);
        maxThicknessField.setEnabled(enabled);
        minWeightField.setEnabled(enabled);
        maxWeightField.setEnabled(enabled);
        productRegexField.setEnabled(enabled);
    }

    private void newType() {
        this.selectedType = new IngotTypeDTO();
        clearForm();
        setFormEnabled(true);
        nameField.focus();
        grid.deselectAll();

        // Defaults
        internalAllowedCheck.setValue(true);
        externalAllowedCheck.setValue(true);
        retrievalAllowedCheck.setValue(true);
        priorityField.setValue(0);
    }

    private void save() {
        // Validierung
        String name = nameField.getValue();
        if (name == null || name.trim().isEmpty()) {
            Notification.show("Bitte Name eingeben", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            nameField.focus();
            return;
        }

        if (lengthTypeCombo.getValue() == null) {
            Notification.show("Bitte Längentyp auswählen", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        // Werte übernehmen
        if (selectedType == null) {
            selectedType = new IngotTypeDTO();
        }
        selectedType.setName(name.trim().toUpperCase());
        selectedType.setDescription(descriptionField.getValue());
        selectedType.setLengthType(lengthTypeCombo.getValue());
        selectedType.setPriority(priorityField.getValue());
        selectedType.setInternalAllowed(internalAllowedCheck.getValue());
        selectedType.setExternalAllowed(externalAllowedCheck.getValue());
        selectedType.setRetrievalAllowed(retrievalAllowedCheck.getValue());
        selectedType.setAutoRetrieval(autoRetrievalCheck.getValue());
        selectedType.setMinLength(minLengthField.getValue());
        selectedType.setMaxLength(maxLengthField.getValue());
        selectedType.setMinWidth(minWidthField.getValue());
        selectedType.setMaxWidth(maxWidthField.getValue());
        selectedType.setMinThickness(minThicknessField.getValue());
        selectedType.setMaxThickness(maxThicknessField.getValue());
        selectedType.setMinWeight(minWeightField.getValue());
        selectedType.setMaxWeight(maxWeightField.getValue());
        selectedType.setProductRegex(productRegexField.getValue());

        try {
            ingotTypeService.save(selectedType);
            Notification.show("Gespeichert", 2000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            loadData();
            clearForm();
        } catch (Exception e) {
            Notification.show("Fehler: " + e.getMessage(), 5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void confirmDelete() {
        if (selectedType == null || selectedType.getId() == null) {
            return;
        }

        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Barrentyp löschen?");
        dialog.setText("Möchten Sie den Barrentyp '" + selectedType.getName() + "' wirklich löschen?");
        dialog.setCancelable(true);
        dialog.setCancelText("Abbrechen");
        dialog.setConfirmText("Löschen");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(e -> delete());
        dialog.open();
    }

    private void delete() {
        if (selectedType == null || selectedType.getId() == null) {
            return;
        }

        try {
            ingotTypeService.delete(selectedType.getId());
            Notification.show("Gelöscht", 2000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            loadData();
            clearForm();
        } catch (Exception e) {
            Notification.show("Fehler: " + e.getMessage(), 5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
}
