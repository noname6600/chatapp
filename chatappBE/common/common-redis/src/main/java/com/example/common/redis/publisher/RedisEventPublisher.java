package com.example.common.redis.publisher;

import com.example.common.event.EventEnvelope;

/**
 * Canonical Redis event publishing contract.
 */
public interface RedisEventPublisher {
    void publish(String channel, EventEnvelope<?> eventEnvelope);
}
