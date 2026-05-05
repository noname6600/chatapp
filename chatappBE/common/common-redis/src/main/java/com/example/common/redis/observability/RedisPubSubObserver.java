package com.example.common.redis.observability;

import com.example.common.event.EventEnvelope;
import com.example.common.redis.flow.RedisEventRoutingContext;

/**
 * Observability contract for Redis pub/sub events.
 *
 * <p>Covers publish success/failure, receive, dispatch failure, and deserialization failure.
 * All default methods guard against null context to avoid NPE in implementations.
 */
public interface RedisPubSubObserver {

    void logPublish(String channel, EventEnvelope<?> message);

    default void logPublish(RedisEventRoutingContext context, EventEnvelope<?> message) {
        if (context != null) {
            logPublish(context.channel(), message);
        }
    }

    void logReceive(String channel, EventEnvelope<?> message);

    default void logReceive(RedisEventRoutingContext context, EventEnvelope<?> message) {
        if (context != null) {
            logReceive(context.channel(), message);
        }
    }

    void logError(String channel, EventEnvelope<?> message, Throwable ex);

    default void logError(RedisEventRoutingContext context, EventEnvelope<?> message, Throwable ex) {
        if (context != null) {
            logError(context.channel(), message, ex);
        }
    }

    void logDeserializeError(String channel, String rawPayload, Exception ex);
}
