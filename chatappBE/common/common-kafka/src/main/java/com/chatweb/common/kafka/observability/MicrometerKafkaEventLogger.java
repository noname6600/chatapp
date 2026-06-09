package com.chatweb.common.kafka.observability;

import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.kafka.flow.KafkaEventRoutingContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Micrometer-backed KafkaEventObserver.
 *
 * Exposes the following meters:
 *   kafka.publish.success   (counter)  tags: topic, event_type
 *   kafka.publish.error     (counter)  tags: topic, event_type
 *   kafka.dispatch.success  (counter)  tags: event_type
 *   kafka.dispatch.error    (counter)  tags: event_type
 */
@RequiredArgsConstructor
public class MicrometerKafkaEventLogger implements KafkaEventObserver {

    private final MeterRegistry registry;
    private final ConcurrentMap<String, Timer> dispatchTimers = new ConcurrentHashMap<>();

    @Override
    public void logPublish(KafkaEventRoutingContext context, EventEnvelope<?> event) {
        registry.counter("kafka.publish.success",
                "topic", tag(context.topic()),
                "event_type", tag(context.eventType())
        ).increment();
    }

    @Override
    public void logError(KafkaEventRoutingContext context, EventEnvelope<?> event, Throwable ex) {
        registry.counter("kafka.publish.error",
                "topic", tag(context.topic()),
                "event_type", tag(context.eventType()),
                "exception", ex != null ? ex.getClass().getSimpleName() : "unknown"
        ).increment();
    }

    @Override
    public void logDispatch(KafkaEventRoutingContext context, EventEnvelope<?> event) {
        registry.counter("kafka.dispatch.success",
                "event_type", tag(context.eventType())
        ).increment();
    }

    @Override
    public void logDispatchError(KafkaEventRoutingContext context, EventEnvelope<?> event, Throwable ex) {
        registry.counter("kafka.dispatch.error",
                "event_type", tag(context.eventType())
        ).increment();
    }

    private String tag(String value) {
        return value != null ? value : "unknown";
    }
}
