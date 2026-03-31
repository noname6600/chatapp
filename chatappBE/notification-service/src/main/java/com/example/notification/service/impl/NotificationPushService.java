package com.example.notification.service.impl;

import com.example.notification.dto.NotificationResponse;
import com.example.notification.dto.UnreadCountResponse;
import com.example.notification.entity.Notification;
import com.example.notification.repository.NotificationRepository;
import com.example.notification.websocket.NotificationWebSocketPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationPushService {

    private final NotificationWebSocketPublisher webSocketPublisher;
    private final NotificationRepository repository;

    public void pushToUser(UUID userId, Notification notification) {
        NotificationResponse payload = NotificationResponse.from(notification);

        webSocketPublisher.publishNotificationNew(userId, payload);

        pushUnreadCount(userId);
    }

    public void pushUnreadCount(UUID userId) {
        long count = repository.countByUserIdAndIsReadFalse(userId);

        UnreadCountResponse payload = UnreadCountResponse.builder()
                .unreadCount(count)
                .build();

        webSocketPublisher.publishUnreadCountUpdate(userId, payload);
    }
}



