package com.hydro.plsbl.ui;

import com.hydro.plsbl.security.CraneAccessService;
import com.hydro.plsbl.security.PlsblSessionContext;
import com.hydro.plsbl.service.CraneStatusService;
import com.hydro.plsbl.ui.component.AccessControlBar;
import com.hydro.plsbl.ui.component.CraneStatusBar;
import com.hydro.plsbl.ui.view.AuftraegeView;
import com.hydro.plsbl.ui.view.BarrenView;
import com.hydro.plsbl.ui.view.KranView;
import com.hydro.plsbl.ui.view.LagerView;
import com.hydro.plsbl.ui.view.PlcStatusView;
import com.hydro.plsbl.ui.view.SawView;
import com.hydro.plsbl.ui.view.SettingsView;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.RouterLink;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Haupt-Layout der Anwendung mit Navigation
 * 
 * Entspricht dem MainFrame aus der JavaFX-Version
 */
public class MainLayout extends AppLayout {
    
    private Span statusLabel;
    private Span alertLabel;

    private final PlsblSessionContext sessionContext;
    private final CraneAccessService accessService;
    private final CraneStatusService craneStatusService;

    @Autowired
    public MainLayout(PlsblSessionContext sessionContext, CraneAccessService accessService,
                      CraneStatusService craneStatusService) {
        this.sessionContext = sessionContext;
        this.accessService = accessService;
        this.craneStatusService = craneStatusService;
        
        createHeader();
        createAccessControlBar();
        createDrawer();
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
