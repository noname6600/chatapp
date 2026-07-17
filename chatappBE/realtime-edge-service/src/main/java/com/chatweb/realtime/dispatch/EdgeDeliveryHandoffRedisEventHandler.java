package com.chatweb.realtime.dispatch;

import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.redis.subscriber.RedisEventHandler;
import com.chatweb.realtime.connection.RealtimeSession;
import com.chatweb.realtime.connection.RealtimeSessionRegistry;
import com.chatweb.realtime.connection.RealtimeWebSocketSessionStore;
import com.chatweb.realtime.delivery.WebSocketOutboundDeliveryQueue;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Metrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Consumes cross-instance handoff events and performs local websocket sends.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EdgeDeliveryHandoffRedisEventHandler implements RedisEventHandler<EdgeDeliveryHandoffEvent> {

    private final RealtimeSessionRegistry sessionRegistry;
    private final RealtimeWebSocketSessionStore webSocketSessionStore;
    private final ObjectMapper objectMapper;
    private final WebSocketOutboundDeliveryQueue outboundDeliveryQueue;

    @Value("${realtime.dispatch.handoff.enabled:false}")
    private boolean enabled;

    @Override
    public String eventType() {
        return EdgeDeliveryHandoffEvent.REDIS_EVENT_TYPE;
    }

    @Override
    public boolean supports(String channel, EventEnvelope<?> envelope) {
        if (!enabled || channel == null || channel.isBlank() || envelope == null || envelope.metadata() == null) {
            return false;
        }
        String type = envelope.metadata().getEventType();
        return EdgeDeliveryHandoffEvent.REDIS_EVENT_TYPE.equals(type)
                && channel.startsWith(EdgeDeliveryHandoffEvent.REDIS_CHANNEL_PREFIX + ".");
    }

    @Override
    public void handle(String channel, EventEnvelope<EdgeDeliveryHandoffEvent> envelope) {
        if (!enabled) {
            return;
        }

        try {
            EdgeDeliveryHandoffEvent event = envelope == null ? null : envelope.payload();
            if (event == null || event.getTargetInstanceId() == null || event.getTargetInstanceId().isBlank()) {
                return;
            }

            String localInstanceId = sessionRegistry.getCurrentInstanceId();
            if (!event.getTargetInstanceId().equals(localInstanceId)) {
                return;
            }

            List<RealtimeSession> targetSessions = resolveTargetSessions(event);
            int delivered = deliverToSessions(targetSessions, event.getEventType(), event.getOriginalEventId(), event.getPayload());
            int failed = Math.max(0, targetSessions.size() - delivered);

            if (delivered > 0) {
                Metrics.counter("realtime.dispatch.remote.handoff.consume.success", "deliveryType", safeTag(event.getDeliveryType())).increment(delivered);
            }
            if (failed > 0) {
                Metrics.counter("realtime.dispatch.remote.handoff.consume.failure", "deliveryType", safeTag(event.getDeliveryType())).increment(failed);
            }

            log.debug("[HANDOFF][CONSUME] targetInstance={} sourceInstance={} deliveryType={} eventType={} originalEventId={} matchedSessions={} delivered={} failed={}",
                    event.getTargetInstanceId(),
                    event.getSourceInstanceId(),
                    event.getDeliveryType(),
                    event.getEventType(),
                    event.getOriginalEventId(),
                    targetSessions.size(),
                    delivered,
                    failed);
        } catch (Exception ex) {
            Metrics.counter("realtime.dispatch.remote.handoff.consume.failure", "deliveryType", "unknown").increment();
            log.warn("[HANDOFF][CONSUME] Failed processing handoff payload channel={}", channel, ex);
        }
    }

    private List<RealtimeSession> resolveTargetSessions(EdgeDeliveryHandoffEvent event) {
        if (event.getTargetSessionIds() != null && !event.getTargetSessionIds().isEmpty()) {
            String localInstanceId = sessionRegistry.getCurrentInstanceId();
            return event.getTargetSessionIds().stream()
                    .map(sessionRegistry::findBySessionId)
                    .filter(java.util.Optional::isPresent)
                    .map(java.util.Optional::get)
                    .filter(s -> localInstanceId.equals(s.getInstanceId()))
                    .collect(Collectors.toList());
        }

        if (event.getSubscriptionKey() != null && !event.getSubscriptionKey().isBlank()) {
            return sessionRegistry.findByChannelOwnedByCurrentInstance(event.getSubscriptionKey())
                    .stream()
                    .collect(Collectors.toList());
        }

        if (event.getTargetUserId() != null && !event.getTargetUserId().isBlank()) {
            try {
                return sessionRegistry.findByUserIdOwnedByCurrentInstance(java.util.UUID.fromString(event.getTargetUserId()))
                        .stream()
                        .collect(Collectors.toList());
            } catch (Exception ignored) {
                return List.of();
            }
        }

        return List.of();
    }

    private String safeTag(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private int deliverToSessions(List<RealtimeSession> sessions, String eventType, String eventId, Object payload) {
        int delivered = 0;
        for (RealtimeSession session : sessions) {
            WebSocketSession webSocketSession = webSocketSessionStore.findBySessionId(session.getSessionId()).orElse(null);
            if (webSocketSession == null || !webSocketSession.isOpen()) {
                continue;
            }

            Map<String, Object> message = new LinkedHashMap<>();
            message.put("type", eventType);
            message.put("payload", payload);
            message.put("eventId", eventId);

            try {
                String json = objectMapper.writeValueAsString(message);
                if (outboundDeliveryQueue.enqueue(session.getSessionId(), webSocketSession, json, safeTag(eventType))) {
                    delivered++;
                }
            } catch (Exception ex) {
                log.warn("[HANDOFF][CONSUME] Failed local delivery sessionId={} eventType={} eventId={}",
                        session.getSessionId(), eventType, eventId, ex);
            }
        }
        return delivered;
    }
}
