package com.chatweb.notification.infrastructure.kafka;

import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.kafka.consumer.KafkaEventDispatcher;
import com.chatweb.common.kafka.topic.KafkaTopics;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FriendRequestEventConsumer {

    private final KafkaEventDispatcher dispatcher;

    @KafkaListener(topics = KafkaTopics.TOPIC_FRIENDSHIP_REQUEST_EVENTS, groupId = NotificationKafkaConsumerGroups.DEFAULT)
    public void listen(EventEnvelope<?> envelope) {
        if (envelope == null || envelope.metadata() == null) return;
        dispatcher.dispatch(envelope);
    }
}
