package com.chatweb.common.redis.dispatcher;

import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.redis.flow.RedisEventRoutingContext;
import com.chatweb.common.redis.observability.RedisPubSubObserver;
import com.chatweb.common.redis.subscriber.RedisEventHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Canonical Redis event dispatcher.
 */
@Slf4j
public class RedisEventDispatcher {

    private final List<? extends RedisEventHandler<?>> handlers;
    private final RedisPubSubObserver observer;

    public RedisEventDispatcher(List<? extends RedisEventHandler<?>> handlers, RedisPubSubObserver observer) {
        this.handlers = handlers;
        this.observer = observer;
    }

    /**
     * Convenience constructor for services that do not need dispatcher-level observability.
     */
    public RedisEventDispatcher(List<? extends RedisEventHandler<?>> handlers) {
        this(handlers, null);
    }

    // Safe: the registry guarantees payload class alignment at registration time via SharedEventCatalog.
    // A type mismatch would only occur if a handler is registered for the wrong event type, which
    // is caught at startup by the duplicate-registration check in DefaultEventPayloadRegistry.
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void dispatch(EventEnvelope<?> envelope) {
        dispatch(null, envelope);
    }

    // Safe: the registry guarantees payload class alignment at registration time via SharedEventCatalog.
    // A type mismatch would only occur if a handler is registered for the wrong event type.
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void dispatch(String channel, EventEnvelope<?> envelope) {
        String eventType = envelope == null || envelope.metadata() == null
                ? null
                : envelope.metadata().getEventType();

        RedisEventHandler handler = handlers.stream()
                .filter(h -> h.supports(channel, envelope))
                .findFirst()
                .orElse(null);

        if (handler == null) {
            log.warn("No handler for channel={} eventType={}", channel, eventType);
            return;
        }

        RedisEventRoutingContext context = RedisEventRoutingContext.of(channel, envelope);
        try {
            handler.handle(channel, envelope);
        } catch (Exception ex) {
            if (observer != null) {
                observer.logError(context, envelope, ex);
            }
            throw ex;
        }
    }
}

