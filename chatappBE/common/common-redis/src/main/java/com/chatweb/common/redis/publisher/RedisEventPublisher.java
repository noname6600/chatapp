package com.chatweb.common.redis.publisher;

import com.chatweb.common.event.EventEnvelope;

/**
 * Canonical Redis event publishing contract.
 */
public interface RedisEventPublisher {
    void publish(String channel, EventEnvelope<?> eventEnvelope);
}
