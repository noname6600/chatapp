package com.chatweb.realtime.adapter.in.kafka;

import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.kafka.topic.KafkaTopics;
import com.chatweb.realtime.delivery.ChatRealtimeDeliveryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Consumes chat room events from Kafka and delivers them to subscribed WebSocket
 * sessions across all realtime-edge instances via the cross-instance handoff mechanism.
 *
 * Replaces the Redis pub/sub path for chat delivery — Kafka gives durable, retry-safe
 * delivery where Redis pub/sub was fire-and-forget.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FanoutChatEventConsumer {

    private static final String CONSUMER_GROUP_ID = "realtime-edge-chat-group";

    private final ChatRealtimeDeliveryService chatDeliveryService;
    private final ChatEventDedupeGuard dedupeGuard;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = {
            KafkaTopics.TOPIC_CHAT_MESSAGE_SENT,
            KafkaTopics.TOPIC_CHAT_MESSAGE_EVENTS,
            KafkaTopics.TOPIC_CHAT_REACTION_UPDATED
        },
        groupId = CONSUMER_GROUP_ID
    )
    public void onChatEvent(EventEnvelope<?> envelope) {
        if (envelope == null || envelope.metadata() == null) {
            log.warn("[CHAT-KAFKA] Null envelope received");
            return;
        }

        String eventType = envelope.metadata().getEventType();
        String eventId = envelope.metadata().getEventId();

        if (dedupeGuard.isDuplicate(eventId)) {
            log.debug("[CHAT-KAFKA] Duplicate ignored eventId={} eventType={}", eventId, eventType);
            return;
        }

        JsonNode payload = objectMapper.valueToTree(envelope.payload());
        UUID roomId = parseUuid(payload.path("roomId").asText(null));

        if (roomId == null) {
            log.warn("[CHAT-KAFKA] Missing roomId in payload eventType={} eventId={}", eventType, eventId);
            return;
        }

        chatDeliveryService.deliverRoomWithHandoff(roomId.toString(), eventType, eventId, payload);
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (Exception ex) {
            return null;
        }
    }
}
