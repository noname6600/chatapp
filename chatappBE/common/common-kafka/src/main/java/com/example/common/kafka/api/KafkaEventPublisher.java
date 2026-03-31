package com.example.common.kafka.api;

public interface KafkaEventPublisher {
    void publish(String topic, String key, KafkaEvent event);
}
