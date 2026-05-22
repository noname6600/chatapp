package com.chatweb.realtime.delivery;

import com.chatweb.realtime.connection.RealtimeSession;
import com.chatweb.realtime.connection.RealtimeSessionRegistry;
import com.chatweb.realtime.connection.RealtimeWebSocketSessionStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Delivers notification events to active websocket sessions for a target user.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationRealtimeDeliveryService {

    private final RealtimeSessionRegistry sessionRegistry;
    private final RealtimeWebSocketSessionStore webSocketSessionStore;
    private final ObjectMapper objectMapper;
    private final WebSocketOutboundDeliveryQueue outboundDeliveryQueue;

    public int deliverToUser(UUID userId, String eventType, String eventId, Object payload) {
        int delivered = 0;
        String notificationChannel = "notification:" + userId;
        // Redis pub/sub: every instance receives this event. Each delivers only to its own
        // locally-owned sessions to avoid duplicates with other instances.
        var localSessions = sessionRegistry.findByUserIdOwnedByCurrentInstance(userId)
                .stream()
                .filter(s -> s.isSubscribedTo(notificationChannel))
                .collect(Collectors.toList());

        for (RealtimeSession session : localSessions) {
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

        log.debug("[NOTI-DELIVERY] Delivered eventType={} eventId={} to {} sessions for user={}",
                eventType, eventId, delivered, userId);
        return delivered;
    }
}
