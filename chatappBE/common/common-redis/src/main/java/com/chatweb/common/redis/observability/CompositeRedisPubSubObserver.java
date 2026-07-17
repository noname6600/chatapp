package com.chatweb.common.redis.observability;

import com.chatweb.common.event.EventEnvelope;

/**
 * Delegates to both a metrics observer and a logging observer so Redis pub/sub events
 * produce both Micrometer counters and SLF4J log lines.
 */
public class CompositeRedisPubSubObserver implements RedisPubSubObserver {

    private final RedisPubSubObserver metrics;
    private final RedisPubSubObserver logs;

    public CompositeRedisPubSubObserver(RedisPubSubObserver metrics, RedisPubSubObserver logs) {
        this.metrics = metrics;
        this.logs = logs;
    }

    @Override
    public void logPublish(String channel, EventEnvelope<?> message) {
        metrics.logPublish(channel, message);
        logs.logPublish(channel, message);
    }

    @Override
    public void logReceive(String channel, EventEnvelope<?> message) {
        metrics.logReceive(channel, message);
        logs.logReceive(channel, message);
    }

    @Override
    public void logError(String channel, EventEnvelope<?> message, Throwable ex) {
        metrics.logError(channel, message, ex);
        logs.logError(channel, message, ex);
    }

    @Override
    public void logDeserializeError(String channel, String rawPayload, Exception ex) {
        metrics.logDeserializeError(channel, rawPayload, ex);
        logs.logDeserializeError(channel, rawPayload, ex);
    }
}
