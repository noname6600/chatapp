package com.chatweb.common.redis.publisher;

import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.event.EventMetadata;
import com.chatweb.common.event.validation.EventContractValidator;
import com.chatweb.common.redis.exception.RedisPubSubException;
import com.chatweb.common.redis.flow.RedisEventRoutingContext;
import com.chatweb.common.redis.observability.RedisPubSubObserver;
import com.chatweb.common.redis.serialization.RedisEventSerializer;
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
        if (eventEnvelope == null) {
            throw new IllegalArgumentException("Redis envelope must not be null");
        }
        RedisEventRoutingContext context = RedisEventRoutingContext.of(channel, eventEnvelope);
        EventMetadata metadata = eventEnvelope.metadata();

        // Stage: VALIDATE â€” fail fast on contract violations before any I/O
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

        // Stage: PUBLISH â€” serialize and send
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
