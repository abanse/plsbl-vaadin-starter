package com.hydro.plsbl.ui.view;

import com.hydro.plsbl.dto.IngotDTO;
import com.hydro.plsbl.service.IngotService;
import com.hydro.plsbl.service.StockyardService;
import com.hydro.plsbl.ui.MainLayout;
import com.hydro.plsbl.ui.dialog.IngotDetailDialog;
import com.hydro.plsbl.ui.dialog.IngotEditDialog;
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
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Barren-Ansicht - Übersicht aller Barren mit CRUD
 */
@Route(value = "barren", layout = MainLayout.class)
@PageTitle("Barren | PLSBL")
public class BarrenView extends VerticalLayout {

    private static final Logger log = LoggerFactory.getLogger(BarrenView.class);

    private final IngotService ingotService;
    private final StockyardService stockyardService;

    private Grid<IngotDTO> grid;
    private TextField searchField;
    private Span countLabel;

    private List<IngotDTO> allIngots;

    public BarrenView(IngotService ingotService, StockyardService stockyardService) {
        this.ingotService = ingotService;
        this.stockyardService = stockyardService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        createHeader();
        createToolbar();
        createGrid();
        loadData();
    }

    private void createHeader() {
        H3 title = new H3("Barren-Übersicht");
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

    private void createToolbar() {
        HorizontalLayout toolbar = new HorizontalLayout();
        toolbar.setSpacing(true);
        toolbar.setWidthFull();
        toolbar.setJustifyContentMode(JustifyContentMode.BETWEEN);

        // Links: Suche
        searchField = new TextField();
        searchField.setPlaceholder("Suche (Barren, Produkt, Platz)...");
        searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchField.setClearButtonVisible(true);
        searchField.setWidth("300px");
        searchField.setValueChangeMode(ValueChangeMode.LAZY);
        searchField.addValueChangeListener(e -> filterData());

        // Filter-Buttons
        HorizontalLayout filterButtons = new HorizontalLayout();
        filterButtons.setSpacing(true);

        Button allBtn = new Button("Alle", e -> {
            searchField.clear();
            filterData();
        });
        allBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button onStockBtn = new Button("Auf Lager", e -> filterOnStock());
        Button releasedBtn = new Button("Freigegeben", e -> filterReleased());
        Button scrapBtn = new Button("Schrott", e -> filterScrap());

        filterButtons.add(allBtn, onStockBtn, releasedBtn, scrapBtn);

        HorizontalLayout leftPart = new HorizontalLayout(searchField, filterButtons);
        leftPart.setAlignItems(Alignment.CENTER);
        leftPart.setSpacing(true);

        // Rechts: Neu-Button
        Button newBtn = new Button("Neu", VaadinIcon.PLUS.create());
        newBtn.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        newBtn.addClickListener(e -> openEditDialog(null));

        toolbar.add(leftPart, newBtn);
        add(toolbar);
    }

    private void createGrid() {
        grid = new Grid<>();
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);

        grid.addColumn(IngotDTO::getIngotNo)
            .setHeader("Barren-Nr")
            .setWidth("150px")
            .setFlexGrow(0)
            .setSortable(true);

        grid.addColumn(dto -> dto.getProductNo() != null ? dto.getProductNo() : "-")
            .setHeader("Produkt")
            .setAutoWidth(true)
            .setFlexGrow(1)
            .setSortable(true);

        grid.addColumn(dto -> dto.getStockyardNo() != null ? dto.getStockyardNo() : "-")
            .setHeader("Lagerplatz")
            .setWidth("100px")
            .setFlexGrow(0)
            .setSortable(true);

        grid.addColumn(dto -> dto.getPilePosition() != null ? dto.getPilePosition().toString() : "-")
            .setHeader("Pos")
            .setWidth("50px")
            .setFlexGrow(0);

        grid.addColumn(dto -> dto.getWeight() != null ? dto.getWeight() + " kg" : "-")
            .setHeader("Gewicht")
            .setWidth("100px")
            .setFlexGrow(0)
            .setSortable(true);

        // Schrott-Spalte mit Badge
        grid.addComponentColumn(ingot -> {
            if (Boolean.TRUE.equals(ingot.getScrap())) {
                Span badge = new Span("Schrott");
                badge.getStyle()
                    .set("color", "white")
                    .set("background-color", "#F44336")
                    .set("padding", "2px 6px")
                    .set("border-radius", "4px")
                    .set("font-size", "11px");
                return badge;
            }
            return new Span("");
        }).setHeader("Schrott").setWidth("80px").setFlexGrow(0);

        // Korrigiert-Spalte mit Badge
        grid.addComponentColumn(ingot -> {
            if (Boolean.TRUE.equals(ingot.getRevised())) {
                Span badge = new Span("Korr.");
                badge.getStyle()
                    .set("color", "white")
                    .set("background-color", "#FF9800")
                    .set("padding", "2px 6px")
                    .set("border-radius", "4px")
                    .set("font-size", "11px");
                return badge;
            }
            return new Span("");
        }).setHeader("Korr.").setWidth("70px").setFlexGrow(0);

        // Freigegeben-Spalte
        grid.addComponentColumn(ingot -> {
            if (ingot.isReleased()) {
                Span badge = new Span("Frei");
                badge.getStyle()
                    .set("color", "white")
                    .set("background-color", "#4CAF50")
                    .set("padding", "2px 6px")
                    .set("border-radius", "4px")
                    .set("font-size", "11px");
                return badge;
            }
            return new Span("");
        }).setHeader("Frei").setWidth("60px").setFlexGrow(0);

        // Bearbeiten-Button
        grid.addComponentColumn(ingot -> {
            Button editBtn = new Button(VaadinIcon.EDIT.create());
            editBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            editBtn.addClickListener(e -> openEditDialog(ingot));
            return editBtn;
        }).setHeader("").setWidth("50px").setFlexGrow(0);

        // Doppelklick für Details
        grid.addItemDoubleClickListener(event -> {
            IngotDetailDialog dialog = new IngotDetailDialog(event.getItem());
            dialog.setOnEdit(this::openEditDialog);
            dialog.setOnDelete(this::deleteIngot);
            dialog.open();
        });

        add(grid);
        setFlexGrow(1, grid);
    }

