package com.example.notification.websocket.redis;

import com.example.common.websocket.protocol.RealtimeWsEvent;
import com.example.notification.constants.NotificationRedisChannels;
import com.example.notification.websocket.WebSocketUserBroadcaster;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class RedisNotificationSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final WebSocketUserBroadcaster broadcaster;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);

        try {
            UUID userId = extractUserId(channel);
            RealtimeWsEvent outgoingMessage = objectMapper.readValue(payload, RealtimeWsEvent.class);
            broadcaster.sendToUser(userId, outgoingMessage);
            log.debug("[NOTI-REDIS] consume channel={} userId={} eventType={}", channel, userId, outgoingMessage.getType());
        } catch (Exception ex) {
            log.warn("Failed to process Redis notification message on channel {}", channel, ex);
        }
    }

    private UUID extractUserId(String channel) {
        if (channel.startsWith(NotificationRedisChannels.NOTIFICATION_USER_PREFIX)) {
            String userId = channel.substring(NotificationRedisChannels.NOTIFICATION_USER_PREFIX.length());
            return UUID.fromString(userId);
        }
        throw new IllegalArgumentException("Unexpected notification channel: " + channel);
    }
}