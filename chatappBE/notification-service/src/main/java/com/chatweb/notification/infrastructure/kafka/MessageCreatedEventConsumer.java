package com.chatweb.notification.infrastructure.kafka;

import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.integration.chat.ChatMessagePayload;
import com.chatweb.common.kafka.consumer.KafkaEventDispatcher;
import com.chatweb.common.kafka.consumer.KafkaEventHandler;
import com.chatweb.common.kafka.topic.KafkaTopics;
import com.chatweb.notification.application.NotificationKafkaEventApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MessageCreatedEventConsumer implements KafkaEventHandler<ChatMessagePayload> {

    private final NotificationKafkaEventApplicationService applicationService;
    private final KafkaEventDispatcher dispatcher;

    @Override
    public String eventType() {
        return KafkaTopics.TOPIC_CHAT_MESSAGE_SENT;
    }

    @Override
    public void handle(EventEnvelope<ChatMessagePayload> event) {
        applicationService.handleChatMessageSentEvent(event.metadata().getEventId(), event.payload());
    }

    @KafkaListener(topics = KafkaTopics.TOPIC_CHAT_MESSAGE_SENT, groupId = NotificationKafkaConsumerGroups.DEFAULT)
    public void listen(EventEnvelope<?> envelope) {
        if (envelope == null || envelope.metadata() == null) return;
        dispatcher.dispatch(envelope);
    }
}
