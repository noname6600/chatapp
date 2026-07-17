package com.chatweb.realtime.adapter.in.kafka;

import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.kafka.topic.KafkaTopics;
import com.chatweb.realtime.delivery.VoiceRoomDeliveryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes voice room events from Kafka and fans them out to all WebSocket sessions
 * subscribed to the affected chat room channel.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VoiceRoomEventConsumer {

    private static final String CONSUMER_GROUP_ID = "realtime-edge-voice-room-group";

    private final VoiceRoomDeliveryService voiceRoomDeliveryService;
    private final VoiceEventDedupeGuard dedupeGuard;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.TOPIC_VOICE_ROOM_EVENTS, groupId = CONSUMER_GROUP_ID)
    public void onVoiceRoomEvent(EventEnvelope<?> envelope) {
        if (envelope == null || envelope.metadata() == null) {
            log.warn("[VOICE-ROOM-KAFKA] Null envelope received");
            return;
        }

        String eventType = envelope.metadata().getEventType();
        String eventId = envelope.metadata().getEventId();

        if (dedupeGuard.isDuplicate(eventId)) {
            log.debug("[VOICE-ROOM-KAFKA] Duplicate ignored eventId={} eventType={}", eventId, eventType);
            return;
        }

        JsonNode payload = objectMapper.valueToTree(envelope.payload());
        String chatRoomId = payload.path("chatRoomId").asText(null);

        if (chatRoomId == null || chatRoomId.isBlank()) {
            log.warn("[VOICE-ROOM-KAFKA] Missing chatRoomId eventType={} eventId={}", eventType, eventId);
            return;
        }

        voiceRoomDeliveryService.deliverToRoom(chatRoomId, eventType, eventId, payload);
    }
}
