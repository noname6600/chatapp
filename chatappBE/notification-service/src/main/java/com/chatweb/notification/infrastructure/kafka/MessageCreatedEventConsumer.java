package com.chatweb.notification.infrastructure.kafka;

import com.chatweb.common.integration.chat.ChatMessagePayload;
import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.kafka.topic.KafkaTopics;
import com.chatweb.notification.application.NotificationKafkaEventApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MessageCreatedEventConsumer {

    private final NotificationKafkaEventApplicationService applicationService;

    @KafkaListener(topics = KafkaTopics.TOPIC_CHAT_MESSAGE_SENT, groupId = NotificationKafkaConsumerGroups.DEFAULT)
    public void listen(EventEnvelope<ChatMessagePayload> envelope) {
        if (envelope == null || envelope.metadata() == null || envelope.payload() == null) {
            return;
        }

        applicationService.handleChatMessageSentEvent(
                envelope.metadata().getEventId(),
                envelope.payload()
        );
    }
}
