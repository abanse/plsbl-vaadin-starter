package com.hydro.plsbl.ui.dialog;

import com.hydro.plsbl.dto.CalloffDTO;
import com.hydro.plsbl.dto.CalloffSearchCriteria;
import com.hydro.plsbl.service.CalloffService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Dialog zur Suche und Anzeige von Abrufen (Calloffs)
 * Entspricht dem Original-Dialog "Abrufe suchen"
 */
public class AbrufeDialog extends Dialog {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    private final CalloffService calloffService;
    private final CalloffSearchCriteria criteria = new CalloffSearchCriteria();

    // Filter-Felder
    private TextField calloffNoField;
    private DateTimePicker deliveryDateFromPicker;
    private DateTimePicker deliveryDateToPicker;
    private TextField sapProductNoField;
    private TextField productNoField;
    private TextField orderNoField;
    private TextField customerNoField;
    private TextField destinationField;
    private TextField positionField;
    private TextField searchPatternField;
    private Checkbox incompleteOnlyCheckbox;
    private Checkbox completedOnlyCheckbox;
    private Checkbox notApprovedOnlyCheckbox;
    private Checkbox noDestinationCheckbox;

    // Ergebnis-Grid
    private Grid<CalloffDTO> grid;

    // Footer-Elemente
    private Span countLabel;
    private Span openSumLabel;
    private Span orderedSumLabel;

    public AbrufeDialog(CalloffService calloffService) {
        this.calloffService = calloffService;

        setHeaderTitle("Abrufe suchen");
        setWidth("1100px");
        setHeight("850px");
        setModal(true);
        setDraggable(true);
        setResizable(true);
        setCloseOnOutsideClick(false);

        createContent();
        createFooter();

        // Initial-Suche mit Standard-Kriterien (nur offene)
        doSearch();
    }

    private void createContent() {
        VerticalLayout content = new VerticalLayout();
        content.setPadding(true);
        content.setSpacing(true);
        content.setSizeFull();

        // Filter-Bereich
        content.add(createFilterSection());

        // Grid
        grid = createGrid();
        content.add(grid);
        content.setFlexGrow(1, grid);

        add(content);
    }

    private VerticalLayout createFilterSection() {
        VerticalLayout filterSection = new VerticalLayout();
        filterSection.setPadding(false);
        filterSection.setSpacing(true);

        // Zeile 1: Abruf-Nr und Checkboxen
        HorizontalLayout row1 = new HorizontalLayout();
        row1.setWidthFull();
        row1.setAlignItems(FlexComponent.Alignment.END);

        calloffNoField = new TextField("Abruf-Nr.");
        calloffNoField.setWidth("120px");

        // Checkboxen rechts
        VerticalLayout checkboxPanel = new VerticalLayout();
        checkboxPanel.setPadding(false);
        checkboxPanel.setSpacing(false);

        incompleteOnlyCheckbox = new Checkbox("nur offene Abrufe");
        incompleteOnlyCheckbox.setValue(true);
        incompleteOnlyCheckbox.addValueChangeListener(e -> {
            if (e.getValue()) {
                completedOnlyCheckbox.setValue(false);
            }
        });

        completedOnlyCheckbox = new Checkbox("nur erledigte Abrufe");
        completedOnlyCheckbox.addValueChangeListener(e -> {
            if (e.getValue()) {
                incompleteOnlyCheckbox.setValue(false);
            }
        });

        notApprovedOnlyCheckbox = new Checkbox("nur gesperrte Abrufe");

        checkboxPanel.add(incompleteOnlyCheckbox, completedOnlyCheckbox, notApprovedOnlyCheckbox);

        row1.add(calloffNoField);
        row1.setFlexGrow(1, calloffNoField);
        row1.add(checkboxPanel);

        // Zeile 2: Liefertermin ab/bis
        HorizontalLayout row2 = new HorizontalLayout();
        row2.setWidthFull();
        row2.setAlignItems(FlexComponent.Alignment.END);

        deliveryDateFromPicker = new DateTimePicker("Liefertermin ab");
        deliveryDateFromPicker.setWidth("230px");
        deliveryDateFromPicker.setLocale(Locale.GERMANY);

        deliveryDateToPicker = new DateTimePicker("Liefertermin bis");
        deliveryDateToPicker.setWidth("230px");
        deliveryDateToPicker.setLocale(Locale.GERMANY);

        row2.add(deliveryDateFromPicker, deliveryDateToPicker);

        // Zeile 3: SAP Artikel-Nr und Artikel
        HorizontalLayout row3 = new HorizontalLayout();
        row3.setWidthFull();

        sapProductNoField = new TextField("SAP Artikel-Nr.");
        sapProductNoField.setWidth("150px");

        productNoField = new TextField("Artikel");
        productNoField.setWidth("300px");

        positionField = new TextField("Position");
        positionField.setWidth("80px");

        row3.add(sapProductNoField, productNoField, positionField);

        // Zeile 4: Auftrags-Nr, Kunden-Nr
        HorizontalLayout row4 = new HorizontalLayout();
        row4.setWidthFull();

        orderNoField = new TextField("Auftrags-Nr.");
        orderNoField.setWidth("150px");

        customerNoField = new TextField("Kunden-Nr.");
        customerNoField.setWidth("150px");

        row4.add(orderNoField, customerNoField);

        // Zeile 5: Lieferort, kein Lieferort
        HorizontalLayout row5 = new HorizontalLayout();
        row5.setWidthFull();
        row5.setAlignItems(FlexComponent.Alignment.END);

        destinationField = new TextField("Lieferort");
        destinationField.setWidth("80px");

        noDestinationCheckbox = new Checkbox("kein Lieferort");
        noDestinationCheckbox.addValueChangeListener(e -> {
            destinationField.setEnabled(!e.getValue());
        });

        row5.add(destinationField, noDestinationCheckbox);

        // Zeile 6: Suchmuster
        HorizontalLayout row6 = new HorizontalLayout();
        row6.setWidthFull();

        searchPatternField = new TextField("Suchmuster");
        searchPatternField.setWidth("400px");

        row6.add(searchPatternField);

        filterSection.add(row1, row2, row3, row4, row5, row6);
        return filterSection;
    }

