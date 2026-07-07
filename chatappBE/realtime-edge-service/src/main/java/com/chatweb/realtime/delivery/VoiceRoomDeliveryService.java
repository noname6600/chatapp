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

/**
 * Delivers voice room events to all WebSocket sessions subscribed to a chat room channel.
 * Reuses the existing room channel subscription — any user who has that room open already
 * receives voice participant updates without any extra subscription.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VoiceRoomDeliveryService {

    private final RealtimeSessionRegistry sessionRegistry;
    private final RealtimeWebSocketSessionStore webSocketSessionStore;
    private final EdgeCrossInstanceDispatchCoordinator dispatchCoordinator;
    private final ObjectMapper objectMapper;
    private final WebSocketOutboundDeliveryQueue outboundDeliveryQueue;

    public int deliverToRoom(String chatRoomId, String eventType, String eventId, Object payload) {
        String roomChannel = "room:" + chatRoomId;
        Collection<RealtimeSession> allSessions = sessionRegistry.findByChannel(roomChannel);
        var split = dispatchCoordinator.splitByOwnership(allSessions);

        int delivered = deliverLocal(split.localOwned(), eventType, eventId, payload, chatRoomId);

        dispatchCoordinator.publishRemoteHandoffs(
                split, "voice", eventType, eventId, roomChannel, null, payload);

        log.debug("[VOICE-DELIVERY] eventType={} chatRoomId={} localDelivered={} remoteInstances={}",
                eventType, chatRoomId, delivered, split.remoteByInstance().size());
        return delivered;
    }

    private int deliverLocal(Collection<RealtimeSession> sessions, String eventType,
                             String eventId, Object payload, String chatRoomId) {
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
                if (outboundDeliveryQueue.enqueue(session.getSessionId(), webSocketSession, json, "voice")) {
                    delivered++;
                }
            } catch (Exception ex) {
                log.warn("[VOICE-DELIVERY][LOCAL-FAIL] eventType={} chatRoomId={} sessionId={}",
                        eventType, chatRoomId, session.getSessionId(), ex);
            }
        }
        return delivered;
    }
}
