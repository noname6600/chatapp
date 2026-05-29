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
public class FriendRequestDeclinedRealtimeHandler implements KafkaEventHandler<Object> {

    private final FriendshipRealtimeDeliveryService friendshipDeliveryService;
    private final ObjectMapper objectMapper;

    @Override
    public String eventType() { return "friend.request.declined"; }

    @Override
    public void handle(EventEnvelope<Object> event) {
        String eventId = event.metadata().getEventId();
        JsonNode node = objectMapper.valueToTree(event.payload());

        UUID senderId = parseUuid(node.path("senderId").asText(null));
        UUID recipientId = parseUuid(node.path("recipientId").asText(null));

        Map<String, Object> wsPayload = new LinkedHashMap<>();
        wsPayload.put("senderId", senderId);
        wsPayload.put("recipientId", recipientId);
        wsPayload.put("requestId", parseUuid(node.path("requestId").asText(null)));
        wsPayload.put("senderDisplayName", node.path("senderDisplayName").asText(null));
        wsPayload.put("type", "friend.request.declined".substring("friend.request.".length()).toUpperCase());

        if (senderId != null) friendshipDeliveryService.deliverToUser(senderId, "friendship.request.declined", eventId, wsPayload);
        if (recipientId != null) friendshipDeliveryService.deliverToUser(recipientId, "friendship.request.declined", eventId, wsPayload);
    }

    private UUID parseUuid(String v) {
        if (v == null || v.isBlank()) return null;
        try { return UUID.fromString(v); } catch (Exception ex) { return null; }
    }
}
