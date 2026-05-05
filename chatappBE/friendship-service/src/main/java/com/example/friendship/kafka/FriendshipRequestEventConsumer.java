package com.example.friendship.kafka;

import com.example.common.integration.friendship.FriendRequestEvent;
import com.example.common.kafka.topic.KafkaTopics;
import com.example.common.integration.kafka.event.FriendRequestKafkaEvent;
import com.example.common.realtime.policy.RealtimeFlowId;
import com.example.friendship.realtime.port.FriendshipRealtimePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FriendshipRequestEventConsumer {

    private final FriendshipRealtimePort friendshipRealtimePort;
    private final FriendshipEventDedupeGuard dedupeGuard;

    @KafkaListener(topics = KafkaTopics.FRIENDSHIP_REQUEST_EVENTS)
    public void listen(FriendRequestKafkaEvent event) {
        if (event == null || event.getPayload() == null) {
            log.warn("[FRIEND] Received null friend request event");
            return;
        }
        if (dedupeGuard.isDuplicate(event.getEventId())) {
            log.info("[FRIEND] Skip duplicate friend request eventId={}", event.getEventId());
            return;
        }

        FriendRequestEvent payload = event.getPayload();
        if (payload.getType() == null) {
            log.warn("[FRIEND] Friend request event has no type");
            return;
        }

        log.info("[FRIEND] Received friend request event: {} senderId={} recipientId={}",
                payload.getType(), payload.getSenderId(), payload.getRecipientId());

        friendshipRealtimePort.publishRelationshipEvent(
            payload.getSenderId(),
            payload.getRecipientId(),
            payload.getType().name(),
            payload,
            payload.getType() == FriendRequestEvent.Type.ACCEPTED
                    ? RealtimeFlowId.FRIENDSHIP_REQUEST_ACCEPTED
                    : RealtimeFlowId.FRIENDSHIP_REQUEST_CREATED
        );
    }
}
