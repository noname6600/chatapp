package com.chatweb.realtime.adapter.in.kafka;

import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.kafka.topic.KafkaTopics;
import com.chatweb.realtime.delivery.CallDeliveryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Consumes call events from Kafka and fans them out to the relevant user sessions.
 *
 * Routing per event type:
 *   CALL_INITIATED  → callee only (informs them of incoming call)
 *   CALL_ACCEPTED   → caller only (delivers their LK token so they can connect)
 *   CALL_DECLINED   → caller only (informs them the callee declined)
 *   CALL_CANCELLED  → callee only (informs them the caller cancelled)
 *   CALL_ENDED      → both parties
 *   CALL_MISSED     → both parties
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FanoutCallEventConsumer {

    private static final String CONSUMER_GROUP_ID = "realtime-edge-call-group";

    private final CallDeliveryService callDeliveryService;
    private final VoiceEventDedupeGuard dedupeGuard;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.TOPIC_VOICE_CALL_EVENTS, groupId = CONSUMER_GROUP_ID)
    public void onCallEvent(EventEnvelope<?> envelope) {
        if (envelope == null || envelope.metadata() == null) return;

        String eventType = envelope.metadata().getEventType();
        String eventId = envelope.metadata().getEventId();

        if (dedupeGuard.isDuplicate(eventId)) {
            log.debug("[CALL-KAFKA] Duplicate ignored eventId={} eventType={}", eventId, eventType);
            return;
        }

        JsonNode payload = objectMapper.valueToTree(envelope.payload());
        UUID callerId = parseUuid(payload.path("callerId").asText(null));
        UUID calleeId = parseUuid(payload.path("calleeId").asText(null));

        if (callerId == null || calleeId == null) {
            log.warn("[CALL-KAFKA] Missing callerId/calleeId eventType={} eventId={}", eventType, eventId);
            return;
        }

        switch (eventType) {
            case "voice.call.initiated"  -> callDeliveryService.deliverToUser(calleeId, eventType, eventId, payload);
            case "voice.call.accepted"   -> callDeliveryService.deliverToUser(callerId, eventType, eventId, payload);
            case "voice.call.declined"   -> callDeliveryService.deliverToUser(callerId, eventType, eventId, payload);
            case "voice.call.cancelled"  -> callDeliveryService.deliverToUser(calleeId, eventType, eventId, payload);
            case "voice.call.ended" -> {
                callDeliveryService.deliverToUser(callerId, eventType, eventId, payload);
                callDeliveryService.deliverToUser(calleeId, eventType, eventId, payload);
            }
            case "voice.call.missed" -> {
                callDeliveryService.deliverToUser(callerId, eventType, eventId, payload);
                callDeliveryService.deliverToUser(calleeId, eventType, eventId, payload);
            }
            default -> log.debug("[CALL-KAFKA] Unknown eventType={} — ignored", eventType);
        }
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try { return UUID.fromString(value); } catch (Exception ignored) { return null; }
    }
}
