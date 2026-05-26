package com.chatweb.common.redis.subscriber;

import com.chatweb.common.event.EventEnvelope;

/**
 * Canonical Redis callback contract for event-type-based dispatch.
 *
 * <p>{@link #handle(EventEnvelope)} is the dispatch method called by
 * {@link com.chatweb.common.redis.dispatcher.RedisEventDispatcher}.
 *
 * <p>Migration note: the previous {@code onEvent} method has been removed.
 * Service implementations that previously overrode {@code onEvent} must
 * override {@code handle} instead. The method signature is identical.
 */
public interface RedisEventHandler<T> {

    String eventType();

    /**
     * Returns whether this handler supports the given channel/envelope pair.
     *
     * <p>Default behavior keeps backward compatibility with eventType-based routing.
     */
    default boolean supports(String channel, EventEnvelope<?> envelope) {
        String incomingEventType = envelope == null || envelope.metadata() == null
                ? null
                : envelope.metadata().getEventType();
        String configuredEventType = eventType();
        return configuredEventType != null && configuredEventType.equals(incomingEventType);
    }

    /**
     * Handles an incoming Redis event.
     *
     * @param channel the Redis channel that delivered the message
     * @param envelope the incoming event envelope
     */
    void handle(String channel, EventEnvelope<T> envelope);
}
