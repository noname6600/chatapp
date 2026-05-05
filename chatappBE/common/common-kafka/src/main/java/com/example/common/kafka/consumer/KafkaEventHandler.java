package com.example.common.kafka.consumer;

import com.example.common.event.EventEnvelope;

/**
 * Canonical Kafka event handler contract.
 *
 * <p>Provides per-event-type handling, symmetric with the Redis
 * {@code RedisEventHandler} pattern. Implementors declare which
 * {@code eventType} they handle and receive strongly-typed events.
 *
 * <p>Service-level consumers can implement this interface and register
 * with a {@link KafkaEventDispatcher} for dispatch-by-type routing,
 * or use it standalone inside an existing {@code @KafkaListener} method.
 *
 * @param <T> the payload type carried by the handled event
 */
public interface KafkaEventHandler<T> {

    /**
     * The canonical event type string this handler processes.
     *
     * <p>Must follow the dot-separated lowercase format validated by
     * {@code EventContractValidator} (e.g., {@code "chat.message.sent"}).
     *
     * @return the event type, never null
     */
    String eventType();

    /**
     * Handles an incoming Kafka event.
     *
     * @param event the incoming event, never null
     */
    void handle(EventEnvelope<T> event);
}
