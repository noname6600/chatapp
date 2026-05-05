package com.example.common.redis.flow;

import com.example.common.event.EventEnvelope;
import com.example.common.event.EventMetadata;

public record RedisEventRoutingContext(
        String channel,
        String eventType,
        String correlationId,
        String sourceService
) {
    public static RedisEventRoutingContext of(String channel, EventEnvelope<?> envelope) {
        EventMetadata meta = envelope != null ? envelope.metadata() : null;
        return new RedisEventRoutingContext(
                channel,
                meta != null ? meta.getEventType() : null,
                meta != null ? meta.getCorrelationId() : null,
                meta != null ? meta.getSourceService() : null
        );
    }
}

