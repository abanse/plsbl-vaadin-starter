package com.hydro.plsbl.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service zur Verwaltung der Kran-Steuerungsberechtigung.
 * 
 * Stellt sicher dass:
 * 1. Nur berechtigte Arbeitsplätze den Kran steuern können
 * 2. Nur EIN Arbeitsplatz gleichzeitig die Steuerung hat
 * 3. Inaktive Steuerungen automatisch freigegeben werden
 */
@Service
public class CraneAccessService {
    
    private static final Logger log = LoggerFactory.getLogger(CraneAccessService.class);
    
    private final WorkstationConfig config;
    
    // Aktueller Inhaber des Steuerungs-Tokens
    private volatile ControlToken currentController = null;
    
    // Alle aktiven Sessions (für Übersicht)
    private final Map<String, SessionInfo> activeSessions = new ConcurrentHashMap<>();
    
    public CraneAccessService(WorkstationConfig config) {
        this.config = config;
    }
    
    // ========================================================================
    // Haupt-API
    // ========================================================================
    
    /**
     * Prüft ob ein Arbeitsplatz aktuell die Kran-Steuerung hat
     */
    public boolean hasControl(String workstationId) {
        if (!config.isTokenBasedControl()) {
            // Ohne Token: Nur Whitelist prüfen
            return config.isAllowedToControlCrane(workstationId);
        }
        
        // Mit Token: Muss der aktuelle Inhaber sein
        return currentController != null && 
               currentController.workstationId.equals(workstationId);
    }
    
    /**
     * Fordert die Kran-Steuerung an
     * 
     * @return true wenn erfolgreich, false wenn bereits vergeben
     */
    public synchronized ControlRequestResult requestControl(String workstationId, String userId) {
        log.info("Control request from {} (user: {})", workstationId, userId);
        
        // 1. Grundsätzlich berechtigt?
        if (!config.isAllowedToControlCrane(workstationId)) {
            log.warn("Workstation {} is not allowed to control crane", workstationId);
            return ControlRequestResult.notAllowed(
                "Dieser Arbeitsplatz ist nicht zur Kran-Steuerung berechtigt"
            );
        }
        
        // 2. Ohne Token-System: Direkt erlauben
        if (!config.isTokenBasedControl()) {
            return ControlRequestResult.granted();
        }
        
        // 3. Bereits Controller?
        if (currentController != null && currentController.workstationId.equals(workstationId)) {
            // Verlängern
            currentController.lastActivity = LocalDateTime.now();
            return ControlRequestResult.granted();
        }
        
        // 4. Anderer hat Steuerung?
        if (currentController != null) {
            log.info("Control denied - currently held by {}", currentController.workstationId);
            return ControlRequestResult.alreadyTaken(
                "Steuerung ist bereits vergeben an: " + currentController.workstationId,
                currentController.workstationId,
                currentController.userId
            );
        }
        
        // 5. Token vergeben
        currentController = new ControlToken(workstationId, userId);
        log.info("Control granted to {} (user: {})", workstationId, userId);
        
        return ControlRequestResult.granted();
    }
    
    /**
     * Gibt die Kran-Steuerung frei
     */
    public synchronized void releaseControl(String workstationId) {
        if (currentController != null && currentController.workstationId.equals(workstationId)) {
            log.info("Control released by {}", workstationId);
            currentController = null;
        }
    }
    
    /**
     * Erzwingt die Freigabe (Admin-Funktion)
     */
    public synchronized void forceReleaseControl(String adminUser) {
        if (currentController != null) {
            log.warn("Control forcibly released by admin {} (was held by {})", 
                     adminUser, currentController.workstationId);
            currentController = null;
        }
    }
    
    /**
     * Meldet Aktivität (verhindert Timeout)
     */
    public void heartbeat(String workstationId) {
        if (currentController != null && currentController.workstationId.equals(workstationId)) {
            currentController.lastActivity = LocalDateTime.now();
        }
        
        // Session aktualisieren
        SessionInfo session = activeSessions.get(workstationId);
        if (session != null) {
            session.lastActivity = LocalDateTime.now();
        }
    }
    
