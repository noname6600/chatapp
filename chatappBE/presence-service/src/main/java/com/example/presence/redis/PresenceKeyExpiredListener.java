package com.example.presence.redis;

import com.example.presence.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class PresenceKeyExpiredListener implements MessageListener {

    private static final String USER_PREFIX = "presence::user:";
    private final PresenceService presenceService;

    @Override
    public void onMessage(Message message, byte[] pattern) {

        String expiredKey = new String(message.getBody(), StandardCharsets.UTF_8);

        if (!expiredKey.startsWith(USER_PREFIX)) {
            return;
        }

        try {
            UUID userId = UUID.fromString(expiredKey.substring(USER_PREFIX.length()));
            presenceService.handleUserOfflineByTTL(userId);

            log.info("[PRESENCE] OFFLINE by TTL user={}", userId);

        } catch (Exception e) {
            log.warn("[PRESENCE] Cannot parse expired key {}", expiredKey, e);
        }
    }
}





