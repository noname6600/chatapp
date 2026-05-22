package com.chatweb.realtime.adapter.in.kafka;

import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.kafka.topic.KafkaTopics;
import com.chatweb.realtime.delivery.FriendshipRealtimeDeliveryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Consumes friendship domain events from Kafka and forwards websocket messages
 * for phase-2 friendship ingress/delivery ownership in realtime-edge.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FriendshipKafkaEventConsumer {

    private static final String WS_FRIEND_REQUEST_RECEIVED = "friendship.request.received";
    private static final String WS_FRIEND_REQUEST_ACCEPTED = "friendship.request.accepted";
    private static final String WS_FRIEND_REQUEST_DECLINED = "friendship.request.declined";
    private static final String WS_FRIEND_REQUEST_CANCELLED = "friendship.request.cancelled";
    private static final String WS_FRIEND_STATUS_CHANGED = "friendship.status.changed";
    private static final String CONSUMER_GROUP_ID = "realtime-edge-friendship-group";

    private final FriendshipRealtimeDeliveryService friendshipDeliveryService;
    private final FriendshipEventDedupeGuard dedupeGuard;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.TOPIC_FRIENDSHIP_REQUEST_EVENTS, groupId = CONSUMER_GROUP_ID)
    public void onFriendRequestEvent(EventEnvelope<?> envelope) {
        if (envelope == null || envelope.metadata() == null) {
            log.warn("[FRIEND-KAFKA] Null friend-request envelope received");
            return;
        }

        String eventType = envelope.metadata().getEventType();
        String eventId = envelope.metadata().getEventId();
        if (dedupeGuard.isDuplicate(eventId)) {
            log.debug("[FRIEND-KAFKA] Duplicate friend-request event ignored eventId={} eventType={}", eventId, eventType);
            return;
        }

        JsonNode payloadNode = objectMapper.valueToTree(envelope.payload());

        UUID senderId = parseUuid(payloadNode.path("senderId").asText(null));
        UUID recipientId = parseUuid(payloadNode.path("recipientId").asText(null));
        UUID requestId = parseUuid(payloadNode.path("requestId").asText(null));

        if (senderId == null || recipientId == null || requestId == null) {
            log.warn("[FRIEND-KAFKA] Invalid friend-request payload eventType={} eventId={}", eventType, eventId);
            return;
        }

        Map<String, Object> wsPayload = new LinkedHashMap<>();
        wsPayload.put("senderId", senderId);
        wsPayload.put("recipientId", recipientId);
        wsPayload.put("requestId", requestId);
        wsPayload.put("senderDisplayName", payloadNode.path("senderDisplayName").asText(null));
        wsPayload.put("createdAt", parseInstant(payloadNode.path("createdAt").asText(null)));

        switch (eventType) {
            case "friend.request.sent" -> {
                wsPayload.put("type", "SENT");
                friendshipDeliveryService.deliverToUser(recipientId, WS_FRIEND_REQUEST_RECEIVED, eventId, wsPayload);
            }
            case "friend.request.accepted" -> {
                wsPayload.put("type", "ACCEPTED");
                friendshipDeliveryService.deliverToUser(senderId, WS_FRIEND_REQUEST_ACCEPTED, eventId, wsPayload);
                friendshipDeliveryService.deliverToUser(recipientId, WS_FRIEND_REQUEST_ACCEPTED, eventId, wsPayload);
            }
            case "friend.request.declined" -> {
                wsPayload.put("type", "DECLINED");
                friendshipDeliveryService.deliverToUser(senderId, WS_FRIEND_REQUEST_DECLINED, eventId, wsPayload);
                friendshipDeliveryService.deliverToUser(recipientId, WS_FRIEND_REQUEST_DECLINED, eventId, wsPayload);
            }
            case "friend.request.cancelled" -> {
                wsPayload.put("type", "CANCELLED");
                friendshipDeliveryService.deliverToUser(senderId, WS_FRIEND_REQUEST_CANCELLED, eventId, wsPayload);
                friendshipDeliveryService.deliverToUser(recipientId, WS_FRIEND_REQUEST_CANCELLED, eventId, wsPayload);
            }
            default -> log.debug("[FRIEND-KAFKA] Ignore unmapped friend-request eventType={} eventId={}", eventType, eventId);
        }
    }

    @KafkaListener(topics = KafkaTopics.TOPIC_FRIENDSHIP_EVENTS, groupId = CONSUMER_GROUP_ID)
    public void onFriendshipEvent(EventEnvelope<?> envelope) {
        if (envelope == null || envelope.metadata() == null) {
            log.warn("[FRIEND-KAFKA] Null friendship envelope received");
            return;
        }

        String eventType = envelope.metadata().getEventType();
        String eventId = envelope.metadata().getEventId();
        if (dedupeGuard.isDuplicate(eventId)) {
            log.debug("[FRIEND-KAFKA] Duplicate friendship event ignored eventId={} eventType={}", eventId, eventType);
            return;
        }

        JsonNode payloadNode = objectMapper.valueToTree(envelope.payload());

        UUID userLow = parseUuid(payloadNode.path("userLow").asText(null));
        UUID userHigh = parseUuid(payloadNode.path("userHigh").asText(null));
        UUID actionUserId = parseUuid(payloadNode.path("actionUserId").asText(null));
        String newStatus = payloadNode.path("status").asText(null);

        if (userLow == null || userHigh == null) {
            log.warn("[FRIEND-KAFKA] Invalid friendship payload eventType={} eventId={}", eventType, eventId);
            return;
        }

        String wsType = mapStatusEventToWsType(eventType);
        if (wsType == null) {
            return;
        }

        Map<String, Object> wsPayload = new LinkedHashMap<>();
        wsPayload.put("userLow", userLow);
        wsPayload.put("userHigh", userHigh);
        wsPayload.put("actionUserId", actionUserId);
        wsPayload.put("newStatus", newStatus);
        wsPayload.put("eventType", eventType);

        friendshipDeliveryService.deliverToUser(userLow, wsType, eventId, wsPayload);
        friendshipDeliveryService.deliverToUser(userHigh, wsType, eventId, wsPayload);
    }

    private String mapStatusEventToWsType(String eventType) {
        return switch (eventType) {
            case "friend.request.accepted" -> WS_FRIEND_REQUEST_ACCEPTED;
            case "friend.request.declined" -> WS_FRIEND_REQUEST_DECLINED;
            case "friend.request.cancelled" -> WS_FRIEND_REQUEST_CANCELLED;
            case "friend.unfriended", "friend.blocked", "friend.unblocked" -> WS_FRIEND_STATUS_CHANGED;
            // friend.request.sent is emitted on aggregate friendship topic but ws fanout
            // is handled by friendship.request.events for recipient-only semantics.
            default -> null;
        };
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception ex) {
            return null;
        }
    }
}