    /**
     * Registriert eine neue Session
     */
    public void registerSession(String workstationId, String userId, AccessMode mode) {
        activeSessions.put(workstationId, new SessionInfo(workstationId, userId, mode));
        log.debug("Session registered: {} (user: {}, mode: {})", workstationId, userId, mode);
    }
    
    /**
     * Entfernt eine Session
     */
    public void unregisterSession(String workstationId) {
        activeSessions.remove(workstationId);
        releaseControl(workstationId);  // Falls Controller war
        log.debug("Session unregistered: {}", workstationId);
    }
    
    // ========================================================================
    // Status-Abfragen
    // ========================================================================
    
    /**
     * Liefert den aktuellen Controller (falls vorhanden)
     */
    public Optional<ControlToken> getCurrentController() {
        return Optional.ofNullable(currentController);
    }
    
    /**
     * Liefert den Zugriffsmodus für einen Arbeitsplatz
     */
    public AccessMode getAccessMode(String workstationId) {
        return config.getModeFor(workstationId);
    }
    
    /**
     * Prüft ob Token-basierte Steuerung aktiv ist
     */
    public boolean isTokenBasedControl() {
        return config.isTokenBasedControl();
    }
    
    /**
     * Liefert alle aktiven Sessions
     */
    public Map<String, SessionInfo> getActiveSessions() {
        return Map.copyOf(activeSessions);
    }
    
    // ========================================================================
    // Timeout-Prüfung (scheduled)
    // ========================================================================
    
    /**
     * Prüft periodisch auf Timeout und gibt inaktive Steuerungen frei
     */
    @Scheduled(fixedRate = 30000)  // Alle 30 Sekunden
    public synchronized void checkTimeout() {
        if (currentController == null) {
            return;
        }
        
        LocalDateTime timeout = LocalDateTime.now()
            .minusSeconds(config.getTokenTimeoutSeconds());
        
        if (currentController.lastActivity.isBefore(timeout)) {
            log.warn("Control token timeout - releasing from {} (inactive since {})",
                     currentController.workstationId, currentController.lastActivity);
            currentController = null;
        }
    }
    
    // ========================================================================
    // Inner Classes
    // ========================================================================
    
    /**
     * Token für die Kran-Steuerung
     */
    public static class ControlToken {
        public final String workstationId;
        public final String userId;
        public final LocalDateTime grantedAt;
        public LocalDateTime lastActivity;
        
        public ControlToken(String workstationId, String userId) {
            this.workstationId = workstationId;
            this.userId = userId;
            this.grantedAt = LocalDateTime.now();
            this.lastActivity = LocalDateTime.now();
        }
    }
    
    /**
     * Ergebnis einer Steuerungs-Anfrage
     */
    public static class ControlRequestResult {
        public final boolean granted;
        public final String message;
        public final String currentHolder;
        public final String currentUser;
        
        private ControlRequestResult(boolean granted, String message, 
                                     String currentHolder, String currentUser) {
            this.granted = granted;
            this.message = message;
            this.currentHolder = currentHolder;
            this.currentUser = currentUser;
        }
        
        public static ControlRequestResult granted() {
            return new ControlRequestResult(true, "Steuerung gewährt", null, null);
        }
        
        public static ControlRequestResult notAllowed(String message) {
            return new ControlRequestResult(false, message, null, null);
        }
        
        public static ControlRequestResult alreadyTaken(String message, 
                                                         String holder, String user) {
            return new ControlRequestResult(false, message, holder, user);
        }
    }
    
    /**
     * Info über eine aktive Session
     */
    public static class SessionInfo {
        public final String workstationId;
        public final String userId;
        public final AccessMode mode;
        public final LocalDateTime connectedAt;
        public LocalDateTime lastActivity;
        
        public SessionInfo(String workstationId, String userId, AccessMode mode) {
            this.workstationId = workstationId;
            this.userId = userId;
            this.mode = mode;
            this.connectedAt = LocalDateTime.now();
            this.lastActivity = LocalDateTime.now();
        }
    }
}
