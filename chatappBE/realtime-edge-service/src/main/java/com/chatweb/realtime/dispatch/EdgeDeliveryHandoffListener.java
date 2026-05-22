package com.chatweb.realtime.dispatch;

import com.chatweb.realtime.connection.RealtimeSession;
import com.chatweb.realtime.connection.RealtimeSessionRegistry;
import com.chatweb.realtime.connection.RealtimeWebSocketSessionStore;
import com.chatweb.realtime.delivery.WebSocketOutboundDeliveryQueue;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Metrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.nio.charset.StandardCharsets;
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
public class EdgeDeliveryHandoffListener implements MessageListener {

    private final RealtimeSessionRegistry sessionRegistry;
    private final RealtimeWebSocketSessionStore webSocketSessionStore;
    private final ObjectMapper objectMapper;
    private final WebSocketOutboundDeliveryQueue outboundDeliveryQueue;

    @Value("${realtime.dispatch.handoff.enabled:false}")
    private boolean enabled;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        if (!enabled) {
            return;
        }

        String raw = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            EdgeDeliveryHandoffEvent event = objectMapper.readValue(raw, EdgeDeliveryHandoffEvent.class);
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
            log.warn("[HANDOFF][CONSUME] Failed processing handoff payload={}", raw, ex);
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
