package com.hydro.plsbl.ui.view;

import com.hydro.plsbl.service.AutoRetrievalService.StaplerAnforderung;
import com.hydro.plsbl.service.StaplerAnforderungBroadcaster;
import com.hydro.plsbl.ui.MainLayout;
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
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Stapler-Ansicht - Zeigt offene Stapler-Anforderungen fuer Auto-Auslagerung.
 *
 * Der Stapler-Fahrer sieht hier alle Barren, die vom externen Lager
 * zum Belade-Bereich gebracht werden muessen.
 */
@Route(value = "stapler", layout = MainLayout.class)
@PageTitle("Stapler | PLSBL")
public class StaplerView extends VerticalLayout {

    private static final Logger log = LoggerFactory.getLogger(StaplerView.class);

    private final StaplerAnforderungBroadcaster staplerBroadcaster;

    private Grid<StaplerAnforderung> grid;
    private Span countLabel;
    private StaplerAnforderungBroadcaster.Registration broadcasterRegistration;

    public StaplerView(StaplerAnforderungBroadcaster staplerBroadcaster) {
        this.staplerBroadcaster = staplerBroadcaster;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        createHeader();
        createGrid();
        createFooter();

        loadData();
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        UI ui = attachEvent.getUI();

        // Fuer Stapler-Anforderungen registrieren
        broadcasterRegistration = staplerBroadcaster.register(anforderungen -> {
            log.info("StaplerView received {} Anforderungen", anforderungen.size());
            ui.access(() -> {
                refreshGrid();
                Notification.show(anforderungen.size() + " neue Stapler-Anforderung(en)",
                    3000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_PRIMARY);
            });
        });
        log.info("StaplerView registered for Stapler notifications");

        // Initiale Daten laden
        refreshGrid();
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        if (broadcasterRegistration != null) {
            broadcasterRegistration.remove();
            broadcasterRegistration = null;
            log.info("StaplerView unregistered from Stapler notifications");
        }
        super.onDetach(detachEvent);
    }

    private void createHeader() {
        H3 title = new H3("Stapler-Anforderungen");
        title.getStyle().set("margin", "0");

        Span info = new Span("Offene Anforderungen fuer Auto-Auslagerung");
        info.getStyle().set("color", "gray");

        countLabel = new Span("0 offene Anforderungen");
        countLabel.getStyle()
            .set("background-color", "#FF9800")
            .set("color", "white")
            .set("padding", "4px 12px")
            .set("border-radius", "12px")
            .set("font-weight", "bold");

        // Spacer
        Span spacer = new Span();
        spacer.getStyle().set("flex-grow", "1");

        // Aktualisieren-Button
        Button refreshButton = new Button("Aktualisieren", VaadinIcon.REFRESH.create());
        refreshButton.addClickListener(e -> {
            refreshGrid();
            Notification.show("Daten aktualisiert", 2000, Notification.Position.BOTTOM_CENTER);
        });

        HorizontalLayout header = new HorizontalLayout(title, info, spacer, countLabel, refreshButton);
        header.setAlignItems(Alignment.CENTER);
        header.setWidthFull();

        add(header);
    }

    private void createGrid() {
        grid = new Grid<>();
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);

        grid.addColumn(StaplerAnforderung::getTransportNo)
            .setHeader("Transport-Nr")
            .setWidth("120px")
            .setFlexGrow(0);

        grid.addColumn(StaplerAnforderung::getBarrenNo)
            .setHeader("Barren-Nr")
            .setWidth("120px")
            .setFlexGrow(0);

        grid.addColumn(StaplerAnforderung::getVonPlatz)
            .setHeader("Von Platz")
            .setWidth("100px")
            .setFlexGrow(0);

        grid.addColumn(StaplerAnforderung::getNachPlatz)
            .setHeader("Nach Platz")
            .setWidth("120px")
            .setFlexGrow(0);

        grid.addColumn(StaplerAnforderung::getCalloffNo)
            .setHeader("Abruf-Nr")
            .setWidth("120px")
            .setFlexGrow(0);

        grid.addColumn(anf -> anf.getGewicht() != null ? anf.getGewicht() + " kg" : "-")
            .setHeader("Gewicht")
            .setWidth("100px")
            .setFlexGrow(0);

        grid.addColumn(anf -> anf.getLaenge() != null ? anf.getLaenge() + " mm" : "-")
            .setHeader("Laenge")
            .setWidth("100px")
            .setFlexGrow(1);

        // Aktion-Spalte mit Button
        grid.addComponentColumn(this::createActionButton)
            .setHeader("Aktion")
            .setWidth("180px")
            .setFlexGrow(0);

        add(grid);
        setFlexGrow(1, grid);
    }

    private Button createActionButton(StaplerAnforderung anforderung) {
        Button button = new Button("Als erledigt markieren", VaadinIcon.CHECK.create());
        button.addThemeVariants(ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_SMALL);
        button.addClickListener(e -> markAsCompleted(anforderung));
        return button;
    }

    private void createFooter() {
        Button clearAllButton = new Button("Alle loeschen", VaadinIcon.TRASH.create());
        clearAllButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        clearAllButton.addClickListener(e -> {
            staplerBroadcaster.clearAll();
            refreshGrid();
            Notification.show("Alle Anforderungen geloescht", 3000, Notification.Position.BOTTOM_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_CONTRAST);
        });

        HorizontalLayout footer = new HorizontalLayout(clearAllButton);
        footer.setWidthFull();
        footer.setJustifyContentMode(JustifyContentMode.END);
        footer.getStyle()
            .set("padding-top", "10px")
            .set("border-top", "1px solid #ddd");

        add(footer);
    }

    private void loadData() {
        refreshGrid();
    }

    private void refreshGrid() {
        List<StaplerAnforderung> anforderungen = staplerBroadcaster.getOffeneAnforderungen();
        grid.setItems(anforderungen);

        int count = anforderungen.size();
        countLabel.setText(count + " offene Anforderung" + (count != 1 ? "en" : ""));

        // Farbe je nach Anzahl
        if (count == 0) {
            countLabel.getStyle()
                .set("background-color", "#4CAF50")
                .set("color", "white");
        } else if (count <= 3) {
            countLabel.getStyle()
                .set("background-color", "#FF9800")
                .set("color", "white");
        } else {
            countLabel.getStyle()
                .set("background-color", "#F44336")
                .set("color", "white");
        }

        log.debug("Grid refreshed: {} Anforderungen", count);
    }

    private void markAsCompleted(StaplerAnforderung anforderung) {
        if (anforderung == null || anforderung.getTransportOrderId() == null) {
            Notification.show("Fehler: Ungueltige Anforderung", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        staplerBroadcaster.markCompleted(anforderung.getTransportOrderId());
        refreshGrid();

        Notification.show("Anforderung " + anforderung.getTransportNo() + " als erledigt markiert",
            3000, Notification.Position.BOTTOM_CENTER)
            .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

        log.info("Stapler-Anforderung {} als erledigt markiert", anforderung.getTransportNo());
    }
}
