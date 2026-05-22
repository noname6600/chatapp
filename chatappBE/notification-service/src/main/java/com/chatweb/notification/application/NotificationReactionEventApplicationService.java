package com.chatweb.notification.application;

import com.chatweb.common.integration.chat.ReactionPayload;
import com.chatweb.common.integration.enums.ReactionAction;
import com.chatweb.notification.entity.NotificationType;
import com.chatweb.notification.repository.NotificationRepository;
import com.chatweb.notification.service.impl.NotificationCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationReactionEventApplicationService {

    private final NotificationRepository notificationRepository;
    private final NotificationCommandService notificationCommandService;

    public void handle(ReactionPayload payload) {
        if (payload == null) {
            return;
        }

        log.info("[NOTI] Kafka received reaction | messageId={} userId={} action={} messageAuthorId={} emoji={}",
                payload.getMessageId(), payload.getUserId(), payload.getAction(), payload.getMessageAuthorId(), payload.getEmoji());

        if (ReactionAction.REMOVE.equals(payload.getAction())) {
            log.debug("[NOTI] Skipping REMOVE reaction event for message {}", payload.getMessageId());
            return;
        }

        if (payload.getMessageAuthorId() == null) {
            log.debug("[NOTI] Skipping reaction event: messageAuthorId is missing for message {}", payload.getMessageId());
            return;
        }
        if (payload.getMessageAuthorId().equals(payload.getUserId())) {
            log.debug("[NOTI] Skipping self-reaction for message {} by user {}", payload.getMessageId(), payload.getUserId());
            return;
        }

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
