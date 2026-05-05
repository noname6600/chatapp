package com.example.common.redis.dispatcher;

import com.example.common.event.EventEnvelope;
import com.example.common.redis.subscriber.RedisEventHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Canonical Redis event dispatcher.
 */
@Slf4j
public class RedisEventDispatcher {

    private final Map<String, RedisEventHandler<?>> eventHandlerMap;

    public RedisEventDispatcher(List<? extends RedisEventHandler<?>> handlers) {
        this.eventHandlerMap = handlers.stream()
                .collect(Collectors.toMap(
                        RedisEventHandler::eventType,
                        h -> h,
                        (a, b) -> {
                            throw new IllegalStateException(
                                    "Duplicate Redis handler for eventType=" + a.eventType()
                            );
                        }
                ));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void dispatch(EventEnvelope<?> envelope) {
        String eventType = envelope == null || envelope.metadata() == null
                ? null
                : envelope.metadata().getEventType();
        RedisEventHandler handler = eventHandlerMap.get(eventType);

        if (handler == null) {
            log.warn("No handler for eventType={}", eventType);
            return;
        }

        handler.handle(envelope);
    }
}

