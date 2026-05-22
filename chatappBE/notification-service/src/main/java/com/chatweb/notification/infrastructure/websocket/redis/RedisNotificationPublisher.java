package com.chatweb.notification.infrastructure.websocket.redis;

import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.event.EventMetadata;
import com.chatweb.common.redis.channel.RedisChannels;
import com.chatweb.common.redis.publisher.RedisEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisNotificationPublisher {

    private final RedisEventPublisher redisEventPublisher;

    public void publish(UUID userId, String eventType, Object payload) {
        String channel = RedisChannels.notificationUser(userId);
        String eventId = UUID.randomUUID().toString();
        EventMetadata metadata = EventMetadata.of(eventId, eventType, "notification-service", Instant.now());
        EventEnvelope<Object> envelope = new EventEnvelope<>(metadata, payload);
        redisEventPublisher.publish(channel, envelope);
        log.debug("[NOTI-REDIS] publish channel={} userId={} eventType={}", channel, userId, eventType);
    }
}
