package com.hydro.plsbl.service;

import com.vaadin.flow.shared.Registration;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Broadcaster für Beladungs-Status Updates.
 * Ermöglicht die Synchronisation zwischen verschiedenen Views (z.B. BeladungView und LagerView).
 */
@Service
public class BeladungBroadcaster {

    private final Executor executor = Executors.newSingleThreadExecutor();
    private final LinkedList<Consumer<BeladungEvent>> listeners = new LinkedList<>();

    /**
     * Registriert einen Listener für Beladungs-Events
     */
    public synchronized Registration register(Consumer<BeladungEvent> listener) {
        listeners.add(listener);
        return () -> {
            synchronized (BeladungBroadcaster.this) {
                listeners.remove(listener);
            }
        };
    }

    /**
     * Sendet ein Event an alle registrierten Listener
     */
    public synchronized void broadcast(BeladungEvent event) {
        for (Consumer<BeladungEvent> listener : listeners) {
            executor.execute(() -> listener.accept(event));
        }
    }

    /**
     * Convenience-Methode: Broadcast eines Status-Updates
     */
    public void broadcastStatusUpdate(int geladeneCount, int totalCount, boolean isLoading) {
        broadcast(new BeladungEvent(BeladungEventType.STATUS_UPDATE, geladeneCount, totalCount, isLoading));
    }

    /**
     * Convenience-Methode: Broadcast dass Beladung gestartet wurde
     */
    public void broadcastBeladungStarted(int totalCount) {
        broadcast(new BeladungEvent(BeladungEventType.BELADUNG_STARTED, 0, totalCount, true));
    }

    /**
     * Convenience-Methode: Broadcast dass Beladung beendet wurde
     */
    public void broadcastBeladungEnded(int geladeneCount) {
        broadcast(new BeladungEvent(BeladungEventType.BELADUNG_ENDED, geladeneCount, geladeneCount, false));
    }

    /**
     * Convenience-Methode: Broadcast dass Beladung beendet wurde MIT Lieferschein
     */
    public void broadcastBeladungEndedWithShipment(int geladeneCount, Long shipmentId, String shipmentNumber) {
        BeladungEvent event = new BeladungEvent(BeladungEventType.BELADUNG_ENDED, geladeneCount, geladeneCount, false);
        event.setShipmentId(shipmentId);
        event.setShipmentNumber(shipmentNumber);
        broadcast(event);
    }

    /**
     * Event-Typen für Beladungs-Broadcasts
     */
    public enum BeladungEventType {
        STATUS_UPDATE,
        BELADUNG_STARTED,
        BELADUNG_ENDED
    }

    /**
     * Event-Daten für Beladungs-Broadcasts
     */
    public static class BeladungEvent {
        private final BeladungEventType type;
        private final int geladeneCount;
        private final int totalCount;
        private final boolean isLoading;
        private Long shipmentId;
        private String shipmentNumber;

        public BeladungEvent(BeladungEventType type, int geladeneCount, int totalCount, boolean isLoading) {
            this.type = type;
            this.geladeneCount = geladeneCount;
            this.totalCount = totalCount;
            this.isLoading = isLoading;
        }

        public BeladungEventType getType() {
            return type;
        }

        public int getGeladeneCount() {
            return geladeneCount;
        }

        public int getTotalCount() {
            return totalCount;
        }

        public boolean isLoading() {
            return isLoading;
        }

        public Long getShipmentId() {
            return shipmentId;
        }

        public void setShipmentId(Long shipmentId) {
            this.shipmentId = shipmentId;
        }

        public String getShipmentNumber() {
            return shipmentNumber;
        }

        public void setShipmentNumber(String shipmentNumber) {
            this.shipmentNumber = shipmentNumber;
        }

        public boolean hasShipment() {
            return shipmentId != null;
        }
    }
}
