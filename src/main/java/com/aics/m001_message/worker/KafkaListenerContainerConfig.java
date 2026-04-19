package com.aics.m001_message.worker;

import com.aics.m001_message.dto.InboundEnvelope;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaListenerContainerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrap;

    @Bean
    public ConsumerFactory<String, InboundEnvelope> envelopeConsumerFactory() {
        Map<String, Object> p = new HashMap<>();
        p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        p.put(ConsumerConfig.GROUP_ID_CONFIG, "cs-bot-worker");
        p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        p.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        p.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);
        p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        p.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        p.put(JsonDeserializer.TRUSTED_PACKAGES, "com.aics.*");
        p.put(JsonDeserializer.VALUE_DEFAULT_TYPE, InboundEnvelope.class.getName());
        p.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaConsumerFactory<>(p);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, InboundEnvelope> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, InboundEnvelope> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(envelopeConsumerFactory());
        factory.setConcurrency(4);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }
}
