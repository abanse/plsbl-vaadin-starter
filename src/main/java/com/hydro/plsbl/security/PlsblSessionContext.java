package com.hydro.plsbl.security;

import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Session-Kontext für PLSBL.
 * 
 * Wird automatisch pro Vaadin-Session erstellt und enthält:
 * - Arbeitsplatz-ID (Hostname/IP)
 * - Benutzer-ID
 * - Zugriffsmodus (CONTROL/VIEW)
 * - Ob aktuell Kran-Steuerung aktiv ist
 */
@Component
@VaadinSessionScope
public class PlsblSessionContext {
    
    private static final Logger log = LoggerFactory.getLogger(PlsblSessionContext.class);
    
    private final CraneAccessService accessService;
    private final WorkstationConfig config;
    
    private String workstationId;
    private String userId;
    private AccessMode accessMode;
    private boolean hasControl = false;
    
    public PlsblSessionContext(CraneAccessService accessService, WorkstationConfig config) {
        this.accessService = accessService;
        this.config = config;
    }
    
    @PostConstruct
    public void init() {
        // Arbeitsplatz ermitteln
        this.workstationId = detectWorkstationId();
        this.userId = detectUserId();
        this.accessMode = config.getModeFor(workstationId);
        
        // Session registrieren
        accessService.registerSession(workstationId, userId, accessMode);
        
        log.info("Session initialized: workstation={}, user={}, mode={}", 
                 workstationId, userId, accessMode);
    }
    
    @PreDestroy
    public void cleanup() {
        // Session abmelden
        accessService.unregisterSession(workstationId);
        log.info("Session cleanup: {}", workstationId);
    }
    
    // ========================================================================
    // Arbeitsplatz-Erkennung
    // ========================================================================
    
    private String detectWorkstationId() {
        // 1. Aus Request (Client-IP)
        VaadinRequest request = VaadinRequest.getCurrent();
        if (request != null) {
            String clientIp = request.getRemoteAddr();
            
            // X-Forwarded-For Header (bei Proxy)
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isEmpty()) {
                clientIp = forwarded.split(",")[0].trim();
            }
            
            // Hostname auflösen (optional)
            try {
                InetAddress addr = InetAddress.getByName(clientIp);
                String hostname = addr.getHostName();
                if (!hostname.equals(clientIp)) {
                    return hostname.toUpperCase();
                }
            } catch (UnknownHostException e) {
                // Hostname nicht auflösbar - IP verwenden
            }
            
            return clientIp;
        }
        
        // 2. Fallback: Server-Hostname
        try {
            return InetAddress.getLocalHost().getHostName().toUpperCase();
        } catch (UnknownHostException e) {
            return "UNKNOWN";
        }
    }
    
    private String detectUserId() {
        // 1. Aus Vaadin Session (falls Login implementiert)
        VaadinSession session = VaadinSession.getCurrent();
        if (session != null) {
            Object user = session.getAttribute("userId");
            if (user != null) {
                return user.toString();
            }
        }
        
        // 2. Aus Request Header (falls SSO/Proxy)
        VaadinRequest request = VaadinRequest.getCurrent();
        if (request != null) {
            String remoteUser = request.getRemoteUser();
            if (remoteUser != null) {
                return remoteUser;
            }
            
            // Custom Header (z.B. von Apache/nginx)
            String userHeader = request.getHeader("X-Remote-User");
            if (userHeader != null) {
                return userHeader;
            }
        }
        
        // 3. Fallback
        return "anonymous";
    }
    
    // ========================================================================
    // Kran-Steuerung
    // ========================================================================
    
    /**
     * Fordert die Kran-Steuerung an
     */
    public CraneAccessService.ControlRequestResult requestCraneControl() {
        CraneAccessService.ControlRequestResult result = 
            accessService.requestControl(workstationId, userId);
        
        if (result.granted) {
            this.hasControl = true;
        }
        
        return result;
    }
    
    /**
     * Gibt die Kran-Steuerung frei
     */
    public void releaseCraneControl() {
        accessService.releaseControl(workstationId);
        this.hasControl = false;
    }
    
    /**
     * Meldet Aktivität (verhindert Timeout)
     */
    public void heartbeat() {
        accessService.heartbeat(workstationId);
    }
    
    // ========================================================================
    // Berechtigungs-Prüfungen
    // ========================================================================
    
    /**
     * Kann dieser Arbeitsplatz den Kran ÜBERHAUPT steuern? (Grundberechtigung)
     */
    public boolean canControlCrane() {
        return accessMode == AccessMode.CONTROL || 
               config.isAllowedToControlCrane(workstationId);
    }
    
    /**
     * Hat dieser Arbeitsplatz AKTUELL die Kran-Steuerung? (Token)
     */
    public boolean hasCraneControl() {
        return hasControl && accessService.hasControl(workstationId);
    }
    
    /**
     * Ist dieser Arbeitsplatz im Nur-Ansicht-Modus?
     */
    public boolean isViewOnly() {
        return accessMode == AccessMode.VIEW;
    }
    
    /**
     * Kann Aktionen ausführen (nicht nur View)?
     */
    public boolean canPerformActions() {
        return accessMode != AccessMode.VIEW;
    }
    
    // ========================================================================
    // Getters
    // ========================================================================
    
    public String getWorkstationId() {
        return workstationId;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public AccessMode getAccessMode() {
        return accessMode;
    }
    
    public boolean isHasControl() {
        return hasControl;
    }
    
    /**
     * Liefert eine Beschreibung für die UI
     */
    public String getModeDescription() {
        if (hasCraneControl()) {
            return "Steuerung aktiv";
        } else if (canControlCrane()) {
            return "Steuerung möglich";
        } else {
            return "Nur Ansicht";
        }
    }
}
