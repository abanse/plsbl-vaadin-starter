package com.hydro.plsbl.ui.component;

import com.hydro.plsbl.service.MessageService;
import com.hydro.plsbl.service.MessageService.MessageEvent;
import com.hydro.plsbl.service.MessageService.SystemMessage;
import com.hydro.plsbl.simulator.CraneSimulatorService;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

/**
 * Alarm-Anzeige-Leiste.
 *
 * Zeigt aktive Alarme an und ermöglicht deren Quittierung.
 * Wird rot/blinkend wenn unquittierte Alarme existieren.
 */
public class AlarmBar extends HorizontalLayout {

    private static final Logger log = LoggerFactory.getLogger(AlarmBar.class);

    private final MessageService messageService;
    private final CraneSimulatorService simulatorService;
    private final Div alarmContainer;
    private final Button acknowledgeAllBtn;
    private final Span statusIcon;
    private final Span cranePausedLabel;

    private Consumer<MessageEvent> messageListener;
    private UI ui;

    public AlarmBar(MessageService messageService, CraneSimulatorService simulatorService) {
        this.messageService = messageService;
        this.simulatorService = simulatorService;

        setWidthFull();
        setAlignItems(FlexComponent.Alignment.CENTER);
        setPadding(true);
        setSpacing(true);
        getStyle()
            .set("background-color", "#333")
            .set("min-height", "40px")
            .set("display", "none");  // Initial versteckt

        // Alarm-Icon
        statusIcon = new Span();
        statusIcon.getStyle()
            .set("font-size", "20px")
            .set("margin-right", "10px");
        add(statusIcon);

        // Kran-Pausiert-Anzeige
        cranePausedLabel = new Span("\u23F8 KRAN GESTOPPT");
        cranePausedLabel.getStyle()
            .set("background-color", "#b71c1c")
            .set("color", "white")
            .set("font-weight", "bold")
            .set("padding", "4px 12px")
            .set("border-radius", "4px")
            .set("margin-right", "10px")
            .set("animation", "alarm-blink 0.5s infinite");
        cranePausedLabel.setVisible(false);
        add(cranePausedLabel);

        // Container für Alarm-Texte
        alarmContainer = new Div();
        alarmContainer.getStyle()
            .set("flex-grow", "1")
            .set("color", "white")
            .set("font-weight", "bold");
        add(alarmContainer);

        // Quittieren-Button
        acknowledgeAllBtn = new Button("Alle quittieren", VaadinIcon.CHECK.create());
        acknowledgeAllBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        acknowledgeAllBtn.addClickListener(e -> acknowledgeAll());
        add(acknowledgeAllBtn);
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        this.ui = attachEvent.getUI();

        // Message-Listener registrieren
        messageListener = this::onMessageEvent;
        messageService.addListener(messageListener);

        // Initiale Anzeige aktualisieren
        updateDisplay();
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        super.onDetach(detachEvent);
        if (messageListener != null) {
            messageService.removeListener(messageListener);
            messageListener = null;
        }
    }

    private void onMessageEvent(MessageEvent event) {
        if (ui != null && ui.isAttached()) {
            ui.access(this::updateDisplay);
        }
    }

    private void updateDisplay() {
        // Nur DOOR-Alarme in der AlarmBar anzeigen
        List<SystemMessage> doorAlarms = messageService.getActiveMessages().stream()
            .filter(m -> m.getCategory() == MessageService.MessageCategory.DOOR)
            .toList();

        // Kran-Pausiert Status prüfen
        boolean cranePaused = simulatorService.isPaused();
        cranePausedLabel.setVisible(cranePaused);

        if (doorAlarms.isEmpty()) {
            // Keine Alarme - verstecken
            getStyle().set("display", "none");
            return;
        }

        // Anzeigen
        getStyle().set("display", "flex");

        // Unquittierte Alarme?
        boolean hasUnacknowledged = doorAlarms.stream().anyMatch(a -> !a.isAcknowledged());

        if (hasUnacknowledged) {
            // Rot blinkend
            getStyle()
                .set("background-color", "#d32f2f")
                .set("animation", "alarm-blink 0.5s infinite");
            statusIcon.setText("\u26A0");  // Warning sign
            statusIcon.getStyle().set("color", "yellow");
            acknowledgeAllBtn.setEnabled(true);
        } else {
            // Orange (quittiert, aber Bedingung noch aktiv)
            getStyle()
                .set("background-color", "#ff9800")
                .remove("animation");
            statusIcon.setText("\u2139");  // Info sign
            statusIcon.getStyle().set("color", "white");
            acknowledgeAllBtn.setEnabled(false);
        }

        // Alarm-Texte zusammenbauen
        StringBuilder sb = new StringBuilder();
        for (SystemMessage msg : doorAlarms) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append(msg.getText());
            if (msg.isAcknowledged()) {
                sb.append(" [quittiert]");
            }
            if (msg.isConditionCleared()) {
                sb.append(" [behoben]");
            }
        }
        alarmContainer.setText(sb.toString());
    }

    private void acknowledgeAll() {
        int count = messageService.acknowledgeAll();
        if (count > 0) {
            Notification.show(count + " Meldung(en) quittiert", 2000, Notification.Position.BOTTOM_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        }
        updateDisplay();
    }

    /**
     * CSS für blinkende Animation (sollte global eingebunden werden)
     */
    public static String getBlinkingCss() {
        return """
            @keyframes alarm-blink {
                0%, 100% { opacity: 1; }
                50% { opacity: 0.7; }
            }
            """;
    }
}
