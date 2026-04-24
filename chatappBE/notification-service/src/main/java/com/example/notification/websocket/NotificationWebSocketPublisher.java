package com.example.notification.websocket;

import com.example.common.websocket.dto.WsOutgoingMessage;
import com.example.notification.dto.NotificationResponse;
import com.example.notification.dto.UnreadCountResponse;
import com.example.notification.websocket.redis.RedisNotificationPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class NotificationWebSocketPublisher {

    private static final String NOTIFICATION_NEW = "NOTIFICATION_NEW";
    private static final String UNREAD_COUNT_UPDATE = "UNREAD_COUNT_UPDATE";

    private final RedisNotificationPublisher redisNotificationPublisher;

    public void publishNotificationNew(UUID userId, NotificationResponse payload) {
        redisNotificationPublisher.publish(
                userId,
                new WsOutgoingMessage(NOTIFICATION_NEW, payload)
        );
    }

    public void publishUnreadCountUpdate(UUID userId, UnreadCountResponse payload) {
        redisNotificationPublisher.publish(
                userId,
                new WsOutgoingMessage(UNREAD_COUNT_UPDATE, payload)
        );
    }
}