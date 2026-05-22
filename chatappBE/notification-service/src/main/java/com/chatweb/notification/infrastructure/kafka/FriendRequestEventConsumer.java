package com.chatweb.notification.infrastructure.kafka;

import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.integration.friendship.FriendRequestPayload;
import com.chatweb.common.kafka.topic.KafkaTopics;
import com.chatweb.notification.application.NotificationKafkaEventApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FriendRequestEventConsumer {

    private final NotificationKafkaEventApplicationService applicationService;

    @KafkaListener(topics = KafkaTopics.TOPIC_FRIENDSHIP_REQUEST_EVENTS, groupId = NotificationKafkaConsumerGroups.DEFAULT)
    public void listen(EventEnvelope<FriendRequestPayload> envelope) {
        if (envelope == null || envelope.metadata() == null) {
            return;
        }

        String eventType = envelope.metadata().getEventType();
        FriendRequestPayload payload = envelope.payload();
        if (payload == null || eventType == null || eventType.isBlank()) {
            return;
        }

        applicationService.handleFriendRequestEvent(
                envelope.metadata().getEventId(),
                eventType,
                payload
        );
    }
}
