package com.hydro.plsbl.service;

import com.hydro.plsbl.service.AutoRetrievalService.StaplerAnforderung;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Broadcaster fuer Stapler-Anforderungen.
 *
 * Benachrichtigt alle registrierten Listener (UI-Views) wenn neue
 * Stapler-Anforderungen fuer Auto-Auslagerung erstellt werden.
 *
 * Verwendung:
 * - AutoRetrievalService ruft broadcast() auf wenn Anforderungen erstellt werden
 * - LagerView oder StaplerView registrieren sich als Listener
 * - Listener zeigt Notification oder aktualisiert Anforderungs-Liste
 */
@Service
public class StaplerAnforderungBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(StaplerAnforderungBroadcaster.class);

    private final Executor executor = Executors.newSingleThreadExecutor();
    private final CopyOnWriteArrayList<Consumer<List<StaplerAnforderung>>> listeners = new CopyOnWriteArrayList<>();

    // Aktuelle offene Anforderungen (fuer spaetere Abfrage)
    private final CopyOnWriteArrayList<StaplerAnforderung> offeneAnforderungen = new CopyOnWriteArrayList<>();

    /**
     * Registriert einen Listener fuer Stapler-Anforderungen.
     *
     * @param listener Consumer der bei neuen Anforderungen aufgerufen wird
     * @return Registration zum Deregistrieren
     */
    public Registration register(Consumer<List<StaplerAnforderung>> listener) {
        listeners.add(listener);
        log.debug("Stapler-Listener registriert, aktuell {} Listener", listeners.size());

        // Sofort bestehende Anforderungen senden
        if (!offeneAnforderungen.isEmpty()) {
            executor.execute(() -> listener.accept(List.copyOf(offeneAnforderungen)));
        }

        return () -> {
            listeners.remove(listener);
            log.debug("Stapler-Listener entfernt, aktuell {} Listener", listeners.size());
        };
    }

    /**
     * Sendet neue Stapler-Anforderungen an alle Listener.
     *
     * @param anforderungen Liste der neuen Anforderungen
     */
    public void broadcast(List<StaplerAnforderung> anforderungen) {
        if (anforderungen == null || anforderungen.isEmpty()) {
            return;
        }

        log.info("=== STAPLER-ANFORDERUNGEN BROADCAST ===");
        log.info("Sende {} Anforderungen an {} Listener", anforderungen.size(), listeners.size());

        // Anforderungen zur Liste hinzufuegen
        offeneAnforderungen.addAll(anforderungen);

        // Log-Ausgabe fuer jede Anforderung
        for (StaplerAnforderung anf : anforderungen) {
            log.info("  >>> STAPLER: {} von {} nach {} (Abruf: {})",
                anf.getBarrenNo(), anf.getVonPlatz(), anf.getNachPlatz(), anf.getCalloffNo());
        }

        // Alle Listener benachrichtigen
        List<StaplerAnforderung> copy = List.copyOf(anforderungen);
        for (Consumer<List<StaplerAnforderung>> listener : listeners) {
            executor.execute(() -> {
                try {
                    listener.accept(copy);
                } catch (Exception e) {
                    log.error("Fehler bei Stapler-Broadcast Listener: {}", e.getMessage());
                }
            });
        }

        log.info("========================================");
    }

    /**
     * Markiert eine Anforderung als erledigt (Stapler hat Barren geholt).
     *
     * @param transportOrderId ID des Transport-Auftrags
     */
    public void markCompleted(Long transportOrderId) {
        offeneAnforderungen.removeIf(a -> transportOrderId.equals(a.getTransportOrderId()));
        log.info("Stapler-Anforderung {} als erledigt markiert, {} offen",
            transportOrderId, offeneAnforderungen.size());
    }

    /**
     * Gibt alle offenen Anforderungen zurueck.
     */
    public List<StaplerAnforderung> getOffeneAnforderungen() {
        return List.copyOf(offeneAnforderungen);
    }

    /**
     * Gibt die Anzahl offener Anforderungen zurueck.
     */
    public int getAnzahlOffen() {
        return offeneAnforderungen.size();
    }

    /**
     * Loescht alle offenen Anforderungen (z.B. bei System-Reset).
     */
    public void clearAll() {
        offeneAnforderungen.clear();
        log.info("Alle Stapler-Anforderungen geloescht");
    }

    /**
     * Functional interface fuer Listener-Deregistrierung.
     */
    @FunctionalInterface
    public interface Registration {
        void remove();
    }
}
