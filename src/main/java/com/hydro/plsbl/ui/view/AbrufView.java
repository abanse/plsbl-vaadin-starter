package com.hydro.plsbl.ui.view;

import com.hydro.plsbl.dto.CalloffDTO;
import com.hydro.plsbl.dto.CalloffSearchCriteria;
import com.hydro.plsbl.service.CalloffService;
import com.hydro.plsbl.ui.MainLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Abruf-Ansicht - Übersicht aller Kundenbestellungen (Calloffs)
 *
 * Abrufe kommen über REST API rein und werden nach Alter abgearbeitet.
 * Ein Abruf kann mehrere Barren enthalten, die in Lieferungen (max. 6 Stück)
 * nach und nach ausgeliefert werden.
 */
@Route(value = "abrufe", layout = MainLayout.class)
@PageTitle("Abrufe | PLSBL")
public class AbrufView extends VerticalLayout {

    private static final Logger log = LoggerFactory.getLogger(AbrufView.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final CalloffService calloffService;

    // UI Components
    private Grid<CalloffDTO> grid;
    private Span countLabel;

    // Filter-Felder
    private TextField calloffNumberField;
    private TextField orderNumberField;
    private TextField customerNumberField;
    private TextField destinationField;
    private Checkbox incompleteOnlyCheckbox;
    private Checkbox approvedOnlyCheckbox;
    private Checkbox notApprovedOnlyCheckbox;

    // Aktions-Buttons
    private Button approveButton;
    private Button revokeButton;
    private Button completeButton;

    // Aktuell selektierter Abruf
    private CalloffDTO selectedCalloff;

    public AbrufView(CalloffService calloffService) {
        this.calloffService = calloffService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        createHeader();
        createFilterSection();
        createGrid();
        createActionBar();
        loadData();
    }

    private void createHeader() {
        H3 title = new H3("Abrufe-Übersicht");
        title.getStyle().set("margin", "0");

        countLabel = new Span("0");
        countLabel.getElement().getThemeList().add("badge");
        countLabel.getElement().getThemeList().add("contrast");

        Button refreshButton = new Button("Aktualisieren", VaadinIcon.REFRESH.create());
        refreshButton.addClickListener(e -> loadData());

        HorizontalLayout header = new HorizontalLayout(title, countLabel, refreshButton);
        header.setAlignItems(Alignment.CENTER);
        header.setWidthFull();
        header.expand(title);

        add(header);
    }

    private void createFilterSection() {
        // Filter-Container mit Hintergrund
        VerticalLayout filterSection = new VerticalLayout();
        filterSection.getStyle()
            .set("background-color", "#f5f5f5")
            .set("padding", "15px")
            .set("border-radius", "4px")
            .set("margin-bottom", "10px");
        filterSection.setSpacing(true);
        filterSection.setPadding(false);

        // Zeile 1: Text-Filter
        HorizontalLayout row1 = new HorizontalLayout();
        row1.setSpacing(true);
        row1.setAlignItems(Alignment.END);

        calloffNumberField = new TextField("Abruf-Nr.");
        calloffNumberField.setPlaceholder("z.B. ABR-2025");
        calloffNumberField.setWidth("130px");
        calloffNumberField.setClearButtonVisible(true);

        orderNumberField = new TextField("Auftrags-Nr.");
        orderNumberField.setPlaceholder("z.B. 4500001234");
        orderNumberField.setWidth("130px");
        orderNumberField.setClearButtonVisible(true);

        customerNumberField = new TextField("Kunden-Nr.");
        customerNumberField.setPlaceholder("z.B. 100123");
        customerNumberField.setWidth("100px");
        customerNumberField.setClearButtonVisible(true);

        destinationField = new TextField("Lieferort");
        destinationField.setPlaceholder("z.B. NF2");
        destinationField.setWidth("80px");
        destinationField.setClearButtonVisible(true);

        row1.add(calloffNumberField, orderNumberField, customerNumberField, destinationField);

        // Zeile 2: Checkboxen und Buttons
        HorizontalLayout row2 = new HorizontalLayout();
        row2.setSpacing(true);
        row2.setAlignItems(Alignment.CENTER);
        row2.setWidthFull();

        incompleteOnlyCheckbox = new Checkbox("nur offene Abrufe");
        incompleteOnlyCheckbox.setValue(true);

        approvedOnlyCheckbox = new Checkbox("nur genehmigte");
        approvedOnlyCheckbox.setValue(false);

        notApprovedOnlyCheckbox = new Checkbox("nur nicht genehmigte");
        notApprovedOnlyCheckbox.setValue(false);

        // Gegenseitiger Ausschluss
        approvedOnlyCheckbox.addValueChangeListener(e -> {
            if (e.getValue()) notApprovedOnlyCheckbox.setValue(false);
        });
        notApprovedOnlyCheckbox.addValueChangeListener(e -> {
            if (e.getValue()) approvedOnlyCheckbox.setValue(false);
        });

        HorizontalLayout checkboxes = new HorizontalLayout(
            incompleteOnlyCheckbox, approvedOnlyCheckbox, notApprovedOnlyCheckbox);
        checkboxes.setSpacing(true);

        // Spacer
        Span spacer = new Span();
        spacer.getStyle().set("flex-grow", "1");

        // Such-Buttons
        Button searchButton = new Button("Suchen", VaadinIcon.SEARCH.create());
        searchButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        searchButton.addClickListener(e -> loadData());

        Button resetButton = new Button("Zurücksetzen", VaadinIcon.CLOSE.create());
        resetButton.addClickListener(e -> resetFilters());

        row2.add(checkboxes, spacer, searchButton, resetButton);

        filterSection.add(row1, row2);
        add(filterSection);
    }

    private void createGrid() {
        grid = new Grid<>();
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);

        // Spalten wie im Bild
        grid.addColumn(CalloffDTO::getCalloffNumber)
            .setHeader("AbrufNr.")
            .setWidth("120px")
            .setFlexGrow(0)
            .setSortable(true);

        grid.addColumn(dto -> dto.getReceived() != null
                ? dto.getReceived().format(DATETIME_FORMATTER) : "-")
            .setHeader("eingegangen")
            .setWidth("140px")
            .setFlexGrow(0)
            .setSortable(true);

        grid.addColumn(dto -> dto.getSapProductNumber() != null ? dto.getSapProductNumber() : "-")
            .setHeader("SAP-Artikel")
            .setWidth("100px")
            .setFlexGrow(0)
            .setSortable(true);

        grid.addColumn(CalloffDTO::getRemainingAmount)
            .setHeader("offen")
            .setWidth("60px")
            .setFlexGrow(0)
            .setSortable(true);

        grid.addColumn(dto -> dto.getAmountRequested() != null ? dto.getAmountRequested() : 0)
            .setHeader("bestellt")
            .setWidth("70px")
            .setFlexGrow(0)
            .setSortable(true);

        grid.addColumn(dto -> dto.getDeliveryDate() != null
                ? dto.getDeliveryDate().format(DATE_FORMATTER) : "-")
            .setHeader("Liefertermin")
            .setWidth("100px")
            .setFlexGrow(0)
            .setSortable(true);

        grid.addColumn(dto -> dto.getDestination() != null ? dto.getDestination() : "-")
            .setHeader("Lieferort")
            .setWidth("80px")
            .setFlexGrow(0)
            .setSortable(true);

        grid.addColumn(CalloffDTO::getOrderDisplay)
            .setHeader("AuftragsNr.")
            .setWidth("120px")
            .setFlexGrow(0)
            .setSortable(true);

        // Freigabe-Badge
        grid.addComponentColumn(dto -> {
            Span badge = new Span(dto.isApproved() ? "Ja" : "Nein");
            badge.getStyle()
                .set("color", "white")
                .set("background-color", dto.isApproved() ? "#4CAF50" : "#9E9E9E")
                .set("padding", "2px 8px")
                .set("border-radius", "4px")
                .set("font-size", "11px");
            return badge;
        }).setHeader("Freigabe").setWidth("80px").setFlexGrow(0);

        // Status-Badge
        grid.addComponentColumn(dto -> {
            String status = dto.getStatusDisplay();
            String color;
            if (dto.isCompleted()) {
                color = "#2196F3"; // Blau - abgeschlossen
            } else if (dto.isApproved()) {
                color = "#4CAF50"; // Grün - genehmigt
            } else {
                color = "#FF9800"; // Orange - offen
            }
            Span badge = new Span(status);
            badge.getStyle()
                .set("color", "white")
                .set("background-color", color)
                .set("padding", "2px 8px")
                .set("border-radius", "4px")
                .set("font-size", "11px");
            return badge;
        }).setHeader("Status").setWidth("100px").setFlexGrow(0);

        // Kunden-Info (flexibel)
        grid.addColumn(dto -> {
            String customer = dto.getCustomerName() != null ? dto.getCustomerName() : "";
            if (dto.getCustomerNumber() != null) {
                customer = "[" + dto.getCustomerNumber() + "] " + customer;
            }
            return customer;
        }).setHeader("Kunde").setFlexGrow(1).setSortable(true);

        // Selektion
        grid.asSingleSelect().addValueChangeListener(e -> {
            selectedCalloff = e.getValue();
            updateActionButtons();
        });

        // Doppelklick für Details (TODO: Dialog)
        grid.addItemDoubleClickListener(event -> {
            CalloffDTO calloff = event.getItem();
            Notification.show("Details für: " + calloff.getCalloffNumber() +
                    "\nKunde: " + calloff.getCustomerName() +
                    "\nOffen: " + calloff.getRemainingAmount() + " / " + calloff.getAmountRequested(),
                5000, Notification.Position.MIDDLE);
        });

        add(grid);
        setFlexGrow(1, grid);
    }

