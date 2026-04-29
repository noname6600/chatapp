package com.example.notification.kafka;

import com.example.common.integration.friendship.FriendRequestEvent;
import com.example.common.integration.kafka.KafkaTopics;
import com.example.common.integration.kafka.event.FriendRequestKafkaEvent;
import com.example.notification.entity.NotificationType;
import com.example.notification.repository.NotificationRepository;
import com.example.notification.service.impl.NotificationCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FriendRequestEventConsumer {

    private final NotificationRepository notificationRepository;
    private final NotificationCommandService notificationCommandService;

        @KafkaListener(topics = KafkaTopics.FRIENDSHIP_REQUEST_EVENTS)
    public void listen(FriendRequestKafkaEvent event) {
        FriendRequestEvent payload = event.getPayload();
        if (payload == null || payload.getType() == null) {
            return;
        }

        if (payload.getType() == FriendRequestEvent.Type.SENT) {
            boolean unreadExists = notificationRepository
                    .findFirstByUserIdAndTypeAndReferenceIdAndIsReadFalse(
                            payload.getRecipientId(),
                            NotificationType.FRIEND_REQUEST,
                            payload.getSenderId()
                    )
                    .isPresent();

            if (unreadExists) {
                log.info("[NOTI] Skip duplicate unread friend request notification for recipient={}", payload.getRecipientId());
                return;
            }

            notificationCommandService.createNotification(
                    payload.getRecipientId(),
                    NotificationType.FRIEND_REQUEST,
                    payload.getSenderId(),
                    null,
                    payload.getSenderId(),
                    payload.getSenderDisplayName(),
                    null,
                    "New friend request",
                    payload.getCreatedAt(),
                    true
            );
            return;
        }

        if (payload.getType() == FriendRequestEvent.Type.ACCEPTED) {
            // Resolve the sticky friend request for the accepter (senderId = accepter, recipientId = original sender).
            notificationRepository
                    .findFirstByUserIdAndTypeAndReferenceIdAndIsReadFalse(
                            payload.getSenderId(),
                            NotificationType.FRIEND_REQUEST,
                            payload.getRecipientId()
                    )
                    .ifPresent(n -> notificationCommandService.resolveActionRequired(n.getId(), payload.getSenderId()));

            notificationCommandService.createNotification(
                    payload.getRecipientId(),
                    NotificationType.FRIEND_REQUEST_ACCEPTED,
                    payload.getSenderId(),
                    null,
                    payload.getSenderId(),
                    payload.getSenderDisplayName(),
                    null,
                    "Friend request accepted",
                    payload.getCreatedAt()
            );
            return;
        }

        if (payload.getType() == FriendRequestEvent.Type.DECLINED
                || payload.getType() == FriendRequestEvent.Type.CANCELLED) {
            // Resolve the sticky friend request for the original recipient.
            notificationRepository
                    .findFirstByUserIdAndTypeAndReferenceIdAndIsReadFalse(
                            payload.getRecipientId(),
                            NotificationType.FRIEND_REQUEST,
                            payload.getSenderId()
                    )
                    .ifPresent(n -> notificationCommandService.resolveActionRequired(n.getId(), payload.getRecipientId()));
        }
    }
}
