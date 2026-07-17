package com.chatweb.notification.application;

import com.chatweb.common.integration.chat.ChatMessagePayload;
import com.chatweb.notification.entity.NotificationType;
import com.chatweb.notification.entity.RoomMuteSetting;
import com.chatweb.notification.entity.RoomNotificationMode;
import com.chatweb.notification.repository.NotificationRepository;
import com.chatweb.notification.repository.RoomMuteSettingRepository;
import com.chatweb.notification.service.NotificationModePolicy;
import com.chatweb.notification.service.impl.NotificationCommandService;
import com.chatweb.notification.service.impl.RoomMuteSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationMessageEventApplicationService {

    private final NotificationRepository notificationRepository;
    private final RoomMuteSettingRepository roomMuteSettingRepository;
    private final RoomMuteSettingService roomMuteSettingService;
    private final NotificationModePolicy notificationModePolicy;
    private final NotificationCommandService notificationCommandService;

    public void handle(ChatMessagePayload payload) {
        if (payload == null) {
            return;
        }

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

        Map<UUID, RoomMuteSetting> muteSettings = roomMuteSettingRepository.findAllByIdRoomId(roomId)
                .stream()
                .collect(Collectors.toMap(s -> s.getId().getUserId(), s -> s));

        for (UUID recipientUserId : recipientUserIds) {
            try {
                RoomNotificationMode mode = Optional.ofNullable(muteSettings.get(recipientUserId))
                        .map(roomMuteSettingService::resolveMode)
                        .orElse(RoomNotificationMode.NO_RESTRICT);

                boolean isMentioned = mentionedUserIds.contains(recipientUserId);
                boolean isReplyToRecipient = replyToAuthorId != null
                        && replyToAuthorId.equals(recipientUserId)
                        && !recipientUserId.equals(senderId);
                if (!notificationModePolicy.shouldDeliverRoomEvent(mode, isMentioned, isReplyToRecipient)) {
                    log.info("[NOTI] Skip recipient={} mode={} isMentioned={} isReply={} in room {}", recipientUserId, mode, isMentioned, isReplyToRecipient, roomId);
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

                // Product decision: plain MESSAGE notifications are suppressed; only mention/reply/invite are delivered.
                if (notificationType == NotificationType.MESSAGE) {
                    log.info("[NOTI] Skip MESSAGE notification for recipient {} (MESSAGE suppression enabled)", recipientUserId);
                    continue;
                }

                boolean unreadExists = notificationRepository
                        .findFirstByUserIdAndTypeAndReferenceIdAndIsReadFalse(recipientUserId, notificationType, payload.getMessageId())
                        .isPresent();
                if (unreadExists) {
                    log.info("[NOTI] Skip duplicate {} notification for recipient {}", notificationType, recipientUserId);
                    continue;
                }

                notificationCommandService.createNotification(
                        recipientUserId,
                        notificationType,
                        payload.getMessageId(),
                        roomId,
                        senderId,
                        senderName,
                    null,
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
