package com.hydro.plsbl.ui.view;

import com.hydro.plsbl.dto.CraneCommandDTO;
import com.hydro.plsbl.dto.TransportOrderDTO;
import com.hydro.plsbl.entity.enums.OrderStatus;
import com.hydro.plsbl.service.CraneCommandService;
import com.hydro.plsbl.service.IngotService;
import com.hydro.plsbl.service.StockyardService;
import com.hydro.plsbl.service.TransportOrderProcessor;
import com.hydro.plsbl.service.TransportOrderService;
import com.hydro.plsbl.ui.MainLayout;
import com.hydro.plsbl.ui.dialog.CraneCommandDetailDialog;
import com.hydro.plsbl.ui.dialog.CraneCommandEditDialog;
import com.hydro.plsbl.ui.dialog.TransportOrderDetailDialog;
import com.hydro.plsbl.ui.dialog.TransportOrderEditDialog;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

/**
 * Aufträge-Ansicht - Kran-Kommandos und Transportaufträge
 */
@Route(value = "auftraege", layout = MainLayout.class)
@PageTitle("Aufträge | PLSBL")
public class AuftraegeView extends VerticalLayout {

    private static final Logger log = LoggerFactory.getLogger(AuftraegeView.class);

    private final CraneCommandService craneCommandService;
    private final TransportOrderService transportOrderService;
    private final TransportOrderProcessor orderProcessor;
    private final IngotService ingotService;
    private final StockyardService stockyardService;

    private Grid<CraneCommandDTO> commandGrid;
    private Grid<TransportOrderDTO> orderGrid;
    private VerticalLayout commandContent;
    private VerticalLayout orderContent;

    private Span commandCountLabel;
    private Span orderCountLabel;

    // Auto-Processing UI
    private Button autoProcessButton;
    private Button abortButton;
    private Span processingStatusLabel;
    private Span currentOrderLabel;

    // Listeners
    private Consumer<TransportOrderDTO> orderStartedListener;
    private Consumer<TransportOrderDTO> orderCompletedListener;
    private Consumer<String> statusChangeListener;

    public AuftraegeView(CraneCommandService craneCommandService, TransportOrderService transportOrderService,
                         TransportOrderProcessor orderProcessor, IngotService ingotService, StockyardService stockyardService) {
        this.craneCommandService = craneCommandService;
        this.transportOrderService = transportOrderService;
        this.orderProcessor = orderProcessor;
        this.ingotService = ingotService;
        this.stockyardService = stockyardService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        createHeader();
        createAutoProcessingPanel();
        createTabs();
        loadData();

        setupListeners();
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        updateAutoProcessingUI();
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        super.onDetach(detachEvent);
        // Listeners werden bei Bedarf entfernt
    }

