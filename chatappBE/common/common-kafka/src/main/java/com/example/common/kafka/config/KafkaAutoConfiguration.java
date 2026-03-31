package com.example.common.kafka.config;

import com.example.common.kafka.api.KafkaEventPublisher;
import com.example.common.kafka.core.DefaultKafkaEventPublisher;
import com.example.common.kafka.observability.KafkaEventLogger;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

@AutoConfiguration
public class KafkaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public KafkaEventLogger kafkaEventLogger() {
        return new KafkaEventLogger();
    }

    @Bean
    @ConditionalOnMissingBean
    public KafkaEventPublisher kafkaEventPublisher(
            KafkaTemplate<String, Object> kafkaTemplate,
            KafkaEventLogger logger
    ) {
        return new DefaultKafkaEventPublisher(kafkaTemplate, logger);
    }
}

