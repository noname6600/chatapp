package com.example.notification.service.impl;

import com.example.common.realtime.policy.RealtimeFlowId;
import com.example.notification.dto.NotificationResponse;
import com.example.notification.dto.UnreadCountResponse;
import com.example.notification.entity.Notification;
import com.example.notification.realtime.NotificationRealtimeEventTypes;
import com.example.notification.realtime.port.NotificationRealtimePort;
import com.example.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationPushService {

    private final NotificationRealtimePort notificationRealtimePort;
    private final NotificationRepository repository;

    public void pushToUser(UUID userId, Notification notification) {
        NotificationResponse payload = NotificationResponse.from(notification);

        notificationRealtimePort.publishUserEvent(
            userId,
            NotificationRealtimeEventTypes.NOTIFICATION_NEW,
            payload,
            RealtimeFlowId.NOTIFICATION_PUSH
        );

        pushUnreadCount(userId);
    }

    public void pushUnreadCount(UUID userId) {
        long count = repository.countByUserIdAndIsReadFalse(userId);

        UnreadCountResponse payload = UnreadCountResponse.builder()
                .unreadCount(count)
                .build();

        notificationRealtimePort.publishUserEvent(
            userId,
            NotificationRealtimeEventTypes.UNREAD_COUNT_UPDATE,
            payload,
            RealtimeFlowId.NOTIFICATION_PUSH
        );
    }
}



