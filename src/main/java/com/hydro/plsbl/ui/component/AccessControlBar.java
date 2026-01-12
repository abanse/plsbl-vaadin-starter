package com.hydro.plsbl.ui.component;

import com.hydro.plsbl.security.AccessMode;
import com.hydro.plsbl.security.CraneAccessService;
import com.hydro.plsbl.security.PlsblSessionContext;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * UI-Komponente zur Anzeige und Steuerung des Zugriffsmodus.
 * 
 * Zeigt an:
 * - Aktueller Arbeitsplatz
 * - Aktueller Modus (Steuerung/Ansicht)
 * - Button zum Anfordern/Freigeben der Steuerung
 */
public class AccessControlBar extends HorizontalLayout {
    
    private static final Logger log = LoggerFactory.getLogger(AccessControlBar.class);
    
    private final PlsblSessionContext sessionContext;
    private final CraneAccessService accessService;
    
    private Span workstationLabel;
    private Span modeLabel;
    private Span controllerLabel;
    private Button controlButton;
    
    // Heartbeat für Token-Verlängerung
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> heartbeatTask;
    
    public AccessControlBar(PlsblSessionContext sessionContext, CraneAccessService accessService) {
        this.sessionContext = sessionContext;
        this.accessService = accessService;
        
        setSpacing(true);
        setAlignItems(Alignment.CENTER);
        getStyle()
            .set("padding", "5px 15px")
            .set("background-color", "#f5f5f5")
            .set("border-radius", "4px");
        
        createComponents();
        updateDisplay();
    }
    
    private void createComponents() {
        // Arbeitsplatz-Anzeige
        workstationLabel = new Span();
        workstationLabel.getStyle()
            .set("font-weight", "bold")
            .set("color", "#1976D2");
        
        // Modus-Anzeige
        modeLabel = new Span();
        modeLabel.getStyle().set("padding", "2px 8px");
        
        // Aktueller Controller
        controllerLabel = new Span();
        controllerLabel.getStyle().set("color", "#666");
        
        // Steuerungs-Button
        controlButton = new Button();
        controlButton.addClickListener(e -> toggleControl());
        
        // Separator
        Span separator = new Span("|");
        separator.getStyle().set("color", "#ccc");
        
        add(
            VaadinIcon.DESKTOP.create(),
            workstationLabel,
            separator,
            modeLabel,
            controllerLabel,
            controlButton
        );
    }
    
    private void updateDisplay() {
        // Arbeitsplatz
        workstationLabel.setText(sessionContext.getWorkstationId());
        
        // Modus
        AccessMode mode = sessionContext.getAccessMode();
        boolean hasControl = sessionContext.hasCraneControl();
        
        if (hasControl) {
            modeLabel.setText("🔓 STEUERUNG AKTIV");
            modeLabel.getStyle()
                .set("background-color", "#4CAF50")
                .set("color", "white")
                .set("border-radius", "4px");
            
            controlButton.setText("Steuerung freigeben");
            controlButton.setIcon(VaadinIcon.UNLOCK.create());
            controlButton.removeThemeVariants(ButtonVariant.LUMO_PRIMARY);
            controlButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
            controlButton.setVisible(true);
            controllerLabel.setVisible(false);
            
        } else if (sessionContext.canControlCrane()) {
            modeLabel.setText("⏸ Steuerung möglich");
            modeLabel.getStyle()
                .set("background-color", "#FFC107")
                .set("color", "black")
                .set("border-radius", "4px");
            
            controlButton.setText("Steuerung anfordern");
            controlButton.setIcon(VaadinIcon.LOCK.create());
            controlButton.removeThemeVariants(ButtonVariant.LUMO_ERROR);
            controlButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            controlButton.setVisible(true);
            
            // Zeige wer aktuell steuert
            accessService.getCurrentController().ifPresentOrElse(
                controller -> {
                    controllerLabel.setText("Aktiv: " + controller.workstationId);
                    controllerLabel.setVisible(true);
                },
                () -> controllerLabel.setVisible(false)
            );
            
        } else {
            modeLabel.setText("👁 NUR ANSICHT");
            modeLabel.getStyle()
                .set("background-color", "#9E9E9E")
                .set("color", "white")
                .set("border-radius", "4px");
            
            controlButton.setVisible(false);
            
            // Zeige wer aktuell steuert
            accessService.getCurrentController().ifPresentOrElse(
                controller -> {
                    controllerLabel.setText("Steuerung: " + controller.workstationId);
                    controllerLabel.setVisible(true);
                },
                () -> controllerLabel.setVisible(false)
            );
        }
    }
    
    private void toggleControl() {
        if (sessionContext.hasCraneControl()) {
            // Freigeben
            sessionContext.releaseCraneControl();
            stopHeartbeat();
            
            Notification.show("Kran-Steuerung freigegeben", 3000, Notification.Position.BOTTOM_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            
        } else {
            // Anfordern
            CraneAccessService.ControlRequestResult result = sessionContext.requestCraneControl();
            
            if (result.granted) {
                startHeartbeat();
                Notification.show("Kran-Steuerung übernommen", 3000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } else {
                Notification notification = new Notification();
                notification.setText(result.message);
                notification.setDuration(5000);
                notification.setPosition(Notification.Position.MIDDLE);
                notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
                notification.open();
            }
        }
        
        updateDisplay();
    }
    
    // ========================================================================
    // Heartbeat
    // ========================================================================
    
    private void startHeartbeat() {
        if (heartbeatTask == null || heartbeatTask.isCancelled()) {
            heartbeatTask = scheduler.scheduleAtFixedRate(
                () -> {
                    try {
                        sessionContext.heartbeat();
                    } catch (Exception e) {
                        log.warn("Heartbeat failed", e);
                    }
                },
                30, 30, TimeUnit.SECONDS  // Alle 30 Sekunden
            );
            log.debug("Heartbeat started");
        }
    }
    
    private void stopHeartbeat() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
            heartbeatTask = null;
            log.debug("Heartbeat stopped");
        }
    }
    
    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        
        // Falls bereits Controller, Heartbeat starten
        if (sessionContext.hasCraneControl()) {
            startHeartbeat();
        }
        
        // Periodische UI-Aktualisierung
        UI ui = attachEvent.getUI();
        scheduler.scheduleAtFixedRate(
            () -> ui.access(this::updateDisplay),
            5, 5, TimeUnit.SECONDS
        );
    }
    
    @Override
    protected void onDetach(DetachEvent detachEvent) {
        super.onDetach(detachEvent);
        stopHeartbeat();
    }
}
