package com.hydro.plsbl.ui;

import com.hydro.plsbl.security.CraneAccessService;
import com.hydro.plsbl.security.PlsblSessionContext;
import com.hydro.plsbl.service.AutoRetrievalService.StaplerAnforderung;
import com.hydro.plsbl.service.CraneStatusService;
import com.hydro.plsbl.service.ErrorBroadcaster;
import com.hydro.plsbl.service.MessageService;
import com.hydro.plsbl.service.StaplerAnforderungBroadcaster;
import com.hydro.plsbl.simulator.CraneSimulatorService;
import com.hydro.plsbl.ui.component.AccessControlBar;
import com.hydro.plsbl.ui.component.AlarmBar;
import com.hydro.plsbl.ui.component.CraneStatusBar;
import com.hydro.plsbl.ui.view.AbrufView;
import com.hydro.plsbl.ui.view.AuftraegeView;
import com.hydro.plsbl.ui.view.BarrenView;
import com.hydro.plsbl.ui.view.BeladungView;
import com.hydro.plsbl.ui.view.KranView;
import com.hydro.plsbl.ui.view.LagerView;
import com.hydro.plsbl.ui.view.MeldungenView;
import com.hydro.plsbl.ui.view.PlcStatusView;
import com.hydro.plsbl.ui.view.SawView;
import com.hydro.plsbl.ui.view.SettingsView;
import com.hydro.plsbl.ui.view.StaplerView;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.shared.Registration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * Haupt-Layout der Anwendung mit Navigation
 * 
 * Entspricht dem MainFrame aus der JavaFX-Version
 */
public class MainLayout extends AppLayout {

    private static final Logger log = LoggerFactory.getLogger(MainLayout.class);

    private Span statusLabel;
    private Span alertLabel;

    private final PlsblSessionContext sessionContext;
    private final CraneAccessService accessService;
    private final CraneStatusService craneStatusService;
    private final ErrorBroadcaster errorBroadcaster;
    private final StaplerAnforderungBroadcaster staplerBroadcaster;
    private final MessageService messageService;
    private final CraneSimulatorService simulatorService;

    private Registration errorRegistration;
    private StaplerAnforderungBroadcaster.Registration staplerRegistration;
    private AlarmBar alarmBar;

    @Autowired
    public MainLayout(PlsblSessionContext sessionContext, CraneAccessService accessService,
                      CraneStatusService craneStatusService, ErrorBroadcaster errorBroadcaster,
                      StaplerAnforderungBroadcaster staplerBroadcaster, MessageService messageService,
                      CraneSimulatorService simulatorService) {
        this.sessionContext = sessionContext;
        this.accessService = accessService;
        this.craneStatusService = craneStatusService;
        this.errorBroadcaster = errorBroadcaster;
        this.staplerBroadcaster = staplerBroadcaster;
        this.messageService = messageService;
        this.simulatorService = simulatorService;

        createHeader();
        createAlarmBar();
        createAccessControlBar();
        createDrawer();

        // CSS für blinkende Alarme hinzufügen
        getElement().executeJs(
            "const style = document.createElement('style');" +
            "style.textContent = `@keyframes alarm-blink { 0%, 100% { opacity: 1; } 50% { opacity: 0.7; } }`;" +
            "document.head.appendChild(style);"
        );
    }

    private void createAlarmBar() {
        alarmBar = new AlarmBar(messageService, simulatorService);
        addToNavbar(true, alarmBar);
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);

        UI ui = attachEvent.getUI();

