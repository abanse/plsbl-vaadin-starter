package com.hydro.plsbl.ui.component;

import com.hydro.plsbl.dto.CraneStatusDTO;
import com.hydro.plsbl.service.CraneStatusService;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Komponente zur Anzeige des Kran-Status im Header
 */
public class CraneStatusBar extends HorizontalLayout {

    private final CraneStatusService craneStatusService;

    private Span modeLabel;
    private Span stateLabel;
    private Span positionLabel;
    private Span routeLabel;
    private Div statusIndicator;
    private Span alarmIcon;
    private boolean hasAlarm = false;

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> refreshTask;
    private UI ui;

    public CraneStatusBar(CraneStatusService craneStatusService) {
        this.craneStatusService = craneStatusService;

        setSpacing(true);
        setAlignItems(Alignment.CENTER);
        getStyle()
            .set("background-color", "rgba(255,255,255,0.1)")
            .set("padding", "4px 12px")
            .set("border-radius", "4px")
            .set("font-size", "13px");

        createComponents();
        loadStatus();
    }

    private void createComponents() {
        // Kran-Icon
        Icon craneIcon = VaadinIcon.COG.create();
        craneIcon.setSize("16px");
        craneIcon.getStyle().set("color", "white");

        Span title = new Span("Kran:");
        title.getStyle()
            .set("color", "white")
            .set("font-weight", "bold");

        // Status-Indikator (farbiger Punkt)
        statusIndicator = new Div();
        statusIndicator.getStyle()
            .set("width", "10px")
            .set("height", "10px")
            .set("border-radius", "50%")
            .set("background-color", "gray");

        // Modus (Automatik/Manuell)
        modeLabel = new Span("-");
        modeLabel.getStyle().set("color", "white");

        // Zustand
        stateLabel = new Span("-");
        stateLabel.getStyle().set("color", "lightgray");

        // Position
        positionLabel = new Span("-");
        positionLabel.getStyle().set("color", "lightgray");

        // Route (Von -> Nach)
        routeLabel = new Span("");
        routeLabel.getStyle().set("color", "#90CAF9");

        // Separator
        Span sep1 = createSeparator();
        Span sep2 = createSeparator();

        // Alarm-Icon (nur bei Störung sichtbar)
        alarmIcon = new Span("⚠ ALARM");
        alarmIcon.getStyle()
            .set("color", "#FF5252")
            .set("font-weight", "bold")
            .set("margin-left", "8px")
            .set("padding", "2px 8px")
            .set("background-color", "rgba(255, 82, 82, 0.2)")
            .set("border-radius", "4px")
            .set("animation", "alarm-blink 0.5s infinite");
        alarmIcon.setVisible(false);

        add(craneIcon, title, statusIndicator, modeLabel, sep1, stateLabel, sep2, positionLabel, routeLabel, alarmIcon);

        // CSS Animation für Blinken injizieren
        getElement().executeJs(
            "if (!document.getElementById('alarm-header-style')) {" +
            "  var style = document.createElement('style');" +
            "  style.id = 'alarm-header-style';" +
            "  style.textContent = '@keyframes alarm-blink { 0%, 100% { opacity: 1; } 50% { opacity: 0.3; } }';" +
            "  document.head.appendChild(style);" +
            "}"
        );
    }

    private Span createSeparator() {
        Span sep = new Span("|");
        sep.getStyle()
            .set("color", "rgba(255,255,255,0.3)")
            .set("margin", "0 4px");
        return sep;
    }

    private void loadStatus() {
        try {
            Optional<CraneStatusDTO> statusOpt = craneStatusService.getCurrentStatus();

            if (statusOpt.isPresent()) {
                updateDisplay(statusOpt.get());
            } else {
                showNoData();
            }
        } catch (Exception e) {
            showError();
        }
    }

    private void updateDisplay(CraneStatusDTO status) {
        // Modus
        modeLabel.setText(status.getModeDisplay());

        // Status-Indikator Farbe
        String indicatorColor = getIndicatorColor(status);
        statusIndicator.getStyle().set("background-color", indicatorColor);

        // Zustand
        stateLabel.setText(status.getJobStateDisplay());

        // Position
        if (status.getXPosition() != null) {
            positionLabel.setText(String.format("X:%d Y:%d",
                status.getXPosition() / 1000,  // mm -> m
                status.getYPosition() != null ? status.getYPosition() / 1000 : 0));
        }

        // Route
        if (status.getFromStockyardNo() != null || status.getToStockyardNo() != null) {
            String from = status.getFromStockyardNo() != null ? status.getFromStockyardNo() : "?";
            String to = status.getToStockyardNo() != null ? status.getToStockyardNo() : "?";
            routeLabel.setText(from + " -> " + to);
            routeLabel.setVisible(true);
        } else {
            routeLabel.setVisible(false);
        }

        // Bei Incident rot markieren und Alarm anzeigen
        if (status.hasIncident()) {
            stateLabel.setText(status.getIncident());
            stateLabel.getStyle().set("color", "#FF5252");

            // Blinkendes Alarm-Icon anzeigen
            alarmIcon.setVisible(true);

            // Status-Indikator blinken lassen
            if (!hasAlarm) {
                statusIndicator.getStyle().set("animation", "alarm-blink 0.5s infinite");
                hasAlarm = true;
            }
        } else {
            stateLabel.getStyle().set("color", "lightgray");

            // Alarm ausblenden
            alarmIcon.setVisible(false);

            // Status-Indikator Animation entfernen
            if (hasAlarm) {
                statusIndicator.getStyle().remove("animation");
                hasAlarm = false;
            }
        }
    }

    private String getIndicatorColor(CraneStatusDTO status) {
        if (status.hasIncident()) {
            return "#FF5252";  // Rot bei Fehler
        }

        String jobState = status.getJobState();
        if (jobState == null) return "gray";

        return switch (jobState) {
            case "IDLE" -> "#4CAF50";      // Grün - Bereit
            case "WORKING" -> "#2196F3";   // Blau - Arbeitet
            case "WAITING" -> "#FFC107";   // Gelb - Wartet
            case "ERROR" -> "#FF5252";     // Rot - Fehler
            default -> "gray";
        };
    }

    private void showNoData() {
        modeLabel.setText("Keine Daten");
        stateLabel.setText("-");
        positionLabel.setText("-");
        routeLabel.setVisible(false);
        statusIndicator.getStyle().set("background-color", "gray");
        alarmIcon.setVisible(false);
        statusIndicator.getStyle().remove("animation");
        hasAlarm = false;
    }

    private void showError() {
        modeLabel.setText("Fehler");
        modeLabel.getStyle().set("color", "#FF5252");
        stateLabel.setText("-");
        positionLabel.setText("-");
        routeLabel.setVisible(false);
        statusIndicator.getStyle().set("background-color", "#FF5252");
        alarmIcon.setVisible(true);  // Bei Verbindungsfehler auch Alarm zeigen
        statusIndicator.getStyle().set("animation", "alarm-blink 0.5s infinite");
        hasAlarm = true;
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        ui = attachEvent.getUI();

        // Auto-Refresh alle 5 Sekunden
        scheduler = Executors.newSingleThreadScheduledExecutor();
        refreshTask = scheduler.scheduleAtFixedRate(() -> {
            if (ui != null) {
                ui.access(this::loadStatus);
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        super.onDetach(detachEvent);

        // Scheduler stoppen
        if (refreshTask != null) {
            refreshTask.cancel(true);
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    /**
     * Manuelles Refresh
     */
    public void refresh() {
        loadStatus();
    }
}
