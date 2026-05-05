package com.example.common.redis.subscriber;

import com.example.common.event.EventEnvelope;

/**
 * Canonical Redis callback contract for event-type-based dispatch.
 *
 * <p>{@link #handle(EventEnvelope)} is the dispatch method called by
 * {@link com.example.common.redis.dispatcher.RedisEventDispatcher}.
 *
 * <p>Migration note: the previous {@code onEvent} method has been removed.
 * Service implementations that previously overrode {@code onEvent} must
 * override {@code handle} instead. The method signature is identical.
 */
public interface RedisEventHandler<T> {

    String eventType();

    /**
     * Handles an incoming Redis event.
     *
     * @param envelope the incoming event envelope
     */
    void handle(EventEnvelope<T> envelope);
}
