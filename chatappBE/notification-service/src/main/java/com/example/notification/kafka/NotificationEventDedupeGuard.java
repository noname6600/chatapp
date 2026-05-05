package com.example.notification.kafka;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class NotificationEventDedupeGuard {

    private static final Duration TTL = Duration.ofMinutes(5);

    private final Map<UUID, Instant> seen = new ConcurrentHashMap<>();

    public boolean isDuplicate(UUID eventId) {
        if (eventId == null) {
            return false;
        }

        Instant now = Instant.now();
        seen.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
        return seen.putIfAbsent(eventId, now.plus(TTL)) != null;
    }
}