    private String formatDimensions(IngotDTO ingot) {
        if (ingot.getLength() == null) return "-";
        return String.format("%d×%d×%d",
            ingot.getLength(),
            ingot.getWidth() != null ? ingot.getWidth() : 0,
            ingot.getThickness() != null ? ingot.getThickness() : 0);
    }

    private String formatFlags(IngotDTO ingot) {
        StringBuilder sb = new StringBuilder();
        if (Boolean.TRUE.equals(ingot.getScrap())) sb.append("S");
        if (Boolean.TRUE.equals(ingot.getRevised())) sb.append("K");
        if (Boolean.TRUE.equals(ingot.getHeadSawn())) sb.append("H");
        if (Boolean.TRUE.equals(ingot.getFootSawn())) sb.append("F");
        if (Boolean.TRUE.equals(ingot.getRotated())) sb.append("R");
        if (ingot.isReleased()) sb.append("*");
        return sb.length() > 0 ? sb.toString() : "-";
    }

    private void loadData() {
        try {
            allIngots = ingotService.findAll();
            grid.setItems(allIngots);
            updateCount();
            log.debug("Loaded {} ingots", allIngots.size());
            Notification.show("Daten geladen: " + allIngots.size() + " Barren",
                2000, Notification.Position.BOTTOM_CENTER);
        } catch (Exception e) {
            log.error("Error loading ingots", e);
            grid.setItems();
            Notification.show("Fehler beim Laden: " + e.getMessage(),
                5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void filterData() {
        if (allIngots == null) return;

        String searchTerm = searchField.getValue();
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            grid.setItems(allIngots);
            updateCount();
            return;
        }

        String term = searchTerm.toLowerCase().trim();
        List<IngotDTO> filtered = allIngots.stream()
            .filter(ingot -> {
                // Barren-Nr prüfen
                if (ingot.getIngotNo() != null && ingot.getIngotNo().toLowerCase().contains(term)) {
                    return true;
                }
                // Produkt-Nr prüfen
                if (ingot.getProductNo() != null && ingot.getProductNo().toLowerCase().contains(term)) {
                    return true;
                }
                // Lagerplatz prüfen
                if (ingot.getStockyardNo() != null && ingot.getStockyardNo().toLowerCase().contains(term)) {
                    return true;
                }
                return false;
            })
            .collect(Collectors.toList());

        grid.setItems(filtered);
        countLabel.setText(filtered.size() + " / " + allIngots.size());
    }

    private void filterOnStock() {
        if (allIngots == null) return;
        List<IngotDTO> filtered = allIngots.stream()
            .filter(ingot -> ingot.getStockyardId() != null)
            .collect(Collectors.toList());
        grid.setItems(filtered);
        updateCount();
    }

    private void filterReleased() {
        if (allIngots == null) return;
        List<IngotDTO> filtered = allIngots.stream()
            .filter(IngotDTO::isReleased)
            .collect(Collectors.toList());
        grid.setItems(filtered);
        updateCount();
    }

    private void filterScrap() {
        if (allIngots == null) return;
        List<IngotDTO> filtered = allIngots.stream()
            .filter(ingot -> Boolean.TRUE.equals(ingot.getScrap()))
            .collect(Collectors.toList());
        grid.setItems(filtered);
        updateCount();
    }

    private void updateCount() {
        int total = ingotService.countAll();
        countLabel.setText(String.valueOf(total));
    }

    private void openEditDialog(IngotDTO ingot) {
        boolean isNew = (ingot == null);
        IngotEditDialog dialog = new IngotEditDialog(ingot, isNew, stockyardService);
        dialog.setOnSave(dto -> {
            try {
                ingotService.save(dto);
                loadData();
            } catch (Exception e) {
                log.error("Error saving ingot", e);
            }
        });
        dialog.open();
    }

    private void deleteIngot(IngotDTO ingot) {
        try {
            ingotService.delete(ingot.getId());
            loadData();
            log.info("Ingot deleted: {}", ingot.getId());
        } catch (Exception e) {
            log.error("Error deleting ingot", e);
        }
    }
}
