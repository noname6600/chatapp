package com.chatweb.notification.service.impl;

import com.chatweb.notification.entity.Notification;
import com.chatweb.notification.entity.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationDomainService {

    private final NotificationCommandService commandService;

    public void notifyChatMessage(UUID receiverId, String messageContent, UUID senderId) {
        commandService.createNotification(
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
    }

    public void notifyWelcome(UUID userId, String email) {
        commandService.createNotification(
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
    }
}

