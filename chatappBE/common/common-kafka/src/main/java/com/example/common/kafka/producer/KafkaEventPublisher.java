package com.example.common.kafka.producer;

import com.example.common.event.EventEnvelope;

/**
 * Canonical Kafka event publishing contract.
 *
 * <p>This is the primary interface for emitting events to Kafka topics.
 */
public interface KafkaEventPublisher {
    void publish(String topic, String key, EventEnvelope<?> envelope);
}
