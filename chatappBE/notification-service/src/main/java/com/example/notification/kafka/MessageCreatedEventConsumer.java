package com.example.notification.kafka;

import com.example.common.integration.chat.ChatMessagePayload;
import com.example.common.kafka.topic.KafkaTopics;
import com.example.common.integration.kafka.event.ChatMessageSentEvent;
import com.example.notification.entity.RoomNotificationMode;
import com.example.notification.entity.NotificationType;
import com.example.notification.repository.NotificationRepository;
import com.example.notification.repository.RoomMuteSettingRepository;
import com.example.notification.service.impl.RoomMuteSettingService;
import com.example.notification.service.impl.NotificationCommandService;
import com.example.notification.service.NotificationModePolicy;
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
    private final RoomMuteSettingService roomMuteSettingService;
    private final NotificationModePolicy notificationModePolicy;
    private final NotificationCommandService notificationCommandService;
    private final NotificationEventDedupeGuard dedupeGuard;

    @KafkaListener(topics = KafkaTopics.CHAT_MESSAGE_SENT, groupId = "notification-service")
    public void listen(ChatMessageSentEvent event) {
        if (event == null || event.getPayload() == null) {
            return;
        }
        if (dedupeGuard.isDuplicate(event.getEventId())) {
            log.info("[NOTI] Skip duplicate chat.message.sent eventId={}", event.getEventId());
            return;
        }

        ChatMessagePayload payload = event.getPayload();
        UUID roomId = payload.getRoomId();
        UUID senderId = payload.getSenderId();
        String senderName = payload.getSenderDisplayName() != null && !payload.getSenderDisplayName().isBlank()
                ? payload.getSenderDisplayName()
                : null;
        String preview = payload.getPreview() != null ? payload.getPreview() : payload.getContent();
        List<UUID> mentionedUserIds = payload.getMentionedUserIds() == null ? List.of() : payload.getMentionedUserIds();
        UUID replyToAuthorId = payload.getReplyToAuthorId();

        log.info("[NOTI] Kafka received chat.message.sent | messageId={} roomId={} senderId={} replyToMessageId={} replyToAuthorId={}",
                payload.getMessageId(), roomId, senderId, payload.getReplyToMessageId(), replyToAuthorId);

        boolean isInviteCard = payload.getBlocks() != null && payload.getBlocks().stream()
                .anyMatch(block -> block != null && (block.getRoomInvite() != null || "ROOM_INVITE".equalsIgnoreCase(block.getType())));

        List<UUID> recipientUserIds = payload.getRecipientUserIds();
        if (recipientUserIds == null || recipientUserIds.isEmpty()) {
            log.info("[NOTI] No recipients for message {} from sender {}", payload.getMessageId(), senderId);
            return;
        }

        log.info("[NOTI] Processing message {} | sender={} replyToAuthorId={} mentionedUsers={} recipients={}",
                payload.getMessageId(), senderId, replyToAuthorId, mentionedUserIds, recipientUserIds);

        for (UUID recipientUserId : recipientUserIds) {
            try {
                RoomNotificationMode mode = roomMuteSettingRepository.findByIdUserIdAndIdRoomId(recipientUserId, roomId)
                        .map(roomMuteSettingService::resolveMode)
                        .orElse(RoomNotificationMode.NO_RESTRICT);

                boolean isMentioned = mentionedUserIds.contains(recipientUserId);
                if (!notificationModePolicy.shouldDeliverRoomEvent(mode, isMentioned)) {
                    log.info("[NOTI] Skip recipient={} mode={} isMentioned={} in room {}", recipientUserId, mode, isMentioned, roomId);
                    continue;
                }

                NotificationType notificationType = isMentioned
                    ? NotificationType.MENTION
                    : (isInviteCard
                        ? NotificationType.GROUP_INVITE
                        : (replyToAuthorId != null && replyToAuthorId.equals(recipientUserId) && !recipientUserId.equals(senderId)
                            ? NotificationType.REPLY
                            : NotificationType.MESSAGE));

                log.info("[NOTI] Assigned type={} for recipient={} | replyToAuthorId={} isMentioned={} isInviteCard={}",
                        notificationType, recipientUserId, replyToAuthorId, isMentioned, isInviteCard);

                // Skip MESSAGE-type notifications - they are suppressed to reduce noise and system load
                if (notificationType == NotificationType.MESSAGE) {
                    log.info("[NOTI] Skip MESSAGE notification for recipient {} (MESSAGE suppression enabled)", recipientUserId);
                    continue;
                }

                // Check for idempotency per recipient + type + message reference
                boolean unreadExists = notificationRepository
                        .findFirstByUserIdAndTypeAndReferenceIdAndIsReadFalse(
                        recipientUserId, notificationType, payload.getMessageId())
                        .isPresent();
                if (unreadExists) {
                    log.info("[NOTI] Skip duplicate {} notification for recipient {}", notificationType, recipientUserId);
                    continue;
                }

                // Create MENTION, REPLY, GROUP_INVITE or other actionable notification for this recipient
                notificationCommandService.createNotification(
                        recipientUserId,
                        notificationType,
                        payload.getMessageId(),
                        roomId,
                        senderId,
                        senderName,
                        senderName,
                        preview,
                        payload.getCreatedAt()
                );

                log.info("[NOTI] Created {} notification for recipient {} from message {}", notificationType, recipientUserId, payload.getMessageId());
            } catch (Exception e) {
                log.error("[NOTI] Error creating notification for recipient {}", recipientUserId, e);
            }
        }
    }
}
