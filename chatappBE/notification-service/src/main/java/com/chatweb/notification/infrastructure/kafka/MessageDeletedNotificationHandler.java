package com.chatweb.notification.infrastructure.kafka;

import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.integration.chat.MessageDeletedPayload;
import com.chatweb.common.kafka.consumer.KafkaEventHandler;
import com.chatweb.notification.application.NotificationKafkaEventApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MessageDeletedNotificationHandler implements KafkaEventHandler<MessageDeletedPayload> {

    private final NotificationKafkaEventApplicationService applicationService;

    @Override
    public String eventType() {
        return "chat.message.deleted";
    }

    @Override
    public void handle(EventEnvelope<MessageDeletedPayload> event) {
        applicationService.handleChatMessageDeletedEvent(
                event.metadata().getEventId(),
                event.payload()
        );
    }
}
