package com.chatweb.realtime.delivery;

import com.chatweb.realtime.connection.RealtimeSession;
import com.chatweb.realtime.connection.RealtimeSessionRegistry;
import com.chatweb.realtime.connection.RealtimeWebSocketSessionStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Delivers presence events to active websocket sessions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PresenceRealtimeDeliveryService {

    private static final String PRESENCE_GLOBAL_CHANNEL = "presence:global";

    private final RealtimeSessionRegistry sessionRegistry;
    private final RealtimeWebSocketSessionStore webSocketSessionStore;
    private final ObjectMapper objectMapper;
    private final WebSocketOutboundDeliveryQueue outboundDeliveryQueue;

    public int deliverGlobal(String eventType, String eventId, Object payload) {
        // Redis pub/sub: all instances receive this event. Each delivers only its locally-owned
        // sessions â€" no handoff needed, other instances handle their own clients.
        return deliverToSessions(
                sessionRegistry.findByChannelOwnedByCurrentInstance(PRESENCE_GLOBAL_CHANNEL),
                eventType, eventId, payload);
    }

    public int deliverRoom(String roomId, String eventType, String eventId, Object payload) {
        String presenceRoomChannel = "presence:" + roomId;
        String typingRoomChannel = "typing:" + roomId;
        String targetChannel = isTypingEvent(eventType) ? typingRoomChannel : presenceRoomChannel;
        return deliverToSessions(
                sessionRegistry.findByChannelOwnedByCurrentInstance(targetChannel),
                eventType, eventId, payload);
    }

    private int deliverToSessions(Collection<RealtimeSession> sessions, String eventType, String eventId, Object payload) {
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
                if (outboundDeliveryQueue.enqueue(session.getSessionId(), webSocketSession, json, "presence")) {
                    delivered++;
                }
            } catch (Exception ex) {
                log.warn("[PRESENCE-DELIVERY][LOCAL-FAIL] eventType={} sessionId={}",
                        eventType, session.getSessionId(), ex);
            }
        }

        return delivered;
    }

    private boolean isTypingEvent(String eventType) {
        return "presence.room.typing".equals(eventType) || "presence.room.stop-typing".equals(eventType);
    }
}