    private void createActionBar() {
        HorizontalLayout actionBar = new HorizontalLayout();
        actionBar.setSpacing(true);
        actionBar.setWidthFull();
        actionBar.setJustifyContentMode(JustifyContentMode.START);

        approveButton = new Button("Genehmigen", VaadinIcon.CHECK.create());
        approveButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        approveButton.setEnabled(false);
        approveButton.addClickListener(e -> approveSelected());

        revokeButton = new Button("Widerrufen", VaadinIcon.CLOSE_SMALL.create());
        revokeButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        revokeButton.setEnabled(false);
        revokeButton.addClickListener(e -> revokeSelected());

        completeButton = new Button("Abschließen", VaadinIcon.CHECK_CIRCLE.create());
        completeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        completeButton.setEnabled(false);
        completeButton.addClickListener(e -> completeSelected());

        actionBar.add(approveButton, revokeButton, completeButton);
        add(actionBar);
    }

    private void loadData() {
        try {
            CalloffSearchCriteria criteria = buildSearchCriteria();
            List<CalloffDTO> calloffs = calloffService.searchByCriteria(criteria);
            grid.setItems(calloffs);
            countLabel.setText(String.valueOf(calloffs.size()));
            log.debug("Loaded {} calloffs", calloffs.size());
        } catch (Exception e) {
            log.error("Error loading calloffs", e);
            grid.setItems();
            Notification.show("Fehler beim Laden: " + e.getMessage(),
                5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private CalloffSearchCriteria buildSearchCriteria() {
        CalloffSearchCriteria criteria = new CalloffSearchCriteria();

        String calloffNo = calloffNumberField.getValue();
        if (calloffNo != null && !calloffNo.trim().isEmpty()) {
            criteria.setCalloffNumber(calloffNo.trim());
        }

        String orderNo = orderNumberField.getValue();
        if (orderNo != null && !orderNo.trim().isEmpty()) {
            criteria.setOrderNumber(orderNo.trim());
        }

        String customerNo = customerNumberField.getValue();
        if (customerNo != null && !customerNo.trim().isEmpty()) {
            criteria.setCustomerNumber(customerNo.trim());
        }

        String dest = destinationField.getValue();
        if (dest != null && !dest.trim().isEmpty()) {
            criteria.setDestination(dest.trim());
        }

        criteria.setIncompleteOnly(incompleteOnlyCheckbox.getValue());
        criteria.setApprovedOnly(approvedOnlyCheckbox.getValue());
        criteria.setNotApprovedOnly(notApprovedOnlyCheckbox.getValue());

        return criteria;
    }

    private void resetFilters() {
        calloffNumberField.clear();
        orderNumberField.clear();
        customerNumberField.clear();
        destinationField.clear();
        incompleteOnlyCheckbox.setValue(true);
        approvedOnlyCheckbox.setValue(false);
        notApprovedOnlyCheckbox.setValue(false);
        loadData();
    }

    private void updateActionButtons() {
        if (selectedCalloff == null) {
            approveButton.setEnabled(false);
            revokeButton.setEnabled(false);
            completeButton.setEnabled(false);
            return;
        }

        boolean isApproved = selectedCalloff.isApproved();
        boolean isCompleted = selectedCalloff.isCompleted();

        // Genehmigen: nur wenn nicht genehmigt und nicht abgeschlossen
        approveButton.setEnabled(!isApproved && !isCompleted);

        // Widerrufen: nur wenn genehmigt und nicht abgeschlossen
        revokeButton.setEnabled(isApproved && !isCompleted);

        // Abschließen: nur wenn genehmigt und nicht abgeschlossen
        completeButton.setEnabled(isApproved && !isCompleted);
    }

    private void approveSelected() {
        if (selectedCalloff == null) return;
        try {
            calloffService.approve(selectedCalloff.getId());
            Notification.show("Abruf " + selectedCalloff.getCalloffNumber() + " genehmigt",
                3000, Notification.Position.BOTTOM_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            loadData();
        } catch (Exception e) {
            log.error("Error approving calloff", e);
            Notification.show("Fehler: " + e.getMessage(),
                5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void revokeSelected() {
        if (selectedCalloff == null) return;
        try {
            calloffService.revokeApproval(selectedCalloff.getId());
            Notification.show("Genehmigung für " + selectedCalloff.getCalloffNumber() + " widerrufen",
                3000, Notification.Position.BOTTOM_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_CONTRAST);
            loadData();
        } catch (Exception e) {
            log.error("Error revoking calloff", e);
            Notification.show("Fehler: " + e.getMessage(),
                5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void completeSelected() {
        if (selectedCalloff == null) return;
        try {
            calloffService.complete(selectedCalloff.getId());
            Notification.show("Abruf " + selectedCalloff.getCalloffNumber() + " abgeschlossen",
                3000, Notification.Position.BOTTOM_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            loadData();
        } catch (Exception e) {
            log.error("Error completing calloff", e);
            Notification.show("Fehler: " + e.getMessage(),
                5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
}
