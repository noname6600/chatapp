package com.example.notification.kafka;

import com.example.common.integration.chat.ReactionPayload;
import com.example.common.integration.enums.ReactionAction;
import com.example.common.integration.kafka.KafkaTopics;
import com.example.common.integration.kafka.event.ChatReactionUpdatedEvent;
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
public class ReactionEventConsumer {

    private final NotificationRepository notificationRepository;
    private final NotificationCommandService notificationCommandService;

    @KafkaListener(topics = KafkaTopics.CHAT_REACTION_UPDATED, groupId = "notification-service")
    public void listen(ChatReactionUpdatedEvent event) {
        if (event == null || event.getPayload() == null) {
            return;
        }

        ReactionPayload payload = event.getPayload();

        log.info("[NOTI] Kafka received reaction | messageId={} userId={} action={} messageAuthorId={} emoji={}",
                payload.getMessageId(), payload.getUserId(), payload.getAction(), payload.getMessageAuthorId(), payload.getEmoji());

        // Only notify on ADD, not REMOVE
        if (ReactionAction.REMOVE.equals(payload.getAction())) {
            log.debug("[NOTI] Skipping REMOVE reaction event for message {}", payload.getMessageId());
            return;
        }

        // Self-reaction: do not notify
        if (payload.getMessageAuthorId() == null) {
            log.debug("[NOTI] Skipping reaction event: messageAuthorId is missing for message {}", payload.getMessageId());
            return;
        }
        if (payload.getMessageAuthorId().equals(payload.getUserId())) {
            log.debug("[NOTI] Skipping self-reaction for message {} by user {}", payload.getMessageId(), payload.getUserId());
            return;
        }

        // Idempotency: skip if an unread REACTION notification for this message+reactor already exists
        boolean unreadExists = notificationRepository
                .findFirstByUserIdAndTypeAndReferenceIdAndIsReadFalse(
                        payload.getMessageAuthorId(),
                        NotificationType.REACTION,
                        payload.getMessageId())
                .isPresent();
        if (unreadExists) {
            log.debug("[NOTI] Skip duplicate REACTION notification for message {}", payload.getMessageId());
            return;
        }

        String emoji = payload.getEmoji() != null ? payload.getEmoji() : "";
        String preview = "reacted " + emoji + " to your message";

        notificationCommandService.createNotification(
                payload.getMessageAuthorId(),
                NotificationType.REACTION,
                payload.getMessageId(),
                payload.getRoomId(),
                payload.getUserId(),
                payload.getActorDisplayName(),
                null,
                preview,
                payload.getCreatedAt()
        );

        log.info("[NOTI] Created REACTION notification for message author {} from reactor {}", payload.getMessageAuthorId(), payload.getUserId());
    }
}
