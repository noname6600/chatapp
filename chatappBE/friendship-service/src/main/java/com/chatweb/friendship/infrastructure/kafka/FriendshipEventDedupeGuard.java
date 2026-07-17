package com.chatweb.friendship.infrastructure.kafka;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
public class FriendshipEventDedupeGuard {

    private static final Duration TTL = Duration.ofMinutes(5);
    private static final String KEY_PREFIX = "dedup:friendship:";

    private final StringRedisTemplate redisTemplate;

    public FriendshipEventDedupeGuard(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isDuplicate(UUID eventId) {
        if (eventId == null) {
            return false;
        }

        Boolean wasAbsent = redisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + eventId, "1", TTL);
        return !Boolean.TRUE.equals(wasAbsent);
    }
}