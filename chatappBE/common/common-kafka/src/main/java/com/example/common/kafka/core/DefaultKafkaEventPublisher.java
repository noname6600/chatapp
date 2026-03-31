package com.example.common.kafka.core;

import com.example.common.kafka.api.KafkaEvent;
import com.example.common.kafka.api.KafkaEventPublisher;
import com.example.common.kafka.exception.KafkaPubSubException;
import com.example.common.kafka.observability.KafkaEventLogger;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;

@RequiredArgsConstructor
public class DefaultKafkaEventPublisher implements KafkaEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaEventLogger logger;

    @Override
    public void publish(String topic, String key, KafkaEvent event) {

        kafkaTemplate.send(topic, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        logger.logError(topic, key, event, ex);
                    } else {
                        logger.logPublish(topic, key, event);
                    }
                });
    }
}

