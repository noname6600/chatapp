package com.chatweb.chat.modules.message.infrastructure.kafka;

import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.kafka.consumer.KafkaEventDispatcher;
import com.chatweb.common.kafka.topic.KafkaTopics;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FriendshipBlockEventConsumer {

    private static final String CONSUMER_GROUP_ID = "chat-service-block-cache";

    private final KafkaEventDispatcher dispatcher;

    @KafkaListener(topics = KafkaTopics.TOPIC_FRIENDSHIP_EVENTS, groupId = CONSUMER_GROUP_ID)
    public void onFriendshipEvent(EventEnvelope<?> envelope) {
        if (envelope == null || envelope.metadata() == null) return;
        dispatcher.dispatch(envelope);
    }
}
