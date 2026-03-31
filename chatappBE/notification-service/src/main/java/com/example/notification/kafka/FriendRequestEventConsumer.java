package com.example.notification.kafka;

import com.example.common.integration.friendship.FriendRequestEvent;
import com.example.common.kafka.Topics;
import com.example.common.kafka.event.FriendRequestKafkaEvent;
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

    @KafkaListener(topics = Topics.FRIENDSHIP_REQUEST_EVENTS)
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
                    null,
                    "New friend request"
            );
            return;
        }

        if (payload.getType() == FriendRequestEvent.Type.ACCEPTED) {
            notificationCommandService.createNotification(
                    payload.getRecipientId(),
                    NotificationType.FRIEND_REQUEST_ACCEPTED,
                    payload.getSenderId(),
                    null,
                    null,
                    "Friend request accepted"
            );
        }
    }
}
