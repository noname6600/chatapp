package com.example.common.event;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Default thread-safe implementation of {@link EventPayloadRegistry}.
 *
 * <p>Backed by a {@link ConcurrentHashMap}. Duplicate registration is rejected
 * at startup to catch misconfigured services early.
 */
public class DefaultEventPayloadRegistry implements EventPayloadRegistry {

    private final ConcurrentHashMap<String, Class<?>> registry = new ConcurrentHashMap<>();

    @Override
    public void register(String eventType, Class<?> payloadClass) {
        Class<?> existing = registry.putIfAbsent(eventType, payloadClass);
        if (existing != null && !existing.equals(payloadClass)) {
            throw new IllegalStateException(
                    "Conflicting event type registration: eventType=" + eventType
                    + " is already mapped to " + existing.getName()
                    + ", cannot re-register with " + payloadClass.getName()
            );
        }
        // if existing == payloadClass: idempotent re-registration, silently accepted
    }

    @Override
    public Class<?> resolvePayload(String eventType) {
        Class<?> clazz = registry.get(eventType);
        if (clazz == null) {
            throw new IllegalStateException(
                    "Unknown event type: eventType=" + eventType
            );
        }
        return clazz;
    }

    @Override
    public boolean contains(String eventType) {
        return registry.containsKey(eventType);
    }
}
