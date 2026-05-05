package com.example.common.event;

/**
 * Transport-independent registry mapping event type strings to payload classes.
 *
 * <p>Payload class resolution logic is owned by {@code common-events}.
 * The canonical pre-populated catalog is available via
 * {@link SharedEventCatalog#registerAll(EventPayloadRegistry)}.
 *
 * <p>Transport modules that own JSON deserialization (such as {@code common-redis}) may
 * extend this interface with transport-scoped aliases for injection-by-type compatibility.
 */
public interface EventPayloadRegistry {

    /**
     * Registers a payload class for the given event type.
     *
     * @param eventType    canonical event type string (e.g., {@code "chat.message.sent"})
     * @param payloadClass the class that represents the event payload
     * @throws IllegalStateException if the event type is already registered
     */
    void register(String eventType, Class<?> payloadClass);

    /**
     * Resolves the payload class for the given event type.
     *
     * @param eventType canonical event type string
     * @return the registered payload class, never null
     * @throws IllegalStateException if no payload class is registered for the event type
     */
    Class<?> resolvePayload(String eventType);

    /**
     * Returns {@code true} if a payload class is registered for the given event type.
     *
     * @param eventType canonical event type string
     * @return {@code true} if registered
     */
    boolean contains(String eventType);
}
