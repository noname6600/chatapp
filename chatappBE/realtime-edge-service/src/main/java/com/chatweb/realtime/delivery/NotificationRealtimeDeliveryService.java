package com.chatweb.realtime.delivery;

import com.chatweb.realtime.connection.RealtimeSession;
import com.chatweb.realtime.connection.RealtimeSessionRegistry;
import com.chatweb.realtime.connection.RealtimeWebSocketSessionStore;
import com.chatweb.realtime.dispatch.EdgeCrossInstanceDispatchCoordinator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Delivers notification events to active websocket sessions for a target user.
 *
 * deliverToUserWithHandoff — Kafka path (primary). Finds ALL sessions for the user
 * across every instance, delivers locally, and handoffs to remote instances.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationRealtimeDeliveryService {

    private final RealtimeSessionRegistry sessionRegistry;
    private final RealtimeWebSocketSessionStore webSocketSessionStore;
    private final EdgeCrossInstanceDispatchCoordinator dispatchCoordinator;
    private final ObjectMapper objectMapper;
    private final WebSocketOutboundDeliveryQueue outboundDeliveryQueue;

    public int deliverToUserWithHandoff(UUID userId, String wsEventType, String eventId, Object payload) {
        String notificationChannel = "notification:" + userId;
        var allSessions = sessionRegistry.findByUserId(userId)
                .stream()
                .filter(s -> s.isSubscribedTo(notificationChannel))
                .collect(Collectors.toList());

        var split = dispatchCoordinator.splitByOwnership(allSessions);
        int delivered = deliverLocal(split.localOwned(), wsEventType, eventId, payload, userId);

        dispatchCoordinator.publishRemoteHandoffs(
                split, "notification", wsEventType, eventId, notificationChannel, userId, payload);

        log.debug("[NOTI-DELIVERY] wsEventType={} eventId={} localDelivered={} remoteInstances={} user={}",
                wsEventType, eventId, delivered, split.remoteByInstance().size(), userId);
        return delivered;
    }

    private int deliverLocal(Collection<RealtimeSession> sessions, String eventType,
                             String eventId, Object payload, UUID userId) {
        int delivered = 0;
        for (RealtimeSession session : sessions) {
            WebSocketSession webSocketSession = webSocketSessionStore
                    .findBySessionId(session.getSessionId())
                    .orElse(null);
            if (webSocketSession == null || !webSocketSession.isOpen()) {
                continue;
            }

            Map<String, Object> message = new LinkedHashMap<>();
            message.put("type", eventType);
            message.put("payload", payload);
            message.put("eventId", eventId);

            try {
                String json = objectMapper.writeValueAsString(message);
                if (outboundDeliveryQueue.enqueue(session.getSessionId(), webSocketSession, json, "notification")) {
                    delivered++;
                }
            } catch (Exception ex) {
                log.warn("[NOTI-DELIVERY][LOCAL-FAIL] user={} sessionId={} eventType={}",
                        userId, session.getSessionId(), eventType, ex);
            }
        }
        return delivered;
    }
}
