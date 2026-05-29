package com.chatweb.notification.infrastructure.kafka;

import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.integration.chat.ChatMessagePayload;
import com.chatweb.common.kafka.consumer.KafkaEventHandler;
import com.chatweb.common.kafka.topic.KafkaTopics;
import com.chatweb.notification.application.NotificationKafkaEventApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MessageCreatedNotificationHandler implements KafkaEventHandler<ChatMessagePayload> {

    private final NotificationKafkaEventApplicationService applicationService;

    @Override
    public String eventType() {
        return KafkaTopics.TOPIC_CHAT_MESSAGE_SENT;
    }

    @Override
    public void handle(EventEnvelope<ChatMessagePayload> event) {
        applicationService.handleChatMessageSentEvent(event.metadata().getEventId(), event.payload());
    }
}
