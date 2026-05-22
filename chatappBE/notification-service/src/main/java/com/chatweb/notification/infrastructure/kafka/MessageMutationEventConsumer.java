package com.chatweb.notification.infrastructure.kafka;

import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.integration.chat.MessageDeletedPayload;
import com.chatweb.common.integration.chat.MessagePinPayload;
import com.chatweb.common.integration.chat.MessageUpdatedPayload;
import com.chatweb.common.kafka.topic.KafkaTopics;
import com.chatweb.notification.application.NotificationKafkaEventApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class MessageMutationEventConsumer {

    private final NotificationKafkaEventApplicationService applicationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.TOPIC_CHAT_MESSAGE_EVENTS, groupId = NotificationKafkaConsumerGroups.DEFAULT)
    public void listen(EventEnvelope<?> envelope) {
        if (envelope == null || envelope.metadata() == null || envelope.payload() == null) {
            return;
        }

        String eventType = envelope.metadata().getEventType();
        UUID eventId = parseEventId(envelope.metadata().getEventId());
        Object payload = envelope.payload();
        if (eventType == null || eventType.isBlank()) {
            return;
        }

        switch (eventType) {
            case "chat.message.edited" -> applicationService.handleChatMessageEditedEvent(
                    eventId,
                    convert(payload, MessageUpdatedPayload.class)
            );
            case "chat.message.deleted" -> applicationService.handleChatMessageDeletedEvent(
                    eventId,
                    convert(payload, MessageDeletedPayload.class)
            );
            case "chat.message.pinned" -> applicationService.handleChatMessagePinnedEvent(
                    eventId,
                    convert(payload, MessagePinPayload.class)
            );
            case "chat.message.unpinned" -> applicationService.handleChatMessageUnpinnedEvent(
                    eventId,
                    convert(payload, MessagePinPayload.class)
            );
            default -> log.debug("[NOTI] Ignoring unsupported mutation eventType={}", eventType);
        }
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

    private <T> T convert(Object payload, Class<T> targetType) {
        if (targetType.isInstance(payload)) {
            return targetType.cast(payload);
        }
        return objectMapper.convertValue(payload, targetType);
    }
}
