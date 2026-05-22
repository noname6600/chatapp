package com.chatweb.notification.infrastructure.kafka;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
public class NotificationEventDedupeGuard {

    private static final Duration TTL = Duration.ofMinutes(5);
    private static final String KEY_PREFIX = "dedup:notif:";

    private final StringRedisTemplate redisTemplate;

    public NotificationEventDedupeGuard(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isDuplicate(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return true;
        }

        try {
            UUID.fromString(eventId);
        } catch (IllegalArgumentException ex) {
            return true;
        }

        Boolean wasAbsent = redisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + eventId, "1", TTL);
        return !Boolean.TRUE.equals(wasAbsent);
    }
}