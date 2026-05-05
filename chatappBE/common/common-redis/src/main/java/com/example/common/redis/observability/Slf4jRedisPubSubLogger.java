package com.example.common.redis.observability;

import com.example.common.event.EventEnvelope;
import com.example.common.event.EventMetadata;
import lombok.extern.slf4j.Slf4j;

/**
 * SLF4J-backed implementation of {@link RedisPubSubObserver}.
 *
 * <p>Logs structured metadata fields only. Raw payload content is never
 * included in log output to avoid leaking user data.
 */
@Slf4j
public class Slf4jRedisPubSubLogger implements RedisPubSubObserver {

    @Override
    public void logPublish(String channel, EventEnvelope<?> envelope) {
        EventMetadata meta = envelope != null ? envelope.metadata() : null;
        log.info(
                "[REDIS][SUCCESS] channel={} eventType={} correlationId={} eventId={} sourceService={}",
                channel,
                meta != null ? meta.getEventType() : null,
                meta != null ? meta.getCorrelationId() : null,
                meta != null ? meta.getEventId() : null,
                meta != null ? meta.getSourceService() : null
        );
    }

    @Override
    public void logReceive(String channel, EventEnvelope<?> envelope) {
        EventMetadata meta = envelope != null ? envelope.metadata() : null;
        log.info(
                "[REDIS][DISPATCH] channel={} eventType={} correlationId={} eventId={} sourceService={}",
                channel,
                meta != null ? meta.getEventType() : null,
                meta != null ? meta.getCorrelationId() : null,
                meta != null ? meta.getEventId() : null,
                meta != null ? meta.getSourceService() : null
        );
    }

    @Override
    public void logError(String channel, EventEnvelope<?> envelope, Throwable ex) {
        EventMetadata meta = envelope != null ? envelope.metadata() : null;
        log.error(
                "[REDIS][FAILURE] channel={} eventType={} correlationId={} eventId={}",
                channel,
                meta != null ? meta.getEventType() : null,
                meta != null ? meta.getCorrelationId() : null,
                meta != null ? meta.getEventId() : null,
                ex
        );
    }

    @Override
    public void logDeserializeError(String channel, String rawPayload, Exception ex) {
        log.error(
                "[REDIS][DESERIALIZE_FAIL] channel={} payloadLength={}",
                channel,
                rawPayload != null ? rawPayload.length() : 0,
                ex
        );
    }
}
