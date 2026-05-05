package com.example.common.redis.publisher;

import com.example.common.event.EventEnvelope;
import com.example.common.event.EventMetadata;
import com.example.common.event.validation.EventContractValidator;
import com.example.common.redis.exception.RedisPubSubException;
import com.example.common.redis.flow.RedisEventRoutingContext;
import com.example.common.redis.observability.RedisPubSubObserver;
import com.example.common.redis.serialization.RedisEventSerializer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;

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
        RedisEventRoutingContext context = RedisEventRoutingContext.of(channel, eventEnvelope);
        EventMetadata metadata = eventEnvelope == null ? null : eventEnvelope.metadata();

        // Stage: VALIDATE — fail fast on contract violations before any I/O
        try {
            EventContractValidator.validateEventNameOrThrow(metadata == null ? null : metadata.getEventType());
            EventContractValidator.validateIdentityOrThrow(
                metadata == null ? null : metadata.getEventId(),
                metadata == null ? null : metadata.getCorrelationId(),
                metadata == null ? null : metadata.getSourceService(),
                metadata == null ? null : metadata.getCreatedAt());
        } catch (IllegalArgumentException ex) {
            logger.logError(context, eventEnvelope, ex);
            throw new RedisPubSubException(channel, "Failed at Redis lifecycle stage VALIDATE", ex);
        }

        // Stage: PUBLISH — serialize and send
        try {
            String payload = serializer.serialize(eventEnvelope);
            redisTemplate.convertAndSend(channel, payload);
            logger.logPublish(context, eventEnvelope);
        } catch (Exception ex) {
            logger.logError(context, eventEnvelope, ex);
            throw new RedisPubSubException(channel, "Failed at Redis lifecycle stage PUBLISH", ex);
        }
    }
}
