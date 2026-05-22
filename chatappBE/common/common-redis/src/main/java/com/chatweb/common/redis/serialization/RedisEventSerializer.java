package com.chatweb.common.redis.serialization;

import com.chatweb.common.event.EventEnvelope;

/**
 * Canonical Redis event serializer contract.
 */
public interface RedisEventSerializer {

    String serialize(EventEnvelope<?> envelope);

    EventEnvelope<?> deserialize(String payload);
}