    private Grid<CalloffDTO> createGrid() {
        Grid<CalloffDTO> g = new Grid<>();
        g.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        g.setWidthFull();
        g.setMinHeight("350px");  // Mindesthöhe für das Grid

        // AbrufNr
        g.addColumn(CalloffDTO::getCalloffNumber)
            .setHeader("AbrufNr.")
            .setWidth("90px")
            .setSortable(true)
            .setFlexGrow(0);

        // eingegangen (received)
        g.addColumn(dto -> dto.getReceived() != null ? dto.getReceived().format(DATE_TIME_FORMAT) : "")
            .setHeader("eingegangen")
            .setWidth("150px")
            .setSortable(true)
            .setFlexGrow(0);

        // SAP-Artikel
        g.addColumn(CalloffDTO::getSapProductNumber)
            .setHeader("SAP-Artikel")
            .setWidth("100px")
            .setSortable(true)
            .setFlexGrow(0);

        // Artikel (verwende auch SAP-Produkt als Artikel, da kein separates Feld)
        g.addColumn(dto -> {
            // Wenn wir das Produkt aus MD_PRODUCT haben, verwende das, sonst SAP-Nummer
            return dto.getSapProductNumber();
        })
            .setHeader("Artikel")
            .setWidth("200px")
            .setSortable(true)
            .setFlexGrow(1);

        // offen (Restmenge)
        g.addColumn(CalloffDTO::getRemainingAmount)
            .setHeader("offen")
            .setWidth("70px")
            .setSortable(true)
            .setFlexGrow(0);

        // bestellt (Bestellmenge)
        g.addColumn(dto -> dto.getAmountRequested() != null ? dto.getAmountRequested() : 0)
            .setHeader("bestellt")
            .setWidth("70px")
            .setSortable(true)
            .setFlexGrow(0);

        // Liefertermin
        g.addColumn(dto -> dto.getDeliveryDate() != null ?
                dto.getDeliveryDate().atStartOfDay().format(DATE_FORMAT) : "")
            .setHeader("Liefertermin")
            .setWidth("150px")
            .setSortable(true)
            .setFlexGrow(0);

        // Lieferort
        g.addColumn(CalloffDTO::getDestination)
            .setHeader("Lieferort")
            .setWidth("80px")
            .setSortable(true)
            .setFlexGrow(0);

        // AuftragsNr
        g.addColumn(CalloffDTO::getOrderNumber)
            .setHeader("AuftragsNr.")
            .setWidth("100px")
            .setSortable(true)
            .setFlexGrow(0);

        // Freigabe (Checkmark-Icon)
        g.addColumn(new ComponentRenderer<>(dto -> {
            if (dto.isApproved()) {
                Icon check = VaadinIcon.CHECK.create();
                check.setColor("green");
                check.setSize("16px");
                return check;
            } else {
                return new Span("");
            }
        }))
            .setHeader("Freigabe")
            .setWidth("80px")
            .setFlexGrow(0);

        return g;
    }

