package com.example.common.kafka.observability;

import com.example.common.event.EventEnvelope;
import com.example.common.kafka.flow.KafkaEventRoutingContext;

/**
 * Observability contract for Kafka event operations.
 *
 * <p>Covers publish success/failure on the producer side and dispatch success/failure
 * on the consumer side. Consumer-side methods have no-op defaults because the Kafka
 * listener infrastructure is service-owned — common-kafka only provides optional
 * dispatcher helpers.
 */
public interface KafkaEventObserver {

    void logPublish(KafkaEventRoutingContext context, EventEnvelope<?> event);

    void logError(KafkaEventRoutingContext context, EventEnvelope<?> event, Throwable ex);

    /**
     * Called when a Kafka event is successfully dispatched to a handler.
     * No-op by default; override to add consumer-side publish observability.
     */
    default void logDispatch(KafkaEventRoutingContext context, EventEnvelope<?> event) {
        // no-op default — consumer path is service-owned
    }

    /**
     * Called when dispatch fails (handler throws).
     * No-op by default; override to add consumer-side error observability.
     */
    default void logDispatchError(KafkaEventRoutingContext context, EventEnvelope<?> event, Throwable ex) {
        // no-op default — consumer path is service-owned
    }
}
