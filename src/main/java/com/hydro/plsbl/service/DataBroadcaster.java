package com.hydro.plsbl.service;

import com.vaadin.flow.shared.Registration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Broadcaster für allgemeine Daten-Updates.
 * Ermöglicht die Synchronisation zwischen verschiedenen Views wenn sich Daten ändern.
 *
 * Verwendung:
 * - In Views: registration = dataBroadcaster.register(event -> ui.access(() -> loadData()));
 * - In Services: dataBroadcaster.broadcast(DataEventType.CALLOFF_CHANGED);
 */
@Service
public class DataBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(DataBroadcaster.class);

    private final Executor executor = Executors.newSingleThreadExecutor();
    private final LinkedList<Consumer<DataEvent>> listeners = new LinkedList<>();

    /**
     * Registriert einen Listener für Daten-Events
     */
    public synchronized Registration register(Consumer<DataEvent> listener) {
        listeners.add(listener);
        log.debug("Listener registriert, aktive Listener: {}", listeners.size());
        return () -> {
            synchronized (DataBroadcaster.this) {
                listeners.remove(listener);
                log.debug("Listener entfernt, aktive Listener: {}", listeners.size());
            }
        };
    }

    /**
     * Sendet ein Event an alle registrierten Listener
     */
    public synchronized void broadcast(DataEvent event) {
        log.info("=== BROADCAST: {} an {} Listener ===", event.getType(), listeners.size());
        if (listeners.isEmpty()) {
            log.warn("Keine Listener registriert für Event: {}", event.getType());
        }
        for (Consumer<DataEvent> listener : listeners) {
            executor.execute(() -> {
                try {
                    log.info("Rufe Listener auf für Event: {}", event.getType());
                    listener.accept(event);
                    log.info("Listener erfolgreich aufgerufen für Event: {}", event.getType());
                } catch (Exception e) {
                    log.error("Fehler beim Broadcasting: {}", e.getMessage(), e);
                }
            });
        }
    }

    /**
     * Convenience-Methode: Broadcast eines einfachen Events
     */
    public void broadcast(DataEventType type) {
        broadcast(new DataEvent(type));
    }

    /**
     * Event-Typen für Daten-Broadcasts
     */
    public enum DataEventType {
        CALLOFF_CHANGED,       // Abruf wurde geändert (approve, revoke, complete, etc.)
        CALLOFF_CREATED,       // Neuer Abruf erstellt
        TRANSPORT_CHANGED,     // Transport-Auftrag geändert
        INGOT_CHANGED,         // Barren geändert
        STOCKYARD_CHANGED,     // Lagerplatz geändert
        REFRESH_ALL            // Alle Daten neu laden
    }

    /**
     * Event-Daten für Daten-Broadcasts
     */
    public static class DataEvent {
        private final DataEventType type;
        private final Long entityId;
        private final String message;

        public DataEvent(DataEventType type) {
            this(type, null, null);
        }

        public DataEvent(DataEventType type, Long entityId) {
            this(type, entityId, null);
        }

        public DataEvent(DataEventType type, Long entityId, String message) {
            this.type = type;
            this.entityId = entityId;
            this.message = message;
        }

        public DataEventType getType() {
            return type;
        }

        public Long getEntityId() {
            return entityId;
        }

        public String getMessage() {
            return message;
        }
    }
}
