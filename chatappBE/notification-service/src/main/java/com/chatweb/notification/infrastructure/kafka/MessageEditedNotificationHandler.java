package com.chatweb.notification.infrastructure.kafka;

import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.integration.chat.MessageUpdatedPayload;
import com.chatweb.common.kafka.consumer.KafkaEventHandler;
import com.chatweb.notification.application.NotificationKafkaEventApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MessageEditedNotificationHandler implements KafkaEventHandler<MessageUpdatedPayload> {

    private final NotificationKafkaEventApplicationService applicationService;

    @Override
    public String eventType() {
        return "chat.message.edited";
    }

    @Override
    public void handle(EventEnvelope<MessageUpdatedPayload> event) {
        applicationService.handleChatMessageEditedEvent(
                parseUuid(event.metadata().getEventId()),
                event.payload()
        );
    }

    private UUID parseUuid(String id) {
        if (id == null || id.isBlank()) return null;
        try { return UUID.fromString(id); } catch (Exception e) { return null; }
    }
}
