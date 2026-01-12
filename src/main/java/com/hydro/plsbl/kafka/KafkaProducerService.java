package com.hydro.plsbl.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hydro.plsbl.kafka.dto.*;
import com.hydro.plsbl.service.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Service fuer das Senden von Kafka-Nachrichten
 *
 * Sendet Ereignisse an SAP und die Saege:
 * - Barren-Ereignisse (abgeholt, umgelagert, modifiziert)
 * - Lieferungs-Abschluss
 * - Rueckmeldungen an die Saege
 */
@Service
public class KafkaProducerService {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducerService.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final SettingsService settingsService;
    private final ObjectMapper objectMapper;

    public KafkaProducerService(KafkaTemplate<String, String> kafkaTemplate,
                                 SettingsService settingsService) {
        this.kafkaTemplate = kafkaTemplate;
        this.settingsService = settingsService;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Prueft ob Kafka aktiviert ist
     */
    public boolean isEnabled() {
        return settingsService.isKafkaEnabled();
    }

    /**
     * Sendet eine Barren-Abholung Nachricht
     */
    public void sendIngotPickedUp(KafkaIngotEventMessage message) {
        if (!isEnabled()) {
            log.debug("Kafka deaktiviert - Nachricht nicht gesendet: {}", message);
            return;
        }
        message.setEventType(KafkaIngotEventMessage.EventType.PICKED_UP);
        sendMessage(settingsService.getKafkaTopicIngotPickedUp(), message.getIngotNumber(), message);
    }

    /**
     * Sendet eine Barren-Umlagerung Nachricht
     */
    public void sendIngotMoved(KafkaIngotEventMessage message) {
        if (!isEnabled()) {
            log.debug("Kafka deaktiviert - Nachricht nicht gesendet: {}", message);
            return;
        }
        message.setEventType(KafkaIngotEventMessage.EventType.MOVED);
        sendMessage(settingsService.getKafkaTopicIngotMoved(), message.getIngotNumber(), message);
    }

    /**
     * Sendet eine Barren-Modifikation Nachricht
     */
    public void sendIngotModified(KafkaIngotEventMessage message) {
        if (!isEnabled()) {
            log.debug("Kafka deaktiviert - Nachricht nicht gesendet: {}", message);
            return;
        }
        message.setEventType(KafkaIngotEventMessage.EventType.MODIFIED);
        sendMessage(settingsService.getKafkaTopicIngotModified(), message.getIngotNumber(), message);
    }

    /**
     * Sendet eine Lieferung-Abgeschlossen Nachricht
     */
    public void sendShipmentCompleted(KafkaShipmentMessage message) {
        if (!isEnabled()) {
            log.debug("Kafka deaktiviert - Nachricht nicht gesendet: {}", message);
            return;
        }
        sendMessage(settingsService.getKafkaTopicShipmentCompleted(), message.getShipmentNumber(), message);
    }

    /**
     * Sendet eine Rueckmeldung an die Saege
     */
    public void sendSawFeedback(KafkaSawFeedbackMessage message) {
        if (!isEnabled()) {
            log.debug("Kafka deaktiviert - Nachricht nicht gesendet: {}", message);
            return;
        }
        String key = message.getIngotNumber() != null ? message.getIngotNumber() : "feedback";
        sendMessage(settingsService.getKafkaTopicSawFeedback(), key, message);
    }

    /**
     * Generische Methode zum Senden einer Nachricht
     */
    private <T> void sendMessage(String topic, String key, T message) {
        try {
            String json = objectMapper.writeValueAsString(message);

            CompletableFuture<SendResult<String, String>> future =
                kafkaTemplate.send(topic, key, json);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Kafka Nachricht gesendet: topic={}, key={}, offset={}",
                        topic, key, result.getRecordMetadata().offset());
                } else {
                    log.error("Fehler beim Senden der Kafka Nachricht: topic={}, key={}, error={}",
                        topic, key, ex.getMessage());
                }
            });

        } catch (JsonProcessingException e) {
            log.error("Fehler beim Serialisieren der Kafka Nachricht: {}", e.getMessage());
        }
    }

    /**
     * Sendet eine Testnachricht um die Verbindung zu pruefen
     */
    public boolean sendTestMessage() {
        if (!isEnabled()) {
            log.warn("Kafka ist deaktiviert");
            return false;
        }

        try {
            String testTopic = settingsService.getKafkaTopicSawFeedback();
            String testMessage = "{\"test\": true, \"timestamp\": \"" + java.time.LocalDateTime.now() + "\"}";

            CompletableFuture<SendResult<String, String>> future =
                kafkaTemplate.send(testTopic, "test", testMessage);

            SendResult<String, String> result = future.get(10, java.util.concurrent.TimeUnit.SECONDS);
            log.info("Kafka Test erfolgreich: topic={}, offset={}",
                testTopic, result.getRecordMetadata().offset());
            return true;

        } catch (Exception e) {
            log.error("Kafka Test fehlgeschlagen: {}", e.getMessage());
            return false;
        }
    }
}
