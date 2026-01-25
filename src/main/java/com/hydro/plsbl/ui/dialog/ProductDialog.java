package com.hydro.plsbl.ui.dialog;

import com.hydro.plsbl.dto.ProductDTO;
import com.hydro.plsbl.service.ProductService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
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
 * Dialog zur Verwaltung von Produkten/Artikeln
 */
public class ProductDialog extends Dialog {

    private final ProductService productService;

    private Grid<ProductDTO> grid;
    private TextField searchField;

    // Formular-Felder
    private TextField productNoField;
    private TextField descriptionField;
    private IntegerField maxPerLocationField;

    private ProductDTO selectedProduct;
    private boolean isEditMode = false;

    public ProductDialog(ProductService productService) {
        this.productService = productService;

        setHeaderTitle("Artikel/Produkte verwalten");
        setWidth("900px");
        setHeight("600px");
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
        listLayout.setWidth("55%");
        listLayout.setPadding(false);
        listLayout.setSpacing(true);

        // Suchfeld und Neu-Button
        HorizontalLayout toolbar = new HorizontalLayout();
        toolbar.setWidthFull();
        toolbar.setSpacing(true);
        toolbar.setAlignItems(FlexComponent.Alignment.BASELINE);

        searchField = new TextField();
        searchField.setPlaceholder("Suchen...");
        searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchField.setClearButtonVisible(true);
        searchField.setWidth("200px");
        searchField.addValueChangeListener(e -> filterData());

        Button newButton = new Button("Neu", VaadinIcon.PLUS.create());
        newButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        newButton.addClickListener(e -> newProduct());

        toolbar.add(searchField, newButton);
        toolbar.expand(searchField);

        // Grid
        grid = new Grid<>(ProductDTO.class, false);
        grid.addColumn(ProductDTO::getProductNo).setHeader("Artikel-Nr.").setWidth("180px").setFlexGrow(0);
        grid.addColumn(ProductDTO::getDescription).setHeader("Beschreibung").setFlexGrow(1);
        grid.addColumn(ProductDTO::getMaxPerLocation).setHeader("Max/Platz").setWidth("90px").setFlexGrow(0);
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        grid.setSelectionMode(Grid.SelectionMode.SINGLE);
        grid.addSelectionListener(e -> {
            e.getFirstSelectedItem().ifPresentOrElse(
                this::selectProduct,
                this::clearForm
            );
        });

        listLayout.add(toolbar, grid);
        listLayout.expand(grid);

        // === Rechte Seite: Formular ===
        VerticalLayout formLayout = new VerticalLayout();
        formLayout.setWidth("45%");
        formLayout.setPadding(true);
        formLayout.setSpacing(true);
        formLayout.getStyle()
            .set("background-color", "var(--lumo-contrast-5pct)")
            .set("border-radius", "var(--lumo-border-radius-m)");

        Span formTitle = new Span("Details");
        formTitle.getStyle().set("font-weight", "bold").set("font-size", "var(--lumo-font-size-l)");

        FormLayout form = new FormLayout();
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        productNoField = new TextField("Artikel-Nr.");
        productNoField.setRequired(true);
        productNoField.setMaxLength(50);
        productNoField.setWidthFull();
        productNoField.setHelperText("Pflichtfeld");

        descriptionField = new TextField("Beschreibung");
        descriptionField.setWidthFull();

        maxPerLocationField = new IntegerField("Max. pro Lagerplatz");
        maxPerLocationField.setMin(1);
        maxPerLocationField.setMax(20);
        maxPerLocationField.setValue(8);
        maxPerLocationField.setWidthFull();
        maxPerLocationField.setHelperText("Maximale Anzahl Barren pro Platz");

        form.add(productNoField, descriptionField, maxPerLocationField);

        // Formular-Buttons
        HorizontalLayout formButtons = new HorizontalLayout();
        formButtons.setSpacing(true);
        formButtons.setWidthFull();
        formButtons.setJustifyContentMode(FlexComponent.JustifyContentMode.END);

        Button saveButton = new Button("Speichern", VaadinIcon.CHECK.create());
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveButton.addClickListener(e -> save());

        Button deleteButton = new Button("Löschen", VaadinIcon.TRASH.create());
        deleteButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        deleteButton.addClickListener(e -> confirmDelete());

        Button cancelButton = new Button("Abbrechen", VaadinIcon.CLOSE.create());
        cancelButton.addClickListener(e -> clearForm());

        formButtons.add(cancelButton, deleteButton, saveButton);

        formLayout.add(formTitle, new Hr(), form, formButtons);

        mainLayout.add(listLayout, formLayout);
        add(mainLayout);

        // Initial: Formular deaktivieren
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
        grid.setItems(productService.findAll());
    }

    private void filterData() {
        String search = searchField.getValue();
        if (search == null || search.isBlank()) {
            loadData();
        } else {
            grid.setItems(productService.searchByDescription(search));
        }
    }

    private void selectProduct(ProductDTO product) {
        this.selectedProduct = product;
        this.isEditMode = true;
        populateForm(product);
        setFormEnabled(true);
    }

    private void populateForm(ProductDTO product) {
        productNoField.setValue(product.getProductNo() != null ? product.getProductNo() : "");
        descriptionField.setValue(product.getDescription() != null ? product.getDescription() : "");
        maxPerLocationField.setValue(product.getMaxPerLocation() != null ? product.getMaxPerLocation() : 8);
    }

    private void clearForm() {
        this.selectedProduct = null;
        this.isEditMode = false;
        productNoField.clear();
        descriptionField.clear();
        maxPerLocationField.setValue(8);
        setFormEnabled(false);
        grid.deselectAll();
    }

    private void setFormEnabled(boolean enabled) {
        productNoField.setEnabled(enabled);
        descriptionField.setEnabled(enabled);
        maxPerLocationField.setEnabled(enabled);
    }

    private void newProduct() {
        this.selectedProduct = new ProductDTO();
        this.isEditMode = false;
        clearForm();
        setFormEnabled(true);
        productNoField.focus();
        grid.deselectAll();
    }

    private void save() {
        // Validierung
        String productNo = productNoField.getValue();
        if (productNo == null || productNo.trim().isEmpty()) {
            Notification.show("Bitte Artikel-Nr. eingeben", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            productNoField.focus();
            return;
        }

        // Werte übernehmen
        if (selectedProduct == null) {
            selectedProduct = new ProductDTO();
        }
        selectedProduct.setProductNo(productNo.trim());
        selectedProduct.setDescription(descriptionField.getValue());
        selectedProduct.setMaxPerLocation(maxPerLocationField.getValue());

        try {
            productService.save(selectedProduct);
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
        if (selectedProduct == null || selectedProduct.getId() == null) {
            return;
        }

        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Artikel löschen?");
        dialog.setText("Möchten Sie den Artikel '" + selectedProduct.getProductNo() + "' wirklich löschen?");
        dialog.setCancelable(true);
        dialog.setCancelText("Abbrechen");
        dialog.setConfirmText("Löschen");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(e -> delete());
        dialog.open();
    }

    private void delete() {
        if (selectedProduct == null || selectedProduct.getId() == null) {
            return;
        }

        try {
            productService.delete(selectedProduct.getId());
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
