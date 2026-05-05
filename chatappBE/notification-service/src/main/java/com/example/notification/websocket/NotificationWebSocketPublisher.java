package com.example.notification.websocket;

import com.example.common.realtime.policy.RealtimeFlowClassificationPolicy;
import com.example.common.realtime.policy.RealtimeFlowId;
import com.example.common.realtime.policy.RealtimeFlowType;
import com.example.common.websocket.protocol.RealtimeWsEvent;
import com.example.notification.dto.NotificationResponse;
import com.example.notification.dto.UnreadCountResponse;
import com.example.notification.realtime.NotificationRealtimeEventTypes;
import com.example.notification.realtime.port.NotificationRealtimePort;
import com.example.notification.websocket.redis.RedisNotificationPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationWebSocketPublisher implements NotificationRealtimePort {

    private final RedisNotificationPublisher redisNotificationPublisher;

    @Override
    public void publishUserEvent(UUID userId, String eventType, Object payload) {
        redisNotificationPublisher.publish(
            userId,
            RealtimeWsEvent.builder()
                .type(eventType)
                .payload(payload)
                .build()
        );
    }

    @Override
    public void publishUserEvent(UUID userId, String eventType, Object payload, RealtimeFlowId flowId) {
        RealtimeFlowType flowType = RealtimeFlowClassificationPolicy.getFlowType(flowId);
        
        log.debug("Publishing user event for flow {}: eventType={}, flowType={}", 
                flowId, eventType, flowType);

        // Enforce delivery semantics based on flow classification
        if (flowType == RealtimeFlowType.DURABLE_FIRST) {
            // DURABLE-FIRST: publish to Kafka first, then fanout
            // TODO: Implement Kafka publish in task 6.2/7.2
            publishDurableFirstFlow(userId, eventType, payload);
        } else if (flowType == RealtimeFlowType.EPHEMERAL_ONLY) {
            // EPHEMERAL-ONLY: direct Redis/WebSocket fanout
            publishEphemeralOnlyFlow(userId, eventType, payload);
        } else if (flowType == RealtimeFlowType.MIXED_WITH_CONVERGENCE) {
            // MIXED-WITH-CONVERGENCE: publish to both Kafka and fanout
            // TODO: Implement Kafka publish in task 6.2/7.2
            publishMixedConvergenceFlow(userId, eventType, payload);
        }
    }

    private void publishDurableFirstFlow(UUID userId, String eventType, Object payload) {
        directFanout(userId, eventType, payload);
    }

    private void publishEphemeralOnlyFlow(UUID userId, String eventType, Object payload) {
        directFanout(userId, eventType, payload);
    }

    private void publishMixedConvergenceFlow(UUID userId, String eventType, Object payload) {
        directFanout(userId, eventType, payload);
    }

    private void directFanout(UUID userId, String eventType, Object payload) {
        redisNotificationPublisher.publish(
                userId,
                RealtimeWsEvent.builder()
                        .type(eventType)
                        .payload(payload)
                        .build()
        );
    }

    public void publishNotificationNew(UUID userId, NotificationResponse payload) {
        publishUserEvent(userId, NotificationRealtimeEventTypes.NOTIFICATION_NEW, payload);
    }

    public void publishUnreadCountUpdate(UUID userId, UnreadCountResponse payload) {
        publishUserEvent(userId, NotificationRealtimeEventTypes.UNREAD_COUNT_UPDATE, payload);
    }
}