package com.chatweb.notification.application;

import com.chatweb.common.integration.friendship.FriendRequestPayload;
import com.chatweb.common.integration.friendship.FriendshipEventType;
import com.chatweb.notification.entity.NotificationType;
import com.chatweb.notification.repository.NotificationRepository;
import com.chatweb.notification.service.impl.NotificationCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationFriendRequestEventApplicationService {

    private final NotificationRepository notificationRepository;
    private final NotificationCommandService notificationCommandService;

        public void handle(String eventType, FriendRequestPayload payload) {
                if (payload == null || eventType == null || eventType.isBlank()) {
            return;
        }

                if (FriendshipEventType.FRIEND_REQUEST_SENT.value().equals(eventType)) {
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

                if (FriendshipEventType.FRIEND_REQUEST_ACCEPTED.value().equals(eventType)) {
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

        if (FriendshipEventType.FRIEND_REQUEST_DECLINED.value().equals(eventType)
                || FriendshipEventType.FRIEND_REQUEST_CANCELLED.value().equals(eventType)) {
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
