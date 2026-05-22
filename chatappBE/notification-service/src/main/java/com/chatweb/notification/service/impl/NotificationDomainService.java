package com.chatweb.notification.service.impl;

import com.chatweb.notification.entity.Notification;
import com.chatweb.notification.entity.NotificationType;
import com.chatweb.notification.infrastructure.kafka.NotificationEventProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationDomainService {

    private final NotificationCommandService commandService;
    private final NotificationEventProducer producer;

    public void notifyChatMessage(UUID receiverId, String messageContent, UUID senderId) {
        Notification noti = commandService.createNotification(
        receiverId,
        NotificationType.MESSAGE,
        senderId,
        null,
        senderId,
        senderId.toString(),
        null,
        messageContent,
        Instant.now()
    );

        producer.publishRequested(noti);
    }

    public void notifyWelcome(UUID userId, String email) {
        Notification noti = commandService.createNotification(
        userId,
        NotificationType.WELCOME,
        userId,
        null,
        null,
        "System",
        "System",
        "Account " + email + " has been register successfully!",
        Instant.now()
    );

        producer.publishRequested(noti);
    }
}

