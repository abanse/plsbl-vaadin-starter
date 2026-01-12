package com.hydro.plsbl.ui.view;

import com.hydro.plsbl.dto.CraneCommandDTO;
import com.hydro.plsbl.dto.TransportOrderDTO;
import com.hydro.plsbl.service.CraneCommandService;
import com.hydro.plsbl.service.IngotService;
import com.hydro.plsbl.service.StockyardService;
import com.hydro.plsbl.service.TransportOrderService;
import com.hydro.plsbl.ui.MainLayout;
import com.hydro.plsbl.ui.dialog.CraneCommandDetailDialog;
import com.hydro.plsbl.ui.dialog.CraneCommandEditDialog;
import com.hydro.plsbl.ui.dialog.TransportOrderDetailDialog;
import com.hydro.plsbl.ui.dialog.TransportOrderEditDialog;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Aufträge-Ansicht - Kran-Kommandos und Transportaufträge
 */
@Route(value = "auftraege", layout = MainLayout.class)
@PageTitle("Aufträge | PLSBL")
public class AuftraegeView extends VerticalLayout {

    private static final Logger log = LoggerFactory.getLogger(AuftraegeView.class);

    private final CraneCommandService craneCommandService;
    private final TransportOrderService transportOrderService;
    private final IngotService ingotService;
    private final StockyardService stockyardService;

    private Grid<CraneCommandDTO> commandGrid;
    private Grid<TransportOrderDTO> orderGrid;
    private VerticalLayout commandContent;
    private VerticalLayout orderContent;

    private Span commandCountLabel;
    private Span orderCountLabel;

    public AuftraegeView(CraneCommandService craneCommandService, TransportOrderService transportOrderService,
                         IngotService ingotService, StockyardService stockyardService) {
        this.craneCommandService = craneCommandService;
        this.transportOrderService = transportOrderService;
        this.ingotService = ingotService;
        this.stockyardService = stockyardService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        createHeader();
        createTabs();
        loadData();
    }

    private void createHeader() {
        H3 title = new H3("Aufträge");
        title.getStyle().set("margin", "0");

        Button refreshButton = new Button("Aktualisieren", VaadinIcon.REFRESH.create());
        refreshButton.addClickListener(e -> loadData());

        HorizontalLayout header = new HorizontalLayout(title, refreshButton);
        header.setAlignItems(Alignment.CENTER);
        header.setWidthFull();
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);

