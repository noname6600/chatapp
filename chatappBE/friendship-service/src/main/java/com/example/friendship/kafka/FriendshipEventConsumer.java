package com.example.friendship.kafka;

import com.example.common.integration.friendship.FriendshipEventType;
import com.example.common.integration.friendship.FriendshipPayload;
import com.example.common.kafka.topic.KafkaTopics;
import com.example.common.integration.kafka.event.FriendshipEvent;
import com.example.common.realtime.policy.RealtimeFlowId;
import com.example.friendship.realtime.port.FriendshipRealtimePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FriendshipEventConsumer {

    private final FriendshipRealtimePort friendshipRealtimePort;
    private final FriendshipEventDedupeGuard dedupeGuard;

    @KafkaListener(topics = KafkaTopics.FRIENDSHIP_EVENTS)
    public void listen(FriendshipEvent event) {
        if (event == null || event.getPayload() == null) {
            log.warn("[FRIEND] Received null event");
            return;
        }
        if (dedupeGuard.isDuplicate(event.getEventId())) {
            log.info("[FRIEND] Skip duplicate friendship eventId={}", event.getEventId());
            return;
        }

        FriendshipPayload payload = event.getPayload();
        String eventTypeStr = event.getEventType();

        log.info("[FRIEND] Received friendship event: {} from userLow={} userHigh={}",
                eventTypeStr, payload.getUserLow(), payload.getUserHigh());

        FriendshipEventType eventType = java.util.Arrays.stream(FriendshipEventType.values())
                .filter(type -> type.value().equals(eventTypeStr))
                .findFirst()
                .orElse(null);

        if (eventType == null) {
            log.warn("[FRIEND] Unknown friendship event type: {}", eventTypeStr);
            return;
        }

        friendshipRealtimePort.publishRelationshipEvent(
            payload.getUserLow(),
            payload.getUserHigh(),
            eventType.value(),
            payload,
            mapFlowId(eventType)
        );
    }

    private RealtimeFlowId mapFlowId(FriendshipEventType eventType) {
        return switch (eventType) {
            case FRIEND_REQUEST_ACCEPTED -> RealtimeFlowId.FRIENDSHIP_REQUEST_ACCEPTED;
            case FRIEND_REQUEST_DECLINED -> RealtimeFlowId.FRIENDSHIP_REQUEST_DECLINED;
            default -> RealtimeFlowId.FRIENDSHIP_STATUS_UPDATE;
        };
    }
}
