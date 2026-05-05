package com.example.common.redis.serialization;

import com.example.common.event.EventEnvelope;

/**
 * Canonical Redis event serializer contract.
 */
public interface RedisEventSerializer {

    String serialize(EventEnvelope<?> envelope);

    EventEnvelope<?> deserialize(String payload);
}

