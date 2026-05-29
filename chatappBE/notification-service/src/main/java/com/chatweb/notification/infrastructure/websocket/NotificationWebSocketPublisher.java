package com.chatweb.notification.infrastructure.websocket;

import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.event.EventMetadata;
import com.chatweb.common.event.TraceContext;
import com.chatweb.common.kafka.producer.KafkaEventPublisher;
import com.chatweb.common.kafka.topic.KafkaTopics;
import com.chatweb.common.realtime.policy.RealtimeFlowId;
import com.chatweb.notification.dto.NotificationResponse;
import com.chatweb.notification.dto.UnreadCountResponse;
import com.chatweb.notification.realtime.NotificationRealtimeEventTypes;
import com.chatweb.notification.realtime.port.NotificationRealtimePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class NotificationWebSocketPublisher implements NotificationRealtimePort {

    private final KafkaEventPublisher kafkaEventPublisher;

    @Value("${spring.application.name}")
    private String sourceService;

    @Override
    public void publishUserEvent(UUID userId, String eventType, Object payload) {
        publish(userId, eventType, payload);
    }

    @Override
    public void publishUserEvent(UUID userId, String eventType, Object payload, RealtimeFlowId flowId) {
        publish(userId, eventType, payload);
    }

    public void publishNotificationNew(UUID userId, NotificationResponse payload) {
        publishUserEvent(userId, NotificationRealtimeEventTypes.NOTIFICATION_NEW, payload);
    }

    public void publishUnreadCountUpdate(UUID userId, UnreadCountResponse payload) {
        publishUserEvent(userId, NotificationRealtimeEventTypes.UNREAD_COUNT_UPDATE, payload);
    }

    private void publish(UUID userId, String wsEventType, Object wsPayload) {
        // Wrap with the target userId so realtime-edge knows where to deliver.
        Map<String, Object> delivery = new LinkedHashMap<>();
        delivery.put("userId", userId.toString());
        delivery.put("wsEventType", wsEventType);
        delivery.put("data", wsPayload);

        String eventId = UUID.randomUUID().toString();
        kafkaEventPublisher.publish(
                KafkaTopics.TOPIC_NOTIFICATION_REALTIME,
                userId.toString(),
                new EventEnvelope<>(
                        new EventMetadata(eventId, wsEventType, sourceService, Instant.now(),
                                TraceContext.correlationIdOrEventId(eventId)),
                        delivery
                )
        );
        log.debug("[NOTI-KAFKA] publish userId={} wsEventType={} eventId={}", userId, wsEventType, eventId);
    }
}
