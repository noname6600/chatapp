package com.chatweb.notification.application;

import com.chatweb.common.integration.account.AccountCreatedPayload;
import com.chatweb.common.integration.chat.ChatMessagePayload;
import com.chatweb.common.integration.chat.MessageDeletedPayload;
import com.chatweb.common.integration.chat.MessagePinPayload;
import com.chatweb.common.integration.chat.MessageUpdatedPayload;
import com.chatweb.common.integration.chat.ReactionPayload;
import com.chatweb.common.integration.friendship.FriendRequestPayload;
import com.chatweb.notification.infrastructure.kafka.NotificationEventDedupeGuard;
import com.chatweb.notification.service.impl.NotificationDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationKafkaEventApplicationService {

    private final NotificationDomainService notificationDomainService;
    private final NotificationMessageEventApplicationService notificationMessageEventApplicationService;
    private final NotificationReactionEventApplicationService notificationReactionEventApplicationService;
    private final NotificationFriendRequestEventApplicationService notificationFriendRequestEventApplicationService;
    private final NotificationEventDedupeGuard dedupeGuard;

    public void handleAccountCreatedEvent(AccountCreatedPayload payload) {
        if (payload == null) {
            return;
        }

        notificationDomainService.notifyWelcome(payload.getAccountId(), payload.getEmail());
    }

    public void handleChatMessageSentEvent(UUID eventId, ChatMessagePayload payload) {
        if (payload == null) {
            return;
        }
        String eventIdValue = eventId == null ? null : eventId.toString();
        if (eventId == null) {
            log.warn("[NOTI] Skip chat.message.sent due to missing eventId");
        }
        if (dedupeGuard.isDuplicate(eventIdValue)) {
            log.info("[NOTI] Skip duplicate chat.message.sent eventId={}", eventId);
            return;
        }

        notificationMessageEventApplicationService.handle(payload);
    }

    public void handleChatMessageSentEvent(String eventId, ChatMessagePayload payload) {
        handleChatMessageSentEvent(parseEventId(eventId), payload);
    }

    public void handleReactionUpdatedEvent(UUID eventId, ReactionPayload payload) {
        if (payload == null) {
            return;
        }
        String eventIdValue = eventId == null ? null : eventId.toString();
        if (eventId == null) {
            log.warn("[NOTI] Skip chat.reaction.updated due to missing eventId");
        }
        if (dedupeGuard.isDuplicate(eventIdValue)) {
            log.info("[NOTI] Skip duplicate chat.reaction.updated eventId={}", eventId);
            return;
        }

        notificationReactionEventApplicationService.handle(payload);
    }

    public void handleReactionUpdatedEvent(String eventId, ReactionPayload payload) {
        handleReactionUpdatedEvent(parseEventId(eventId), payload);
    }

    public void handleChatMessageEditedEvent(UUID eventId, MessageUpdatedPayload payload) {
        handleMessageMutationEvent(eventId, "chat.message.edited", payload == null ? null : payload.getMessageId(), payload);
    }

    public void handleChatMessageDeletedEvent(UUID eventId, MessageDeletedPayload payload) {
        handleMessageMutationEvent(eventId, "chat.message.deleted", payload == null ? null : payload.getMessageId(), payload);
    }

    public void handleChatMessagePinnedEvent(UUID eventId, MessagePinPayload payload) {
        handleMessageMutationEvent(eventId, "chat.message.pinned", payload == null ? null : payload.getMessageId(), payload);
    }

    public void handleChatMessageUnpinnedEvent(UUID eventId, MessagePinPayload payload) {
        handleMessageMutationEvent(eventId, "chat.message.unpinned", payload == null ? null : payload.getMessageId(), payload);
    }

    public void handleFriendRequestEvent(UUID eventId, String eventType, FriendRequestPayload payload) {
        if (payload == null || eventType == null || eventType.isBlank()) {
            return;
        }
        String eventIdValue = eventId == null ? null : eventId.toString();
        if (eventId == null) {
            log.warn("[NOTI] Skip friendship request event due to missing eventId type={}", eventType);
        }
        if (dedupeGuard.isDuplicate(eventIdValue)) {
            log.info("[NOTI] Skip duplicate friendship request eventId={}", eventId);
            return;
        }

        notificationFriendRequestEventApplicationService.handle(eventType, payload);
    }

    public void handleFriendRequestEvent(String eventId, String eventType, FriendRequestPayload payload) {
        handleFriendRequestEvent(parseEventId(eventId), eventType, payload);
    }

    private UUID parseEventId(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(eventId);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void handleMessageMutationEvent(UUID eventId, String eventType, UUID messageId, Object payload) {
        if (payload == null || messageId == null) {
            return;
        }
        String eventIdValue = eventId == null ? null : eventId.toString();
        if (eventId == null) {
            log.warn("[NOTI] Skip {} due to missing eventId", eventType);
        }
        if (dedupeGuard.isDuplicate(eventIdValue)) {
            log.info("[NOTI] Skip duplicate {} eventId={}", eventType, eventId);
            return;
        }

        log.info("[NOTI] Accepted {} eventId={} messageId={}", eventType, eventId, messageId);
    }
}
