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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Delivers friendship events to active websocket sessions for a target user.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FriendshipRealtimeDeliveryService {

    private final RealtimeSessionRegistry sessionRegistry;
    private final RealtimeWebSocketSessionStore webSocketSessionStore;
    private final EdgeCrossInstanceDispatchCoordinator dispatchCoordinator;
    private final ObjectMapper objectMapper;
    private final WebSocketOutboundDeliveryQueue outboundDeliveryQueue;

    public int deliverToUser(UUID userId, String wsType, String eventId, Object payload) {
        int delivered = 0;
        String friendshipChannel = "friendship:" + userId;
        var ownershipSplit = dispatchCoordinator.splitByOwnership(
                sessionRegistry.findByUserId(userId)
                        .stream()
                        .filter(s -> s.isSubscribedTo(friendshipChannel))
                        .collect(Collectors.toList())
        );

        for (RealtimeSession session : ownershipSplit.localOwned()) {
            WebSocketSession webSocketSession = webSocketSessionStore
                    .findBySessionId(session.getSessionId())
                    .orElse(null);
            if (webSocketSession == null || !webSocketSession.isOpen()) {
                continue;
            }

            Map<String, Object> message = new LinkedHashMap<>();
            message.put("type", wsType);
            message.put("payload", payload);
            message.put("eventId", eventId);

            try {
                String json = objectMapper.writeValueAsString(message);
                if (outboundDeliveryQueue.enqueue(session.getSessionId(), webSocketSession, json, "friendship")) {
                    delivered++;
                }
            } catch (Exception ex) {
                log.warn("[FRIEND-DELIVERY][LOCAL-FAIL] user={} sessionId={} wsType={}",
                        userId, session.getSessionId(), wsType, ex);
            }
        }

            dispatchCoordinator.publishRemoteHandoffs(
                ownershipSplit,
                "friendship",
                wsType,
                eventId,
                friendshipChannel,
                userId,
                payload
            );

        log.debug("[FRIEND-DELIVERY] Delivered wsType={} eventId={} to {} sessions for user={}",
                wsType, eventId, delivered, userId);
        return delivered;
    }
}
