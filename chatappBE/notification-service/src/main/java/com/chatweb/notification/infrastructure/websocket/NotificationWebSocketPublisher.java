package com.chatweb.notification.infrastructure.websocket;

import com.chatweb.common.realtime.policy.RealtimeFlowClassificationPolicy;
import com.chatweb.common.realtime.policy.RealtimeFlowId;
import com.chatweb.common.realtime.policy.RealtimeFlowType;
import com.chatweb.notification.dto.NotificationResponse;
import com.chatweb.notification.dto.UnreadCountResponse;
import com.chatweb.notification.realtime.NotificationRealtimeEventTypes;
import com.chatweb.notification.realtime.port.NotificationRealtimePort;
import com.chatweb.notification.infrastructure.websocket.redis.RedisNotificationPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class NotificationWebSocketPublisher implements NotificationRealtimePort {

    private final RedisNotificationPublisher redisNotificationPublisher;

    @Override
    public void publishUserEvent(UUID userId, String eventType, Object payload) {
        redisNotificationPublisher.publish(userId, eventType, payload);
    }

    @Override
    public void publishUserEvent(UUID userId, String eventType, Object payload, RealtimeFlowId flowId) {
        RealtimeFlowType flowType = RealtimeFlowClassificationPolicy.getFlowType(flowId);

        log.debug("Publishing user event for flow {}: eventType={}, flowType={}",
                flowId, eventType, flowType);

        if (flowType == RealtimeFlowType.DURABLE_FIRST) {
            // TODO: Implement Kafka publish in task 6.2/7.2
            redisNotificationPublisher.publish(userId, eventType, payload);
        } else if (flowType == RealtimeFlowType.EPHEMERAL_ONLY) {
            redisNotificationPublisher.publish(userId, eventType, payload);
        } else if (flowType == RealtimeFlowType.MIXED_WITH_CONVERGENCE) {
            // TODO: Implement Kafka publish in task 6.2/7.2
            redisNotificationPublisher.publish(userId, eventType, payload);
        }
    }

    public void publishNotificationNew(UUID userId, NotificationResponse payload) {
        publishUserEvent(userId, NotificationRealtimeEventTypes.NOTIFICATION_NEW, payload);
    }

    public void publishUnreadCountUpdate(UUID userId, UnreadCountResponse payload) {
        publishUserEvent(userId, NotificationRealtimeEventTypes.UNREAD_COUNT_UPDATE, payload);
    }
}
