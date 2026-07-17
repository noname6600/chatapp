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
 * Delivers 1:1 call events (INITIATED, ACCEPTED, DECLINED, CANCELLED, ENDED, MISSED)
 * to specific user sessions via WebSocket, with cross-instance handoff support.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CallDeliveryService {

    private final RealtimeSessionRegistry sessionRegistry;
    private final RealtimeWebSocketSessionStore webSocketSessionStore;
    private final EdgeCrossInstanceDispatchCoordinator dispatchCoordinator;
    private final ObjectMapper objectMapper;
    private final WebSocketOutboundDeliveryQueue outboundDeliveryQueue;

    public int deliverToUser(UUID userId, String eventType, String eventId, Object payload) {
        String callChannel = "call:" + userId;
        var sessions = sessionRegistry.findByUserId(userId)
                .stream()
                .filter(s -> s.isSubscribedTo(callChannel))
                .collect(Collectors.toList());

        var split = dispatchCoordinator.splitByOwnership(sessions);
        int delivered = deliverLocal(split.localOwned(), eventType, eventId, payload, userId);

        dispatchCoordinator.publishRemoteHandoffs(
                split, "call", eventType, eventId, callChannel, userId, payload);

        log.debug("[CALL-DELIVERY] eventType={} userId={} localDelivered={} remoteInstances={}",
                eventType, userId, delivered, split.remoteByInstance().size());
        return delivered;
    }

    private int deliverLocal(Collection<RealtimeSession> sessions, String eventType,
                             String eventId, Object payload, UUID userId) {
        int delivered = 0;
        for (RealtimeSession session : sessions) {
            WebSocketSession webSocketSession = webSocketSessionStore
                    .findBySessionId(session.getSessionId())
                    .orElse(null);
            if (webSocketSession == null || !webSocketSession.isOpen()) continue;

            Map<String, Object> message = new LinkedHashMap<>();
            message.put("type", eventType);
            message.put("payload", payload);
            message.put("eventId", eventId);

            try {
                String json = objectMapper.writeValueAsString(message);
                if (outboundDeliveryQueue.enqueue(session.getSessionId(), webSocketSession, json, "call")) {
                    delivered++;
                }
            } catch (Exception ex) {
                log.warn("[CALL-DELIVERY][LOCAL-FAIL] user={} sessionId={} eventType={}",
                        userId, session.getSessionId(), eventType, ex);
            }
        }
        return delivered;
    }
}
