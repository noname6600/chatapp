package com.chatweb.realtime.adapter.in.kafka;

import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.kafka.topic.KafkaTopics;
import com.chatweb.realtime.delivery.NotificationRealtimeDeliveryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Consumes notification delivery events from Kafka and delivers them to the target
 * user's WebSocket sessions across all realtime-edge instances via handoff.
 *
 * Replaces the Redis pub/sub path for notification delivery.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FanoutNotificationEventConsumer {

    private static final String CONSUMER_GROUP_ID = "realtime-edge-notification-group";

    private final NotificationRealtimeDeliveryService notificationDeliveryService;
    private final NotificationEventDedupeGuard dedupeGuard;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.TOPIC_NOTIFICATION_REALTIME, groupId = CONSUMER_GROUP_ID)
    public void onNotificationEvent(EventEnvelope<?> envelope) {
        if (envelope == null || envelope.metadata() == null) {
            log.warn("[NOTI-KAFKA] Null envelope received");
            return;
        }

        String eventId = envelope.metadata().getEventId();

        if (dedupeGuard.isDuplicate(eventId)) {
            log.debug("[NOTI-KAFKA] Duplicate ignored eventId={}", eventId);
            return;
        }

        JsonNode wrapper = objectMapper.valueToTree(envelope.payload());
        UUID userId = parseUuid(wrapper.path("userId").asText(null));
        String wsEventType = wrapper.path("wsEventType").asText(null);
        JsonNode data = wrapper.path("data");

        if (userId == null || wsEventType == null || wsEventType.isBlank()) {
            log.warn("[NOTI-KAFKA] Missing userId or wsEventType eventId={}", eventId);
            return;
        }

        notificationDeliveryService.deliverToUserWithHandoff(userId, wsEventType, eventId, data);
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