    private void setupListeners() {
        UI ui = UI.getCurrent();

        orderStartedListener = order -> {
            ui.access(() -> {
                loadOrders();
                updateAutoProcessingUI();
            });
        };

        orderCompletedListener = order -> {
            ui.access(() -> {
                loadOrders();
                updateAutoProcessingUI();
                Notification.show("Auftrag " + order.getTransportNo() + " abgeschlossen",
                    3000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            });
        };

        statusChangeListener = message -> {
            ui.access(() -> {
                processingStatusLabel.setText(message);
                updateAutoProcessingUI();
            });
        };

        orderProcessor.addOrderStartedListener(orderStartedListener);
        orderProcessor.addOrderCompletedListener(orderCompletedListener);
        orderProcessor.addStatusChangeListener(statusChangeListener);
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

    private void createAutoProcessingPanel() {
        HorizontalLayout panel = new HorizontalLayout();
        panel.setWidthFull();
        panel.setAlignItems(Alignment.CENTER);
        panel.setPadding(true);
        panel.getStyle()
            .set("background-color", "#E3F2FD")
            .set("border-radius", "8px")
            .set("margin-bottom", "10px");

        // Auto-Processing Button
        autoProcessButton = new Button("Auto-Verarbeitung starten", VaadinIcon.PLAY.create());
        autoProcessButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        autoProcessButton.addClickListener(e -> toggleAutoProcessing());

        // Abbruch Button
        abortButton = new Button("Abbrechen", VaadinIcon.STOP.create());
        abortButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        abortButton.setEnabled(false);
        abortButton.addClickListener(e -> {
            orderProcessor.abortCurrentOrder();
            updateAutoProcessingUI();
        });

        // Status Label
        processingStatusLabel = new Span("Bereit");
        processingStatusLabel.getStyle()
            .set("font-weight", "bold")
            .set("margin-left", "20px");

        // Aktueller Auftrag
        currentOrderLabel = new Span("");
        currentOrderLabel.getStyle()
            .set("color", "#1565C0")
            .set("margin-left", "10px");

        // Wartende Aufträge
        Span pendingLabel = new Span();
        pendingLabel.getStyle().set("margin-left", "auto");

        panel.add(autoProcessButton, abortButton, processingStatusLabel, currentOrderLabel, pendingLabel);

        add(panel);
    }

    private void toggleAutoProcessing() {
        if (orderProcessor.isAutoProcessingEnabled()) {
            orderProcessor.stopAutoProcessing();
        } else {
            orderProcessor.startAutoProcessing();
        }
        updateAutoProcessingUI();
    }

    private void updateAutoProcessingUI() {
        boolean isEnabled = orderProcessor.isAutoProcessingEnabled();
        boolean isProcessing = orderProcessor.isProcessing();

        if (isEnabled) {
            autoProcessButton.setText("Auto-Verarbeitung stoppen");
            autoProcessButton.setIcon(VaadinIcon.PAUSE.create());
            autoProcessButton.removeThemeVariants(ButtonVariant.LUMO_SUCCESS);
            autoProcessButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        } else {
            autoProcessButton.setText("Auto-Verarbeitung starten");
            autoProcessButton.setIcon(VaadinIcon.PLAY.create());
            autoProcessButton.removeThemeVariants(ButtonVariant.LUMO_ERROR);
            autoProcessButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        }

        abortButton.setEnabled(isProcessing);

        // Aktueller Auftrag anzeigen
        orderProcessor.getCurrentOrder().ifPresentOrElse(
            order -> currentOrderLabel.setText("Aktuell: " + order.getTransportNo() + " (" + order.getRoute() + ")"),
            () -> currentOrderLabel.setText("")
        );

        // Wartende Aufträge
        int pending = orderProcessor.getPendingOrderCount();
        if (pending > 0) {
            processingStatusLabel.setText(isProcessing ? "Verarbeitet..." : pending + " Aufträge wartend");
        } else if (!isProcessing) {
            processingStatusLabel.setText("Keine wartenden Aufträge");
        }
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
            .setWidth("60px")
            .setFlexGrow(0);

        // Status-Spalte mit farbiger Badge
        orderGrid.addComponentColumn(dto -> {
            Span badge = new Span(dto.getStatusDisplay());
            badge.getStyle()
                .set("padding", "2px 8px")
                .set("border-radius", "12px")
                .set("font-size", "11px")
                .set("font-weight", "bold");

            OrderStatus status = dto.getStatus();
            if (status == null) status = OrderStatus.PENDING;

            switch (status) {
                case PENDING -> badge.getStyle()
                    .set("background-color", "#FFF3E0")
                    .set("color", "#E65100");
                case IN_PROGRESS -> badge.getStyle()
                    .set("background-color", "#E3F2FD")
                    .set("color", "#1565C0");
                case PICKED_UP -> badge.getStyle()
                    .set("background-color", "#E8F5E9")
                    .set("color", "#2E7D32");
                case COMPLETED -> badge.getStyle()
                    .set("background-color", "#C8E6C9")
                    .set("color", "#1B5E20");
                case FAILED -> badge.getStyle()
                    .set("background-color", "#FFEBEE")
                    .set("color", "#C62828");
                case CANCELLED -> badge.getStyle()
                    .set("background-color", "#ECEFF1")
                    .set("color", "#546E7A");
                case PAUSED -> badge.getStyle()
                    .set("background-color", "#FFF8E1")
                    .set("color", "#FF8F00");
            }
            return badge;
        }).setHeader("Status")
          .setWidth("100px")
          .setFlexGrow(0);

        orderGrid.addColumn(dto -> dto.getTransportNo() != null ? dto.getTransportNo() : "-")
            .setHeader("Transport-Nr")
            .setWidth("100px")
            .setFlexGrow(0);

        orderGrid.addColumn(TransportOrderDTO::getRoute)
            .setHeader("Route")
            .setWidth("140px")
            .setFlexGrow(0);

        orderGrid.addColumn(dto -> dto.getIngotNo() != null ? dto.getIngotNo() : "-")
            .setHeader("Barren")
            .setWidth("100px")
            .setFlexGrow(0);

        orderGrid.addColumn(dto -> {
            Integer prio = dto.getPriority();
            return prio != null && prio > 0 ? prio.toString() : "-";
        }).setHeader("Prio")
          .setWidth("60px")
          .setFlexGrow(0);

        // Aktion-Spalte mit Start-Button
        orderGrid.addComponentColumn(dto -> {
            if (dto.getStatus() == OrderStatus.PENDING) {
                Button startBtn = new Button(VaadinIcon.PLAY.create());
                startBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_SUCCESS);
                startBtn.getElement().setAttribute("title", "Auftrag jetzt starten");
                startBtn.addClickListener(e -> {
                    orderProcessor.processOrder(dto);
                    loadOrders();
                    updateAutoProcessingUI();
                });
                return startBtn;
            }
            return new Span("");
        }).setHeader("")
          .setWidth("50px")
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
