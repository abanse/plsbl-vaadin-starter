package com.hydro.plsbl.ui;

import com.hydro.plsbl.security.CraneAccessService;
import com.hydro.plsbl.security.PlsblSessionContext;
import com.hydro.plsbl.service.CraneStatusService;
import com.hydro.plsbl.service.ErrorBroadcaster;
import com.hydro.plsbl.ui.component.AccessControlBar;
import com.hydro.plsbl.ui.component.CraneStatusBar;
import com.hydro.plsbl.ui.view.AbrufView;
import com.hydro.plsbl.ui.view.AuftraegeView;
import com.hydro.plsbl.ui.view.BarrenView;
import com.hydro.plsbl.ui.view.BeladungView;
import com.hydro.plsbl.ui.view.KranView;
import com.hydro.plsbl.ui.view.LagerView;
import com.hydro.plsbl.ui.view.PlcStatusView;
import com.hydro.plsbl.ui.view.SawView;
import com.hydro.plsbl.ui.view.SettingsView;
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

    private Registration errorRegistration;

    @Autowired
    public MainLayout(PlsblSessionContext sessionContext, CraneAccessService accessService,
                      CraneStatusService craneStatusService, ErrorBroadcaster errorBroadcaster) {
        this.sessionContext = sessionContext;
        this.accessService = accessService;
        this.craneStatusService = craneStatusService;
        this.errorBroadcaster = errorBroadcaster;

        createHeader();
        createAccessControlBar();
        createDrawer();
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);

        // Für Fehler-Benachrichtigungen registrieren (global für alle Views)
        UI ui = attachEvent.getUI();
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
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        if (errorRegistration != null) {
            errorRegistration.remove();
            errorRegistration = null;
            log.info("MainLayout unregistered from error notifications");
        }
        super.onDetach(detachEvent);
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
        tabs.add(createTab(VaadinIcon.CLIPBOARD_CHECK, "Abrufe [F10]", AbrufView.class));
        tabs.add(createTab(VaadinIcon.PLUG, "SPS-Status", PlcStatusView.class));
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
