package com.hydro.plsbl.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hydro.plsbl.kafka.dto.*;
import com.hydro.plsbl.service.SettingsService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

/**
 * Service fuer das Empfangen von Kafka-Nachrichten
 *
 * Empfaengt Nachrichten von SAP und der Saege:
 * - Abruf-Telegramme (Calloffs)
 * - Abholauftraege (Pickup Orders)
 * - Produkt-Einschraenkungen
 */
@Service
public class KafkaConsumerService {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerService.class);

    private final SettingsService settingsService;
    private final ObjectMapper objectMapper;

    // Event-Handler (werden von anderen Services gesetzt)
    private CalloffHandler calloffHandler;
    private PickupOrderHandler pickupOrderHandler;
    private ProductRestrictionHandler productRestrictionHandler;

    public KafkaConsumerService(SettingsService settingsService) {
        this.settingsService = settingsService;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    // === Handler Interfaces ===

    @FunctionalInterface
    public interface CalloffHandler {
        void handle(KafkaCalloffMessage message);
    }

    @FunctionalInterface
    public interface PickupOrderHandler {
        void handle(KafkaPickupOrderMessage message);
    }

    @FunctionalInterface
    public interface ProductRestrictionHandler {
        void handle(String productNumber, boolean restricted);
    }

    // === Handler Setter ===

    public void setCalloffHandler(CalloffHandler handler) {
        this.calloffHandler = handler;
    }

    public void setPickupOrderHandler(PickupOrderHandler handler) {
        this.pickupOrderHandler = handler;
    }

    public void setProductRestrictionHandler(ProductRestrictionHandler handler) {
        this.productRestrictionHandler = handler;
    }

    // === Kafka Listeners ===

    /**
     * Listener fuer Abruf-Telegramme (Calloffs) von SAP
     */
    @KafkaListener(
        topics = "#{@settingsService.kafkaTopicCalloff}",
        groupId = "#{@settingsService.kafkaGroupId}",
        autoStartup = "#{@settingsService.kafkaEnabled}"
    )
    public void onCalloff(ConsumerRecord<String, String> record, Acknowledgment ack) {
        log.info("Kafka Calloff empfangen: key={}, partition={}, offset={}",
            record.key(), record.partition(), record.offset());

        try {
            KafkaCalloffMessage message = objectMapper.readValue(record.value(), KafkaCalloffMessage.class);
            log.debug("Calloff deserialisiert: {}", message);

            if (calloffHandler != null) {
                calloffHandler.handle(message);
                log.info("Calloff verarbeitet: {}", message.getCalloffNumber());
            } else {
                log.warn("Kein Calloff-Handler registriert!");
            }

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Fehler beim Verarbeiten des Calloffs: {}", e.getMessage(), e);
            // Nachricht wird nicht acknowledged - wird erneut zugestellt
        }
    }

    /**
     * Listener fuer Abholauftraege von der Saege
     */
    @KafkaListener(
        topics = "#{@settingsService.kafkaTopicPickupOrder}",
        groupId = "#{@settingsService.kafkaGroupId}",
        autoStartup = "#{@settingsService.kafkaEnabled}"
    )
    public void onPickupOrder(ConsumerRecord<String, String> record, Acknowledgment ack) {
        log.info("Kafka Pickup-Order empfangen: key={}, partition={}, offset={}",
            record.key(), record.partition(), record.offset());

        try {
            KafkaPickupOrderMessage message = objectMapper.readValue(record.value(), KafkaPickupOrderMessage.class);
            log.debug("Pickup-Order deserialisiert: {}", message);

            if (pickupOrderHandler != null) {
                pickupOrderHandler.handle(message);
                log.info("Pickup-Order verarbeitet: Barren {}", message.getIngotNumber());
            } else {
                log.warn("Kein Pickup-Order-Handler registriert!");
            }

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Fehler beim Verarbeiten des Pickup-Orders: {}", e.getMessage(), e);
        }
    }

    /**
     * Listener fuer Produkt-Einschraenkungen
     */
    @KafkaListener(
        topics = "#{@settingsService.kafkaTopicProductRestriction}",
        groupId = "#{@settingsService.kafkaGroupId}",
        autoStartup = "#{@settingsService.kafkaEnabled}"
    )
    public void onProductRestriction(ConsumerRecord<String, String> record, Acknowledgment ack) {
        log.info("Kafka Product-Restriction empfangen: key={}", record.key());

        try {
            // Einfaches Format: Key = Produkt-Nummer, Value = "true"/"false"
            String productNumber = record.key();
            boolean restricted = Boolean.parseBoolean(record.value());

            if (productRestrictionHandler != null) {
                productRestrictionHandler.handle(productNumber, restricted);
                log.info("Product-Restriction verarbeitet: {} = {}", productNumber, restricted);
            } else {
                log.warn("Kein Product-Restriction-Handler registriert!");
            }

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Fehler beim Verarbeiten der Product-Restriction: {}", e.getMessage(), e);
        }
    }

    /**
     * Prueft ob Kafka aktiviert ist
     */
    public boolean isEnabled() {
        return settingsService.isKafkaEnabled();
    }
}
