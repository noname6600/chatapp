package com.chatweb.realtime.adapter.out.presence;

import com.chatweb.realtime.connection.RealtimeSession;
import com.chatweb.realtime.connection.RealtimeSessionRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Bridges edge WebSocket lifecycle events to the presence-service domain layer via HTTP.
 *
 * <p>Separates presence lifecycle signalling (connect, disconnect) and session-level
 * initial state delivery (global snapshot) from the generic WebSocket handler.
 *
 * <p>Connect/disconnect decisions:
 * <ul>
 *   <li>{@code onPresenceConnected} â€” always signals presence-service that the user is online.</li>
 *   <li>{@code onPresenceDisconnected} â€” signals offline only when no other edge sessions for this
 *       user remain subscribed to {@code "presence:global"}.</li>
 * </ul>
 *
 * @since Phase C
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EdgePresenceLifecycleBridge {

    private static final String PRESENCE_GLOBAL_CHANNEL = "presence:global";

    private final PresenceDomainClient presenceDomainClient;
    private final RealtimeSessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;

    /**
     * Called immediately after a client connects on {@code /ws/presence}.
     *
     * <p>Subscribes the session to the global presence channel, signals online to
     * presence-service, and pushes the current global snapshot to the client.
     *
     * @param realtimeSession the edge session
     * @param wsSession       the raw WebSocket session (used for sending the snapshot)
     * @param accessToken     the client's JWT (forwarded as Bearer to presence-service)
     */
    public void onPresenceConnected(RealtimeSession realtimeSession,
                                     WebSocketSession wsSession,
                                     String accessToken) {
        UUID userId = realtimeSession.getUserId();

        presenceDomainClient.connect(accessToken);

        try {
            JsonNode users = presenceDomainClient.globalSnapshot(accessToken);
            sendGlobalSnapshot(wsSession, users);
            log.debug("[PRESENCE-BRIDGE] Global snapshot sent on connect: userId={}", userId);
        } catch (Exception ex) {
            log.warn("[PRESENCE-BRIDGE] Failed to send global snapshot on connect: userId={}", userId, ex);
        }
    }

    private void sendGlobalSnapshot(WebSocketSession wsSession, JsonNode users) throws Exception {
        Map<String, Object> snapshotPayload = new LinkedHashMap<>();
        snapshotPayload.put("users", users);

        Map<String, Object> snapshotMessage = new LinkedHashMap<>();
        snapshotMessage.put("type", "presence.global.snapshot");
        snapshotMessage.put("payload", snapshotPayload);

        wsSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(snapshotMessage)));
    }

    /**
     * Called when a client session disconnects from {@code /ws/presence}.
     *
     * <p>Signals offline to presence-service only if this was the user's last presence session
     * on this edge instance. This mirrors the logic in {@code PresenceConnectionLifecycleAdapter}.
     *
     * @param realtimeSession the closing edge session
     * @param accessToken     the client's JWT
     */
    public void onPresenceDisconnected(RealtimeSession realtimeSession, String accessToken) {
        UUID userId = realtimeSession.getUserId();

        boolean hasOtherPresenceSessions = sessionRegistry.findByUserId(userId)
                .stream()
                .anyMatch(s -> s.isSubscribedTo(PRESENCE_GLOBAL_CHANNEL));

        if (!hasOtherPresenceSessions) {
            presenceDomainClient.disconnect(accessToken);
            log.debug("[PRESENCE-BRIDGE] Offline signal sent (last session): userId={}", userId);
        } else {
            log.debug("[PRESENCE-BRIDGE] Disconnect suppressed (other sessions exist): userId={}", userId);
        }
    }
}
