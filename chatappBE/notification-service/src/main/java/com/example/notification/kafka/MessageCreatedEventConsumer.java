package com.example.notification.kafka;

import com.example.common.integration.chat.ChatMessagePayload;
import com.example.common.kafka.event.ChatMessageSentEvent;
import com.example.notification.entity.NotificationType;
import com.example.notification.repository.NotificationRepository;
import com.example.notification.repository.RoomMuteSettingRepository;
import com.example.notification.service.impl.NotificationCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class MessageCreatedEventConsumer {

    private final NotificationRepository notificationRepository;
    private final RoomMuteSettingRepository roomMuteSettingRepository;
    private final NotificationCommandService notificationCommandService;

    @KafkaListener(topics = "chat.message.sent", groupId = "notification-service")
    public void listen(ChatMessageSentEvent event) {
        if (event == null || event.getPayload() == null) {
            return;
        }

        ChatMessagePayload payload = event.getPayload();
        UUID roomId = payload.getRoomId();
        UUID senderId = payload.getSenderId();
        String senderName = payload.getSenderId().toString(); // Would be enriched from user-service in production
        String preview = payload.getPreview() != null ? payload.getPreview() : payload.getContent();
        List<UUID> mentionedUserIds = payload.getMentionedUserIds() == null ? List.of() : payload.getMentionedUserIds();

        List<UUID> recipientUserIds = payload.getRecipientUserIds();
        if (recipientUserIds == null || recipientUserIds.isEmpty()) {
            log.debug("[NOTI] No recipients for message from sender {}", senderId);
            return;
        }

        for (UUID recipientUserId : recipientUserIds) {
            try {
                // Check if room is muted for this user
                boolean isMuted = roomMuteSettingRepository.findByIdUserIdAndIdRoomId(recipientUserId, roomId)
                        .isPresent();
                if (isMuted) {
                    log.debug("[NOTI] Skip MESSAGE notification for muted user {} in room {}", recipientUserId, roomId);
                    continue;
                }

                NotificationType notificationType = mentionedUserIds.contains(recipientUserId)
                    ? NotificationType.MENTION
                    : NotificationType.MESSAGE;

                // Check for idempotency per recipient + type + message reference
                boolean unreadExists = notificationRepository
                        .findFirstByUserIdAndTypeAndReferenceIdAndIsReadFalse(
                        recipientUserId, notificationType, payload.getMessageId())
                        .isPresent();
                if (unreadExists) {
                    log.debug("[NOTI] Skip duplicate {} notification for recipient {}", notificationType, recipientUserId);
                    continue;
                }

                // Create MESSAGE or MENTION notification for this recipient
                notificationCommandService.createNotification(
                        recipientUserId,
                    notificationType,
                        payload.getMessageId(),
                        roomId,
                        senderName,
                        preview
                );

                log.info("[NOTI] Created {} notification for recipient {} from message {}", notificationType, recipientUserId, payload.getMessageId());
            } catch (Exception e) {
                log.error("[NOTI] Error creating notification for recipient {}", recipientUserId, e);
            }
        }
    }
}
