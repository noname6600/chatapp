package com.example.common.kafka.observability;

import com.example.common.event.EventEnvelope;
import com.example.common.kafka.flow.KafkaEventRoutingContext;
import lombok.extern.slf4j.Slf4j;

/**
 * SLF4J-backed implementation of {@link KafkaEventObserver}.
 *
 * <p>Covers both producer-side (publish/error) and consumer-side (dispatch/dispatchError)
 * observability. Null-safe: all fields are guarded before access.
 */
@Slf4j
public class Slf4jKafkaEventLogger implements KafkaEventObserver {

    private static final String SUCCESS = "SUCCESS";
    private static final String FAILURE = "FAILURE";

    @Override
    public void logPublish(KafkaEventRoutingContext context, EventEnvelope<?> event) {
        log.info("[KAFKA][{}] topic={} key={} eventId={} eventType={} correlationId={} sourceService={}",
                SUCCESS,
                context != null ? context.topic() : null,
                context != null ? context.key() : null,
                event != null && event.metadata() != null ? event.metadata().getEventId() : null,
                context != null ? context.eventType() : null,
                context != null ? context.correlationId() : null,
                context != null ? context.sourceService() : null);
    }

    @Override
    public void logError(KafkaEventRoutingContext context, EventEnvelope<?> event, Throwable ex) {
        log.error("[KAFKA][{}] topic={} key={} eventId={} eventType={} correlationId={} sourceService={}",
                FAILURE,
                context != null ? context.topic() : null,
                context != null ? context.key() : null,
                event != null && event.metadata() != null ? event.metadata().getEventId() : null,
                context != null ? context.eventType() : null,
                context != null ? context.correlationId() : null,
                context != null ? context.sourceService() : null,
                ex);
    }

    @Override
    public void logDispatch(KafkaEventRoutingContext context, EventEnvelope<?> event) {
        log.debug("[KAFKA][DISPATCH][{}] eventId={} eventType={} correlationId={}",
                SUCCESS,
                event != null && event.metadata() != null ? event.metadata().getEventId() : null,
                context != null ? context.eventType() : null,
                context != null ? context.correlationId() : null);
    }

    @Override
    public void logDispatchError(KafkaEventRoutingContext context, EventEnvelope<?> event, Throwable ex) {
        log.error("[KAFKA][DISPATCH][{}] eventId={} eventType={} correlationId={}",
                FAILURE,
                event != null && event.metadata() != null ? event.metadata().getEventId() : null,
                context != null ? context.eventType() : null,
                context != null ? context.correlationId() : null,
                ex);
    }
}
