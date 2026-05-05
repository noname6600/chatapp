package com.example.notification.websocket.redis;

import com.example.common.websocket.protocol.RealtimeWsEvent;
import com.example.notification.constants.NotificationRedisChannels;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisNotificationPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public void publish(UUID userId, RealtimeWsEvent payload) {
        String channel = NotificationRedisChannels.userChannel(userId);

        try {
            String json = objectMapper.writeValueAsString(payload);
            redisTemplate.convertAndSend(channel, json);
            log.debug("[NOTI-REDIS] publish channel={} userId={} eventType={}", channel, userId, payload.getType());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize notification payload", ex);
        }
    }
}