    private void createFooter() {
        HorizontalLayout footer = new HorizontalLayout();
        footer.setWidthFull();
        footer.setAlignItems(FlexComponent.Alignment.CENTER);

        // Linker Bereich: Zähler
        HorizontalLayout leftSection = new HorizontalLayout();
        leftSection.setAlignItems(FlexComponent.Alignment.CENTER);

        Icon listIcon = VaadinIcon.LIST.create();
        listIcon.setSize("16px");
        countLabel = new Span("0");
        countLabel.getStyle().set("min-width", "40px");

        Button filterButton = new Button("filtern", VaadinIcon.FILTER.create());
        filterButton.addClickListener(e -> resetFilter());

        leftSection.add(listIcon, countLabel, filterButton);

        // Mittlerer Bereich: Summen
        HorizontalLayout middleSection = new HorizontalLayout();
        middleSection.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);

        openSumLabel = new Span("0");
        openSumLabel.getStyle().set("min-width", "50px").set("text-align", "right");

        orderedSumLabel = new Span("0");
        orderedSumLabel.getStyle().set("min-width", "50px").set("text-align", "right");

        middleSection.add(openSumLabel, orderedSumLabel);

        // Rechter Bereich: Buttons
        HorizontalLayout rightSection = new HorizontalLayout();
        rightSection.setJustifyContentMode(FlexComponent.JustifyContentMode.END);

        Button searchButton = new Button("suchen", VaadinIcon.SEARCH.create());
        searchButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        searchButton.addClickListener(e -> doSearch());

        Button closeButton = new Button("schließen", VaadinIcon.CLOSE.create());
        closeButton.addClickListener(e -> close());

        rightSection.add(searchButton, closeButton);

        footer.add(leftSection, middleSection, rightSection);
        footer.setFlexGrow(1, middleSection);

        getFooter().add(footer);
    }

    private void resetFilter() {
        calloffNoField.clear();
        deliveryDateFromPicker.clear();
        deliveryDateToPicker.clear();
        sapProductNoField.clear();
        productNoField.clear();
        orderNoField.clear();
        customerNoField.clear();
        destinationField.clear();
        positionField.clear();
        searchPatternField.clear();
        incompleteOnlyCheckbox.setValue(true);
        completedOnlyCheckbox.setValue(false);
        notApprovedOnlyCheckbox.setValue(false);
        noDestinationCheckbox.setValue(false);
        destinationField.setEnabled(true);
    }

    private void doSearch() {
        // Kriterien aus Feldern übernehmen
        criteria.setCalloffNumber(calloffNoField.getValue());
        criteria.setDeliveryDateFrom(deliveryDateFromPicker.getValue());
        criteria.setDeliveryDateTo(deliveryDateToPicker.getValue());
        criteria.setSapProductNumber(sapProductNoField.getValue());
        criteria.setProductNumber(productNoField.getValue());
        criteria.setOrderNumber(orderNoField.getValue());
        criteria.setOrderPosition(positionField.getValue());
        criteria.setCustomerNumber(customerNoField.getValue());
        criteria.setDestination(destinationField.getValue());
        criteria.setNoDestination(noDestinationCheckbox.getValue());
        criteria.setSearchPattern(searchPatternField.getValue());
        criteria.setIncompleteOnly(incompleteOnlyCheckbox.getValue());
        criteria.setCompletedOnly(completedOnlyCheckbox.getValue());
        criteria.setNotApprovedOnly(notApprovedOnlyCheckbox.getValue());

        // Suche ausführen
        List<CalloffDTO> results = calloffService.searchByCriteria(criteria);
        grid.setItems(results);

        // Zähler und Summen aktualisieren
        updateSummary(results);
    }

    private void updateSummary(List<CalloffDTO> results) {
        countLabel.setText(String.valueOf(results.size()));

        int openSum = results.stream()
            .mapToInt(CalloffDTO::getRemainingAmount)
            .sum();

        int orderedSum = results.stream()
            .mapToInt(dto -> dto.getAmountRequested() != null ? dto.getAmountRequested() : 0)
            .sum();

        openSumLabel.setText(String.valueOf(openSum));
        orderedSumLabel.setText(String.valueOf(orderedSum));
    }
}
