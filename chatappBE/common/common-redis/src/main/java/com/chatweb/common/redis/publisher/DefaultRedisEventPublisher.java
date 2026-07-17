package com.chatweb.common.redis.publisher;

import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.event.EventMetadata;
import com.chatweb.common.event.validation.EventContractValidator;
import com.chatweb.common.redis.exception.RedisPubSubException;
import com.chatweb.common.redis.flow.RedisEventRoutingContext;
import com.chatweb.common.redis.observability.RedisPubSubObserver;
import com.chatweb.common.redis.serialization.RedisEventSerializer;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.HashMap;

@RequiredArgsConstructor
public class DefaultRedisEventPublisher implements RedisEventPublisher {

    private final StringRedisTemplate redisTemplate;
    private final RedisEventSerializer serializer;
    private final RedisPubSubObserver logger;

    @Override
    public void publish(String channel, EventEnvelope<?> eventEnvelope) {
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("Redis channel must not be null or blank");
        }
        if (eventEnvelope == null) {
            throw new IllegalArgumentException("Redis envelope must not be null");
        }
        // Inject current W3C traceparent into the envelope so the subscriber can
        // restore the trace context and continue the same trace across the channel.
        eventEnvelope = injectTraceparent(eventEnvelope);

        RedisEventRoutingContext context = RedisEventRoutingContext.of(channel, eventEnvelope);
        EventMetadata metadata = eventEnvelope.metadata();

        // Stage: VALIDATE â€" fail fast on contract violations before any I/O
        try {
            EventContractValidator.validateEventNameOrThrow(metadata.getEventType());
            EventContractValidator.validateIdentityOrThrow(
                metadata.getEventId(),
                metadata.getCorrelationId(),
                metadata.getSourceService(),
                metadata.getCreatedAt());
        } catch (IllegalArgumentException ex) {
            logger.logError(context, eventEnvelope, ex);
            throw new RedisPubSubException(channel, "Failed at Redis lifecycle stage VALIDATE", ex);
        }

        // Stage: PUBLISH â€" serialize and send
        try {
            String payload = serializer.serialize(eventEnvelope);
            redisTemplate.convertAndSend(channel, payload);
            logger.logPublish(context, eventEnvelope);
        } catch (Exception ex) {
            logger.logError(context, eventEnvelope, ex);
            throw new RedisPubSubException(channel, "Failed at Redis lifecycle stage PUBLISH", ex);
        }
    }

    private <T> EventEnvelope<T> injectTraceparent(EventEnvelope<T> envelope) {
        var carrier = new HashMap<String, String>();
        GlobalOpenTelemetry.getPropagators().getTextMapPropagator()
                .inject(Context.current(), carrier, HashMap::put);
        String traceparent = carrier.get("traceparent");
        if (traceparent == null) {
            return envelope;
        }
        EventMetadata original = envelope.metadata();
        EventMetadata enriched = new EventMetadata(
                original.getEventId(), original.getEventType(), original.getSourceService(),
                original.getCreatedAt(), original.getCorrelationId(), traceparent);
        return new EventEnvelope<>(enriched, envelope.payload());
    }
}
