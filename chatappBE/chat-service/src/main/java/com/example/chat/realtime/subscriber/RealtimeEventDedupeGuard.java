package com.example.chat.realtime.subscriber;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RealtimeEventDedupeGuard {

    private static final Duration TTL = Duration.ofMinutes(5);

    private final Map<UUID, Instant> seen = new ConcurrentHashMap<>();
    private final Map<String, Instant> seenKeys = new ConcurrentHashMap<>();

    public boolean isDuplicate(UUID eventId) {
        if (eventId == null) {
            return false;
        }

        cleanupExpired();

        Instant existing = seen.putIfAbsent(eventId, Instant.now().plus(TTL));
        return existing != null;
    }

    public boolean isDuplicateKey(String eventKey) {
        if (eventKey == null || eventKey.isBlank()) {
            return false;
        }

        cleanupExpired();

        Instant existing = seenKeys.putIfAbsent(eventKey, Instant.now().plus(TTL));
        return existing != null;
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        seen.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
        seenKeys.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
    }
}