        add(header);
    }

    private void createTabs() {
        // Tab-Labels mit Zählern
        commandCountLabel = new Span("0");
        commandCountLabel.getElement().getThemeList().add("badge small");

        orderCountLabel = new Span("0");
        orderCountLabel.getElement().getThemeList().add("badge small");

        Tab commandTab = new Tab(new HorizontalLayout(
            VaadinIcon.COG.create(),
            new Span("Kran-Kommandos"),
            commandCountLabel
        ));

        Tab orderTab = new Tab(new HorizontalLayout(
            VaadinIcon.TRUCK.create(),
            new Span("Transportaufträge"),
            orderCountLabel
        ));

        Tabs tabs = new Tabs(commandTab, orderTab);
        tabs.setWidthFull();

        // Content-Bereiche
        commandContent = createCommandContent();
        orderContent = createOrderContent();
        orderContent.setVisible(false);

        // Tab-Wechsel
        tabs.addSelectedChangeListener(event -> {
            commandContent.setVisible(event.getSelectedTab() == commandTab);
            orderContent.setVisible(event.getSelectedTab() == orderTab);
        });

        add(tabs, commandContent, orderContent);
        setFlexGrow(1, commandContent, orderContent);
    }

    private VerticalLayout createCommandContent() {
        VerticalLayout layout = new VerticalLayout();
        layout.setSizeFull();
        layout.setPadding(false);
        layout.setSpacing(true);

        // Toolbar
        HorizontalLayout toolbar = new HorizontalLayout();
        toolbar.setSpacing(true);
        toolbar.setWidthFull();
        toolbar.setJustifyContentMode(JustifyContentMode.BETWEEN);

        // Links: Filter-Buttons
        HorizontalLayout filterButtons = new HorizontalLayout();
        filterButtons.setSpacing(true);

        Button allBtn = new Button("Alle", e -> loadCommands());
        allBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        filterButtons.add(allBtn);

        // Rechts: Aktions-Buttons
        HorizontalLayout actionButtons = new HorizontalLayout();
        actionButtons.setSpacing(true);

        Button newBtn = new Button("Neu", VaadinIcon.PLUS.create());
        newBtn.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        newBtn.addClickListener(e -> openCommandEditDialog(null));
        actionButtons.add(newBtn);

        toolbar.add(filterButtons, actionButtons);
        layout.add(toolbar);

        // Grid
        commandGrid = new Grid<>();
        commandGrid.setSizeFull();
        commandGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);

        commandGrid.addColumn(CraneCommandDTO::getId)
            .setHeader("ID")
            .setWidth("80px")
            .setFlexGrow(0);

        commandGrid.addColumn(CraneCommandDTO::getCmdTypeDisplay)
            .setHeader("Typ")
            .setWidth("120px")
            .setFlexGrow(0);

        commandGrid.addColumn(CraneCommandDTO::getCraneModeDisplay)
            .setHeader("Modus")
            .setWidth("120px")
            .setFlexGrow(0);

        commandGrid.addColumn(CraneCommandDTO::getRoute)
            .setHeader("Route")
            .setWidth("180px")
            .setFlexGrow(0);

        commandGrid.addColumn(dto -> dto.getIngotNo() != null ? dto.getIngotNo() : "-")
            .setHeader("Barren")
            .setWidth("120px")
            .setFlexGrow(0);

        commandGrid.addColumn(dto -> dto.getRotate() != null ? dto.getRotate() + "°" : "-")
            .setHeader("Rotation")
            .setWidth("80px")
            .setFlexGrow(1);

        // Klick-Listener für Detail-Dialog
        commandGrid.addItemClickListener(event -> {
            CraneCommandDetailDialog dialog = new CraneCommandDetailDialog(event.getItem());
            dialog.setOnEdit(this::openCommandEditDialog);
            dialog.setOnDelete(this::deleteCommand);
            dialog.open();
        });

        layout.add(commandGrid);
        layout.setFlexGrow(1, commandGrid);

        return layout;
    }

    private VerticalLayout createOrderContent() {
        VerticalLayout layout = new VerticalLayout();
        layout.setSizeFull();
        layout.setPadding(false);
        layout.setSpacing(true);

        // Toolbar
        HorizontalLayout toolbar = new HorizontalLayout();
        toolbar.setSpacing(true);
        toolbar.setWidthFull();
        toolbar.setJustifyContentMode(JustifyContentMode.BETWEEN);

        // Links: Filter-Buttons
        HorizontalLayout filterButtons = new HorizontalLayout();
        filterButtons.setSpacing(true);

        Button allBtn = new Button("Alle", e -> loadOrders());
        allBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        filterButtons.add(allBtn);

        // Rechts: Aktions-Buttons
        HorizontalLayout actionButtons = new HorizontalLayout();
        actionButtons.setSpacing(true);

        Button newBtn = new Button("Neu", VaadinIcon.PLUS.create());
        newBtn.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        newBtn.addClickListener(e -> openOrderEditDialog(null));
        actionButtons.add(newBtn);

        toolbar.add(filterButtons, actionButtons);
        layout.add(toolbar);

        // Grid
        orderGrid = new Grid<>();
        orderGrid.setSizeFull();
        orderGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);

        orderGrid.addColumn(TransportOrderDTO::getId)
            .setHeader("ID")
            .setWidth("80px")
            .setFlexGrow(0);

        orderGrid.addColumn(dto -> dto.getTransportNo() != null ? dto.getTransportNo() : "-")
            .setHeader("Transport-Nr")
            .setWidth("120px")
            .setFlexGrow(0);

        orderGrid.addColumn(TransportOrderDTO::getRouteWithPositions)
            .setHeader("Route")
            .setWidth("200px")
            .setFlexGrow(0);

        orderGrid.addColumn(dto -> dto.getIngotNo() != null ? dto.getIngotNo() : "-")
            .setHeader("Barren")
            .setWidth("120px")
            .setFlexGrow(0);

        orderGrid.addColumn(dto -> dto.getNormText() != null ? dto.getNormText() : "-")
            .setHeader("Norm")
            .setWidth("150px")
            .setFlexGrow(0);

        orderGrid.addColumn(dto -> dto.getToPilePosition() != null ? dto.getToPilePosition().toString() : "-")
            .setHeader("Ziel-Pos")
            .setWidth("100px")
            .setFlexGrow(1);

        // Klick-Listener für Detail-Dialog
        orderGrid.addItemClickListener(event -> {
            TransportOrderDetailDialog dialog = new TransportOrderDetailDialog(event.getItem());
            dialog.setOnEdit(this::openOrderEditDialog);
            dialog.setOnDelete(this::deleteOrder);
            dialog.open();
        });

        layout.add(orderGrid);
        layout.setFlexGrow(1, orderGrid);

        return layout;
    }

    private void loadData() {
        loadCommands();
        loadOrders();
        updateCounts();
    }

    private void loadCommands() {
        try {
            List<CraneCommandDTO> commands = craneCommandService.findLatest(100);
            commandGrid.setItems(commands);
            log.debug("Loaded {} crane commands", commands.size());
        } catch (Exception e) {
            log.error("Error loading crane commands", e);
            commandGrid.setItems();
        }
    }

    private void loadOrders() {
        try {
            List<TransportOrderDTO> orders = transportOrderService.findLatest(100);
            orderGrid.setItems(orders);
            log.debug("Loaded {} transport orders", orders.size());
        } catch (Exception e) {
            log.error("Error loading transport orders", e);
            orderGrid.setItems();
        }
    }

    private void updateCounts() {
        try {
            int commandCount = craneCommandService.countAll();
            commandCountLabel.setText(String.valueOf(commandCount));

            int orderCount = transportOrderService.countAll();
            orderCountLabel.setText(String.valueOf(orderCount));
        } catch (Exception e) {
            log.error("Error updating counts", e);
        }
    }

    private void openCommandEditDialog(CraneCommandDTO command) {
        boolean isNew = (command == null);
        CraneCommandEditDialog dialog = new CraneCommandEditDialog(command, isNew);
        dialog.setOnSave(dto -> {
            try {
                craneCommandService.save(dto);
                loadCommands();
                updateCounts();
            } catch (Exception e) {
                log.error("Error saving crane command", e);
            }
        });
        dialog.open();
    }

    private void openOrderEditDialog(TransportOrderDTO order) {
        boolean isNew = (order == null);
        TransportOrderEditDialog dialog = new TransportOrderEditDialog(order, isNew, ingotService, stockyardService);
        dialog.setOnSave(dto -> {
            try {
                transportOrderService.save(dto);
                loadOrders();
                updateCounts();
            } catch (Exception e) {
                log.error("Error saving transport order", e);
            }
        });
        dialog.open();
    }

    private void deleteCommand(CraneCommandDTO command) {
        try {
            craneCommandService.delete(command.getId());
            loadCommands();
            updateCounts();
            log.info("Crane command deleted: {}", command.getId());
        } catch (Exception e) {
            log.error("Error deleting crane command", e);
        }
    }

    private void deleteOrder(TransportOrderDTO order) {
        try {
            transportOrderService.delete(order.getId());
            loadOrders();
            updateCounts();
            log.info("Transport order deleted: {}", order.getId());
        } catch (Exception e) {
            log.error("Error deleting transport order", e);
        }
    }
}
