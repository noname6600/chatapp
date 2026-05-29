package com.chatweb.notification.infrastructure.kafka;

import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.integration.chat.ReactionPayload;
import com.chatweb.common.kafka.consumer.KafkaEventHandler;
import com.chatweb.common.kafka.topic.KafkaTopics;
import com.chatweb.notification.application.NotificationKafkaEventApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReactionUpdatedNotificationHandler implements KafkaEventHandler<ReactionPayload> {

    private final NotificationKafkaEventApplicationService applicationService;

    @Override
    public String eventType() {
        return KafkaTopics.TOPIC_CHAT_REACTION_UPDATED;
    }

    @Override
    public void handle(EventEnvelope<ReactionPayload> event) {
        applicationService.handleReactionUpdatedEvent(event.metadata().getEventId(), event.payload());
    }
}
