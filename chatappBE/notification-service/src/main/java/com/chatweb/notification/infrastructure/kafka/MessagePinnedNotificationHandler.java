package com.chatweb.notification.infrastructure.kafka;

import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.integration.chat.MessagePinPayload;
import com.chatweb.common.kafka.consumer.KafkaEventHandler;
import com.chatweb.notification.application.NotificationKafkaEventApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MessagePinnedNotificationHandler implements KafkaEventHandler<MessagePinPayload> {

    private final NotificationKafkaEventApplicationService applicationService;

    @Override
    public String eventType() {
        return "chat.message.pinned";
    }

    @Override
    public void handle(EventEnvelope<MessagePinPayload> event) {
        applicationService.handleChatMessagePinnedEvent(
                event.metadata().getEventId(),
                event.payload()
        );
    }
}
