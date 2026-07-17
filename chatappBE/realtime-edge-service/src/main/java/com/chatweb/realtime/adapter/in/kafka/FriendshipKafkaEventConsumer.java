package com.chatweb.realtime.adapter.in.kafka;

import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.kafka.consumer.KafkaEventDispatcher;
import com.chatweb.common.kafka.topic.KafkaTopics;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FriendshipKafkaEventConsumer {

    private static final String CONSUMER_GROUP_ID = "realtime-edge-friendship-group";

    private final KafkaEventDispatcher dispatcher;
    private final FriendshipEventDedupeGuard dedupeGuard;

    @KafkaListener(topics = KafkaTopics.TOPIC_FRIENDSHIP_REQUEST_EVENTS, groupId = CONSUMER_GROUP_ID)
    public void onFriendRequestEvent(EventEnvelope<?> envelope) {
        if (envelope == null || envelope.metadata() == null) return;
        if (dedupeGuard.isDuplicate(envelope.metadata().getEventId())) return;
        dispatcher.dispatch(envelope);
    }

    @KafkaListener(topics = KafkaTopics.TOPIC_FRIENDSHIP_EVENTS, groupId = CONSUMER_GROUP_ID)
    public void onFriendshipEvent(EventEnvelope<?> envelope) {
        if (envelope == null || envelope.metadata() == null) return;
        if (dedupeGuard.isDuplicate(envelope.metadata().getEventId())) return;
        dispatcher.dispatch(envelope);
    }
}