        // Fuer Fehler-Benachrichtigungen registrieren (global fuer alle Views)
        errorRegistration = errorBroadcaster.register(error -> {
            log.info("MainLayout received error: {}", error.getErrorMessage());
            try {
                ui.access(() -> {
                    Notification notification = new Notification();
                    notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
                    notification.setPosition(Notification.Position.MIDDLE);
                    notification.setDuration(10000); // 10 Sekunden

                    HorizontalLayout layout = new HorizontalLayout();
                    layout.setAlignItems(FlexComponent.Alignment.CENTER);
                    layout.setSpacing(true);

                    Span text = new Span(error.getErrorType() + ": " + error.getErrorMessage());
                    text.getStyle().set("font-weight", "bold");

                    Button closeBtn = new Button(VaadinIcon.CLOSE_SMALL.create(), e -> notification.close());
                    closeBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_ERROR);

                    layout.add(text, closeBtn);
                    notification.add(layout);
                    notification.open();

                    log.info("Error notification displayed in MainLayout");
                });
            } catch (Exception e) {
                log.error("Error showing notification in MainLayout: {}", e.getMessage());
            }
        });
        log.info("MainLayout registered for error notifications");

        // Fuer Stapler-Anforderungen registrieren (Auto-Auslagerung)
        staplerRegistration = staplerBroadcaster.register(anforderungen -> {
            log.info("MainLayout received {} Stapler-Anforderungen", anforderungen.size());
            try {
                ui.access(() -> showStaplerNotification(anforderungen));
            } catch (Exception e) {
                log.error("Error showing Stapler notification: {}", e.getMessage());
            }
        });
        log.info("MainLayout registered for Stapler notifications");
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        if (errorRegistration != null) {
            errorRegistration.remove();
            errorRegistration = null;
            log.info("MainLayout unregistered from error notifications");
        }
        if (staplerRegistration != null) {
            staplerRegistration.remove();
            staplerRegistration = null;
            log.info("MainLayout unregistered from Stapler notifications");
        }
        super.onDetach(detachEvent);
    }

    /**
     * Zeigt eine Notification fuer Stapler-Anforderungen (Auto-Auslagerung)
     */
    private void showStaplerNotification(List<StaplerAnforderung> anforderungen) {
        if (anforderungen == null || anforderungen.isEmpty()) {
            return;
        }

        Notification notification = new Notification();
        notification.addThemeVariants(NotificationVariant.LUMO_PRIMARY);
        notification.setPosition(Notification.Position.TOP_END);
        notification.setDuration(15000); // 15 Sekunden

        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(false);

        // Titel
        HorizontalLayout header = new HorizontalLayout();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        Span icon = new Span(VaadinIcon.TRUCK.create());
        icon.getStyle().set("color", "#FF9800");
        Span title = new Span(" STAPLER-ANFORDERUNG");
        title.getStyle().set("font-weight", "bold").set("font-size", "14px");
        Button closeBtn = new Button(VaadinIcon.CLOSE_SMALL.create(), e -> notification.close());
        closeBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        header.add(icon, title, closeBtn);
        layout.add(header);

        // Anforderungen auflisten (max 3)
        int shown = 0;
        for (StaplerAnforderung anf : anforderungen) {
            if (shown >= 3) {
                layout.add(new Span("... und " + (anforderungen.size() - 3) + " weitere"));
                break;
            }
            String text = String.format("%s: %s -> %s", anf.getBarrenNo(), anf.getVonPlatz(), anf.getNachPlatz());
            Span line = new Span(text);
            line.getStyle().set("font-size", "12px").set("margin-left", "20px");
            layout.add(line);
            shown++;
        }

        // Abruf-Info
        if (!anforderungen.isEmpty() && anforderungen.get(0).getCalloffNo() != null) {
            Span abrufInfo = new Span("Abruf: " + anforderungen.get(0).getCalloffNo());
            abrufInfo.getStyle().set("font-size", "11px").set("color", "#666").set("margin-top", "5px");
            layout.add(abrufInfo);
        }

        notification.add(layout);
        notification.open();

        log.info("Stapler notification displayed: {} Anforderungen", anforderungen.size());
    }
    
    private void createHeader() {
        // Logo
        H1 logo = new H1("PLS Barrenlager");
        logo.getStyle()
            .set("font-size", "var(--lumo-font-size-l)")
            .set("margin", "0")
            .set("color", "white");

        // Kran-Status-Anzeige
        CraneStatusBar craneStatusBar = new CraneStatusBar(craneStatusService);

        // Status-Anzeigen
        statusLabel = new Span("Status: OK");
        statusLabel.getStyle().set("color", "lightgreen");

        alertLabel = new Span("0 Alarme");
        alertLabel.getStyle().set("color", "white");

        HorizontalLayout statusBar = new HorizontalLayout(craneStatusBar, statusLabel, alertLabel);
        statusBar.setSpacing(true);
        statusBar.setAlignItems(FlexComponent.Alignment.CENTER);
        statusBar.getStyle().set("margin-left", "auto");

        // Header zusammenbauen
        HorizontalLayout header = new HorizontalLayout(
            new DrawerToggle(),
            logo,
            statusBar
        );
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        header.setWidthFull();
        header.setPadding(true);
        header.getStyle()
            .set("background-color", "#1976D2")
            .set("color", "white");

        addToNavbar(header);
    }
    
    private void createAccessControlBar() {
        // Zugriffskontroll-Leiste unter dem Header
        AccessControlBar accessBar = new AccessControlBar(sessionContext, accessService);
        accessBar.setWidthFull();
        
        addToNavbar(true, accessBar);  // true = unter dem Header
    }
    
    private void createDrawer() {
        // Navigation Tabs
        Tabs tabs = new Tabs();
        tabs.setOrientation(Tabs.Orientation.VERTICAL);
        
        // Haupt-Views (wie F4-F8 in JavaFX)
        tabs.add(createTab(VaadinIcon.STORAGE, "Lager [F4]", LagerView.class));
        tabs.add(createTab(VaadinIcon.CLIPBOARD, "Aufträge [F5]", AuftraegeView.class));
        tabs.add(createTab(VaadinIcon.CUBE, "Barren [F6]", BarrenView.class));
        tabs.add(createTab(VaadinIcon.COG_O, "Kran [F7]", KranView.class));
        tabs.add(createTab(VaadinIcon.SCISSORS, "Säge [F8]", SawView.class));
        tabs.add(createTab(VaadinIcon.TRUCK, "Beladung [F9]", BeladungView.class));
        tabs.add(createTab(VaadinIcon.TRUCK, "Stapler", StaplerView.class));
        tabs.add(createTab(VaadinIcon.CLIPBOARD_CHECK, "Abrufe [F10]", AbrufView.class));
        tabs.add(createTab(VaadinIcon.PLUG, "SPS-Status", PlcStatusView.class));
        tabs.add(createTab(VaadinIcon.BELL, "Meldungen", MeldungenView.class));
        tabs.add(createTab(VaadinIcon.COGS, "Einstellungen", SettingsView.class));
        
        // Info-Bereich
        VerticalLayout drawer = new VerticalLayout();
        drawer.add(tabs);
        drawer.add(new Span("─────────────"));
        drawer.add(new Span("PLSBL v4.0"));
        drawer.add(new Span("Spring Boot + Vaadin"));
        
        addToDrawer(drawer);
    }
    
    @SuppressWarnings("unchecked")
    private <T extends Component> Tab createTab(VaadinIcon icon, String label, Class<T> viewClass) {
        RouterLink link = new RouterLink();
        link.add(icon.create(), new Span(" " + label));
        link.setRoute((Class<? extends Component>) viewClass);
        link.getStyle().set("text-decoration", "none");
        
        Tab tab = new Tab(link);
        return tab;
    }
    
    // === Public Methods für Status-Updates ===
    
    public void updateStatus(String text, String color) {
        if (statusLabel != null) {
            statusLabel.setText("Status: " + text);
            statusLabel.getStyle().set("color", color);
        }
    }
    
    public void updateAlertCount(int count) {
        if (alertLabel != null) {
            alertLabel.setText(count + " Alarme");
            alertLabel.getStyle().set("color", count > 0 ? "red" : "white");
        }
    }
}
