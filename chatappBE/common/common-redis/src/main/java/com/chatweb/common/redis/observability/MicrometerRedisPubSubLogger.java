package com.chatweb.common.redis.observability;

import com.chatweb.common.event.EventEnvelope;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;

/**
 * Micrometer-backed RedisPubSubObserver.
 *
 * Exposes the following meters:
 *   redis.publish.success   (counter)  tags: channel
 *   redis.receive           (counter)  tags: channel
 *   redis.error             (counter)  tags: channel
 *   redis.deserialize.error (counter)  tags: channel
 */
@RequiredArgsConstructor
public class MicrometerRedisPubSubLogger implements RedisPubSubObserver {

    private final MeterRegistry registry;

    @Override
    public void logPublish(String channel, EventEnvelope<?> message) {
        registry.counter("redis.publish.success", "channel", tag(channel)).increment();
    }

    @Override
    public void logReceive(String channel, EventEnvelope<?> message) {
        registry.counter("redis.receive", "channel", tag(channel)).increment();
    }

    @Override
    public void logError(String channel, EventEnvelope<?> message, Throwable ex) {
        registry.counter("redis.error", "channel", tag(channel)).increment();
    }

    @Override
    public void logDeserializeError(String channel, String rawPayload, Exception ex) {
        registry.counter("redis.deserialize.error", "channel", tag(channel)).increment();
    }

    private String tag(String value) {
        return value != null ? value : "unknown";
    }
}
