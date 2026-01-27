package com.hydro.plsbl.ui.view;

import com.hydro.plsbl.service.MessageService;
import com.hydro.plsbl.service.MessageService.*;
import com.hydro.plsbl.ui.MainLayout;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

/**
 * View für System-Meldungen (Alarme, Warnungen, Info).
 *
 * Zeigt alle Meldungen in einem Grid mit Filter- und Quittier-Funktionen.
 */
@Route(value = "meldungen", layout = MainLayout.class)
@PageTitle("Meldungen | PLSBL")
public class MeldungenView extends VerticalLayout {

    private static final Logger log = LoggerFactory.getLogger(MeldungenView.class);

    private final MessageService messageService;

    private Grid<SystemMessage> messageGrid;
    private ComboBox<String> typeFilter;
    private ComboBox<String> statusFilter;
    private Span countLabel;
    private Consumer<MessageEvent> messageListener;
    private UI ui;

    public MeldungenView(MessageService messageService) {
        this.messageService = messageService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        add(createToolbar());
        add(createGrid());

        loadMessages();
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        this.ui = attachEvent.getUI();

        // Listener für Meldungs-Updates
        messageListener = this::onMessageEvent;
        messageService.addListener(messageListener);

        loadMessages();
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        super.onDetach(detachEvent);
        if (messageListener != null) {
            messageService.removeListener(messageListener);
            messageListener = null;
        }
    }

    private HorizontalLayout createToolbar() {
        HorizontalLayout toolbar = new HorizontalLayout();
        toolbar.setWidthFull();
        toolbar.setAlignItems(FlexComponent.Alignment.CENTER);
        toolbar.setSpacing(true);

        // Typ-Filter
        typeFilter = new ComboBox<>("Typ");
        typeFilter.setItems("Alle", "Alarm", "Warnung", "Info");
        typeFilter.setValue("Alle");
        typeFilter.setWidth("150px");
        typeFilter.addValueChangeListener(e -> loadMessages());

        // Status-Filter
        statusFilter = new ComboBox<>("Status");
        statusFilter.setItems("Alle", "Aktiv", "Quittiert", "Behoben");
        statusFilter.setValue("Alle");
        statusFilter.setWidth("150px");
        statusFilter.addValueChangeListener(e -> loadMessages());

        // Spacer
        Span spacer = new Span();

        // Zähler
        countLabel = new Span();
        countLabel.getStyle()
            .set("font-weight", "bold")
            .set("margin-right", "20px");

        // Buttons
        Button acknowledgeAllBtn = new Button("Alle quittieren", VaadinIcon.CHECK.create());
        acknowledgeAllBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        acknowledgeAllBtn.addClickListener(e -> acknowledgeAll());

        Button refreshBtn = new Button("Aktualisieren", VaadinIcon.REFRESH.create());
        refreshBtn.addClickListener(e -> loadMessages());

        // Test-Button (nur für Entwicklung)
        Button testBtn = new Button("Test-Meldung", VaadinIcon.BELL.create());
        testBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        testBtn.addClickListener(e -> createTestMessage());

        toolbar.add(typeFilter, statusFilter, spacer, countLabel, acknowledgeAllBtn, refreshBtn, testBtn);
        toolbar.expand(spacer);

        return toolbar;
    }

    private Grid<SystemMessage> createGrid() {
        messageGrid = new Grid<>();
        messageGrid.setSizeFull();
        messageGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);

        // Status-Icon Spalte
        messageGrid.addColumn(new ComponentRenderer<>(this::createStatusIcon))
            .setHeader("")
            .setWidth("50px")
            .setFlexGrow(0)
            .setFrozen(true);

        // Zeitstempel
        messageGrid.addColumn(SystemMessage::getCreatedAtFormatted)
            .setHeader("Zeit")
            .setWidth("150px")
            .setFlexGrow(0)
            .setSortable(true);

        // Typ
        messageGrid.addColumn(msg -> msg.getType().getDisplayName())
            .setHeader("Typ")
            .setWidth("100px")
            .setFlexGrow(0)
            .setSortable(true);

        // Kategorie
        messageGrid.addColumn(msg -> msg.getCategory().getDisplayName())
            .setHeader("Kategorie")
            .setWidth("120px")
            .setFlexGrow(0)
            .setSortable(true);

        // Code
        messageGrid.addColumn(SystemMessage::getCode)
            .setHeader("Code")
            .setWidth("120px")
            .setFlexGrow(0);

        // Text
        messageGrid.addColumn(SystemMessage::getText)
            .setHeader("Meldungstext")
            .setFlexGrow(1);

        // Quelle
        messageGrid.addColumn(SystemMessage::getSource)
            .setHeader("Quelle")
            .setWidth("100px")
            .setFlexGrow(0);

        // Status
        messageGrid.addColumn(SystemMessage::getStatus)
            .setHeader("Status")
            .setWidth("100px")
            .setFlexGrow(0);

