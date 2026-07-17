package com.chatweb.common.kafka.observability;

import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.kafka.flow.KafkaEventRoutingContext;

/**
 * Delegates to both a metrics observer and a logging observer so Kafka events
 * produce both Micrometer counters and SLF4J log lines.
 */
public class CompositeKafkaEventObserver implements KafkaEventObserver {

    private final KafkaEventObserver metrics;
    private final KafkaEventObserver logs;

    public CompositeKafkaEventObserver(KafkaEventObserver metrics, KafkaEventObserver logs) {
        this.metrics = metrics;
        this.logs = logs;
    }

    @Override
    public void logPublish(KafkaEventRoutingContext context, EventEnvelope<?> event) {
        metrics.logPublish(context, event);
        logs.logPublish(context, event);
    }

    @Override
    public void logError(KafkaEventRoutingContext context, EventEnvelope<?> event, Throwable ex) {
        metrics.logError(context, event, ex);
        logs.logError(context, event, ex);
    }

    @Override
    public void logDispatch(KafkaEventRoutingContext context, EventEnvelope<?> event) {
        metrics.logDispatch(context, event);
        logs.logDispatch(context, event);
    }

    @Override
    public void logDispatchError(KafkaEventRoutingContext context, EventEnvelope<?> event, Throwable ex) {
        metrics.logDispatchError(context, event, ex);
        logs.logDispatchError(context, event, ex);
    }
}
