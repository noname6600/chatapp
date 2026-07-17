package com.chatweb.realtime.adapter.in.kafka;

import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.kafka.consumer.KafkaEventHandler;
import com.chatweb.realtime.delivery.FriendshipRealtimeDeliveryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class FriendUnblockedRealtimeHandler implements KafkaEventHandler<Object> {

    private final FriendshipRealtimeDeliveryService friendshipDeliveryService;
    private final ObjectMapper objectMapper;

    @Override
    public String eventType() { return "friend.unblocked"; }

    @Override
    public void handle(EventEnvelope<Object> event) {
        String eventId = event.metadata().getEventId();
        JsonNode node = objectMapper.valueToTree(event.payload());

        UUID userLow = parseUuid(node.path("userLow").asText(null));
        UUID userHigh = parseUuid(node.path("userHigh").asText(null));
        if (userLow == null || userHigh == null) return;

        Map<String, Object> wsPayload = new LinkedHashMap<>();
        wsPayload.put("userLow", userLow);
        wsPayload.put("userHigh", userHigh);
        wsPayload.put("actionUserId", parseUuid(node.path("actionUserId").asText(null)));
        wsPayload.put("newStatus", node.path("status").asText(null));
        wsPayload.put("eventType", "friend.unblocked");

        friendshipDeliveryService.deliverToUser(userLow, "friendship.status.changed", eventId, wsPayload);
        friendshipDeliveryService.deliverToUser(userHigh, "friendship.status.changed", eventId, wsPayload);
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try { return UUID.fromString(value); } catch (Exception ex) { return null; }
    }
}

