package com.chatweb.realtime.adapter.in.kafka;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class VoiceEventDedupeGuard {

    private static final Duration TTL = Duration.ofMinutes(5);
    private static final String KEY_PREFIX = "dedup:realtime:voice:";

    private final StringRedisTemplate redisTemplate;

    public VoiceEventDedupeGuard(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isDuplicate(String eventId) {
        if (eventId == null || eventId.isBlank()) return false;
        Boolean wasAbsent = redisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + eventId, "1", TTL);
        return !Boolean.TRUE.equals(wasAbsent);
    }
}