        // Quittiert um
        messageGrid.addColumn(SystemMessage::getAcknowledgedAtFormatted)
            .setHeader("Quittiert")
            .setWidth("150px")
            .setFlexGrow(0);

        // Anzahl
        messageGrid.addColumn(SystemMessage::getCount)
            .setHeader("Anz.")
            .setWidth("60px")
            .setFlexGrow(0);

        // Quittieren-Button
        messageGrid.addColumn(new ComponentRenderer<>(this::createAcknowledgeButton))
            .setHeader("")
            .setWidth("120px")
            .setFlexGrow(0);

        // Row-Styling basierend auf Typ und Status
        messageGrid.setClassNameGenerator(msg -> {
            if (!msg.isAcknowledged()) {
                return switch (msg.getType()) {
                    case ALARM -> "alarm-row";
                    case WARNING -> "warning-row";
                    default -> "";
                };
            }
            return "";
        });

        return messageGrid;
    }

    private Span createStatusIcon(SystemMessage msg) {
        Span icon = new Span();
        icon.getStyle()
            .set("font-size", "16px")
            .set("display", "flex")
            .set("justify-content", "center")
            .set("align-items", "center");

        if (!msg.isAcknowledged()) {
            // Unquittiert
            switch (msg.getType()) {
                case ALARM -> {
                    icon.setText("\u26A0");  // Warning triangle
                    icon.getStyle().set("color", "#d32f2f");
                }
                case WARNING -> {
                    icon.setText("\u26A0");
                    icon.getStyle().set("color", "#ff9800");
                }
                default -> {
                    icon.setText("\u2139");  // Info
                    icon.getStyle().set("color", "#2196f3");
                }
            }
        } else if (!msg.isConditionCleared()) {
            // Quittiert aber Bedingung noch aktiv
            icon.setText("\u2713");  // Checkmark
            icon.getStyle().set("color", "#ff9800");
        } else {
            // Behoben
            icon.setText("\u2713");
            icon.getStyle().set("color", "#4caf50");
        }

        return icon;
    }

    private Button createAcknowledgeButton(SystemMessage msg) {
        Button btn = new Button("Quittieren");
        btn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
        btn.setEnabled(!msg.isAcknowledged());

        btn.addClickListener(e -> {
            if (messageService.acknowledge(msg.getId())) {
                Notification.show("Meldung quittiert", 1500, Notification.Position.BOTTOM_CENTER);
                loadMessages();
            }
        });

        return btn;
    }

    private void loadMessages() {
        List<SystemMessage> messages = messageService.getAllMessages();

        // Filter anwenden
        String typeFilterValue = typeFilter.getValue();
        if (typeFilterValue != null && !typeFilterValue.equals("Alle")) {
            MessageType filterType = switch (typeFilterValue) {
                case "Alarm" -> MessageType.ALARM;
                case "Warnung" -> MessageType.WARNING;
                case "Info" -> MessageType.INFO;
                default -> null;
            };
            if (filterType != null) {
                messages = messages.stream()
                    .filter(m -> m.getType() == filterType)
                    .toList();
            }
        }

        String statusFilterValue = statusFilter.getValue();
        if (statusFilterValue != null && !statusFilterValue.equals("Alle")) {
            messages = switch (statusFilterValue) {
                case "Aktiv" -> messages.stream().filter(m -> !m.isAcknowledged()).toList();
                case "Quittiert" -> messages.stream().filter(m -> m.isAcknowledged() && !m.isConditionCleared()).toList();
                case "Behoben" -> messages.stream().filter(m -> m.isConditionCleared()).toList();
                default -> messages;
            };
        }

        messageGrid.setItems(messages);

        // Zähler aktualisieren
        long unackCount = messageService.getUnacknowledgedMessages().size();
        long activeCount = messageService.getActiveMessages().size();
        countLabel.setText(String.format("%d Meldungen (%d unquittiert, %d aktiv)",
            messageService.getAllMessages().size(), unackCount, activeCount));
    }

    private void acknowledgeAll() {
        int count = messageService.acknowledgeAll();
        if (count > 0) {
            Notification.show(count + " Meldung(en) quittiert", 2000, Notification.Position.BOTTOM_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            loadMessages();
        } else {
            Notification.show("Keine unquittierten Meldungen", 1500, Notification.Position.BOTTOM_CENTER);
        }
    }

    private void createTestMessage() {
        // Test-Meldung erzeugen
        int random = (int) (Math.random() * 3);
        switch (random) {
            case 0 -> messageService.alarm(MessageCategory.DOOR, "TEST_DOOR", "Test Tor-Alarm", "Test");
            case 1 -> messageService.warning(MessageCategory.SYSTEM, "TEST_WARN", "Test Warnung", "Test");
            case 2 -> messageService.info(MessageCategory.TRANSPORT, "TEST_INFO", "Test Info-Meldung", "Test");
        }
        loadMessages();
    }

    private void onMessageEvent(MessageEvent event) {
        if (ui != null && ui.isAttached()) {
            ui.access(this::loadMessages);
        }
    }
}
