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
 * Delivers chat room events to active websocket sessions subscribed to room channels.
 *
 * deliverRoomWithHandoff — Kafka path (primary). Finds ALL sessions for the room across
 * every instance, delivers locally, and handoffs to remote instances. Used by
 * FanoutChatEventConsumer; gives durable, retry-safe multi-instance delivery.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatRealtimeDeliveryService {

    private final RealtimeSessionRegistry sessionRegistry;
    private final RealtimeWebSocketSessionStore webSocketSessionStore;
    private final EdgeCrossInstanceDispatchCoordinator dispatchCoordinator;
    private final ObjectMapper objectMapper;
    private final WebSocketOutboundDeliveryQueue outboundDeliveryQueue;

    public int deliverRoomWithHandoff(String roomId, String eventType, String eventId, Object payload) {
        String roomChannel = “room:” + roomId;
        Collection<RealtimeSession> allSessions = sessionRegistry.findByChannel(roomChannel);
        var split = dispatchCoordinator.splitByOwnership(allSessions);

        int delivered = deliverLocal(split.localOwned(), eventType, eventId, payload, roomId);

        dispatchCoordinator.publishRemoteHandoffs(
                split, “chat”, eventType, eventId, roomChannel, null, payload);

        log.debug(“[CHAT-DELIVERY] eventType={} roomId={} localDelivered={} remoteInstances={}”,
                eventType, roomId, delivered, split.remoteByInstance().size());
        return delivered;
    }

    private int deliverLocal(Collection<RealtimeSession> sessions, String eventType,
                             String eventId, Object payload, String roomId) {
        int delivered = 0;
        for (RealtimeSession session : sessions) {
            WebSocketSession webSocketSession = webSocketSessionStore
                    .findBySessionId(session.getSessionId())
                    .orElse(null);
            if (webSocketSession == null || !webSocketSession.isOpen()) {
                continue;
            }

            Map<String, Object> message = new LinkedHashMap<>();
            message.put(“type”, eventType);
            message.put(“payload”, payload);
            message.put(“eventId”, eventId);

            try {
                String json = objectMapper.writeValueAsString(message);
                if (outboundDeliveryQueue.enqueue(session.getSessionId(), webSocketSession, json, “chat”)) {
                    delivered++;
                }
            } catch (Exception ex) {
                log.warn(“[CHAT-DELIVERY][LOCAL-FAIL] eventType={} roomId={} sessionId={}”,
                        eventType, roomId, session.getSessionId(), ex);
            }
        }
        return delivered;
    }
}
