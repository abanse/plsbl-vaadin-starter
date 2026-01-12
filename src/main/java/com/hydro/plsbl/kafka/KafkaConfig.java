package com.hydro.plsbl.kafka;

import com.hydro.plsbl.service.SettingsService;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka-Konfiguration fuer PLSBL
 *
 * Konfiguriert Producer und Consumer fuer die Kommunikation
 * mit SAP und der Saege ueber Apache Kafka.
 */
@Configuration
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    private final SettingsService settingsService;

    public KafkaConfig(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    /**
     * Kafka Producer Factory
     */
    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, settingsService.getKafkaBootstrapServers());
        configProps.put(ProducerConfig.CLIENT_ID_CONFIG, settingsService.getKafkaClientId() + "-producer");
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        // Zuverlässige Zustellung
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);

        log.info("Kafka Producer konfiguriert fuer: {}", settingsService.getKafkaBootstrapServers());
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * Kafka Template fuer das Senden von Nachrichten
     */
    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    /**
     * Kafka Consumer Factory
     */
    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, settingsService.getKafkaBootstrapServers());
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, settingsService.getKafkaGroupId());
        configProps.put(ConsumerConfig.CLIENT_ID_CONFIG, settingsService.getKafkaClientId() + "-consumer");
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        // Auto-Commit deaktiviert fuer manuelle Kontrolle
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        log.info("Kafka Consumer konfiguriert fuer: {} (Group: {})",
            settingsService.getKafkaBootstrapServers(), settingsService.getKafkaGroupId());
        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    /**
     * Kafka Listener Container Factory
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        // Manuelle Acknowledge-Kontrolle
        factory.getContainerProperties().setAckMode(
            org.springframework.kafka.listener.ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }
}
