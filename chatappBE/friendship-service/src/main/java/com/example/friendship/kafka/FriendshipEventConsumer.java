package com.example.friendship.kafka;

import com.example.common.integration.friendship.FriendshipEventType;
import com.example.common.integration.friendship.FriendshipPayload;
import com.example.common.integration.kafka.KafkaTopics;
import com.example.common.integration.kafka.event.FriendshipEvent;
import com.example.friendship.websocket.FriendshipWebSocketPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FriendshipEventConsumer {

    private final FriendshipWebSocketPublisher webSocketPublisher;

    @KafkaListener(topics = KafkaTopics.FRIENDSHIP_EVENTS)
    public void listen(FriendshipEvent event) {
        if (event == null || event.getPayload() == null) {
            log.warn("[FRIEND] Received null event");
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

        webSocketPublisher.publishFriendshipStatusChange(eventType, payload);
    }
}
