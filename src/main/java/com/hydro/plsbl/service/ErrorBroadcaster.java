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
 * Broadcaster für Fehlermeldungen die im UI angezeigt werden sollen.
 * Verwendet Vaadin Push um Notifications an alle verbundenen Clients zu senden.
 */
@Service
public class ErrorBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(ErrorBroadcaster.class);

    private final Executor executor = Executors.newSingleThreadExecutor();
    private final LinkedList<Consumer<ErrorMessage>> listeners = new LinkedList<>();

    /**
     * Registriert einen Listener für Fehlermeldungen
     */
    public synchronized Registration register(Consumer<ErrorMessage> listener) {
        listeners.add(listener);
        log.info("Error listener registered, total: {}", listeners.size());
        return () -> {
            synchronized (ErrorBroadcaster.this) {
                listeners.remove(listener);
                log.info("Error listener removed, total: {}", listeners.size());
            }
        };
    }

    /**
     * Sendet eine Fehlermeldung an alle registrierten Listener
     */
    public synchronized void broadcast(String errorType, String errorMessage) {
        log.info("=== ERROR BROADCAST: {} - {} an {} Listener ===", errorType, errorMessage, listeners.size());
        if (listeners.isEmpty()) {
            log.warn("Keine Listener registriert für Fehler!");
        }
        ErrorMessage error = new ErrorMessage(errorType, errorMessage);
        for (Consumer<ErrorMessage> listener : listeners) {
            executor.execute(() -> {
                try {
                    log.info("Rufe Error-Listener auf...");
                    listener.accept(error);
                    log.info("Error-Listener erfolgreich aufgerufen");
                } catch (Exception e) {
                    log.error("Fehler beim Error-Broadcasting: {}", e.getMessage(), e);
                }
            });
        }
    }

    /**
     * Fehlermeldung Container
     */
    public static class ErrorMessage {
        private final String errorType;
        private final String errorMessage;
        private final long timestamp;

        public ErrorMessage(String errorType, String errorMessage) {
            this.errorType = errorType;
            this.errorMessage = errorMessage;
            this.timestamp = System.currentTimeMillis();
        }

        public String getErrorType() {
            return errorType;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }
}
