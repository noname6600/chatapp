package com.chatweb.notification.infrastructure.kafka;

import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.integration.chat.MessageUpdatedPayload;
import com.chatweb.common.kafka.consumer.KafkaEventHandler;
import com.chatweb.notification.application.NotificationKafkaEventApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
                event.metadata().getEventId(),
                event.payload()
        );
    }
}
