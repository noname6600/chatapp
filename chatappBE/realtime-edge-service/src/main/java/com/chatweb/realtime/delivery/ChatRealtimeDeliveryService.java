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

/**
 * Delivers chat room events to active websocket sessions subscribed to room channels.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatRealtimeDeliveryService {

    private final RealtimeSessionRegistry sessionRegistry;
    private final RealtimeWebSocketSessionStore webSocketSessionStore;
    private final ObjectMapper objectMapper;
    private final WebSocketOutboundDeliveryQueue outboundDeliveryQueue;

    public int deliverRoom(String roomId, String eventType, String eventId, Object payload) {
        int delivered = 0;
        String roomChannel = "room:" + roomId;
        // Redis pub/sub broadcasts to every instance simultaneously â€” each instance delivers
        // only its own locally-owned sessions. No cross-instance handoff needed or wanted;
        // enabling it would cause duplicate delivery since all instances receive the same event.
        var localSessions = sessionRegistry.findByChannelOwnedByCurrentInstance(roomChannel);

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
                if (outboundDeliveryQueue.enqueue(session.getSessionId(), webSocketSession, json, "chat")) {
                    delivered++;
                }
            } catch (Exception ex) {
                log.warn("[CHAT-DELIVERY][LOCAL-FAIL] eventType={} roomId={} sessionId={}",
                        eventType, roomId, session.getSessionId(), ex);
            }
        }

        return delivered;
    }
}
