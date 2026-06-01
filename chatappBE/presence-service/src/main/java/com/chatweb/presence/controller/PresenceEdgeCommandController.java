package com.chatweb.presence.controller;

import com.chatweb.common.integration.presence.PresenceStopTypingPayload;
import com.chatweb.common.integration.presence.PresenceTypingPayload;
import com.chatweb.common.realtime.policy.RealtimeFlowId;
import com.chatweb.common.security.jwt.JwtHelper;
import com.chatweb.common.integration.presence.PresenceEventType;
import com.chatweb.presence.authorization.RoomAuthorizationService;
import com.chatweb.presence.realtime.port.PresenceRealtimePort;
import com.chatweb.presence.service.IPresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Edge command controller that receives presence lifecycle and command signals
 * forwarded by realtime-edge-service via HTTP.
 *
 * <p>This controller is the inbound boundary for the Phase C presence migration.
 * All calls arrive from realtime-edge-service carrying the client's JWT. The JWT is
 * validated by Spring Security (oauth2ResourceServer) and the userId is extracted from it.
 *
 * <p>Endpoints mirror the legacy presence WebSocket command surface so
 * edge-proxied calls drive identical domain state transitions.
 *
 * <p>Path: {@code /api/v1/presence/ws/**}
 *
 * @since Phase C
 */
@RestController
@RequestMapping("/api/v1/presence/ws")
@RequiredArgsConstructor
@Slf4j
public class PresenceEdgeCommandController {

    private final IPresenceService presenceService;
    private final PresenceRealtimePort presenceRealtimePort;
    private final RoomAuthorizationService roomAuthorizationService;

    /**
     * Signals that the client has connected via realtime-edge-service.
     * Drives the same domain transitions as WebSocket afterConnectionEstablished.
     */
    @PostMapping("/connect")
    public ResponseEntity<Void> connect(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = extractUserId(jwt);
        if (userId == null) {
            return ResponseEntity.badRequest().build();
        }
        presenceService.online(userId);
        presenceService.heartbeat(userId, true);
        log.debug("[EDGE-CMD] User connected via edge: userId={}", userId);
        return ResponseEntity.ok().build();
    }

    /**
     * Signals that the client has disconnected from realtime-edge-service.
     * Called only when the user's last presence session on the edge closes.
     * Uses handleUserOfflineByTTL to clear any stale connection counts and
     * guarantee the USER_OFFLINE event is always published on clean logout.
     */
    @PostMapping("/disconnect")
    public ResponseEntity<Void> disconnect(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = extractUserId(jwt);
        if (userId == null) {
            return ResponseEntity.badRequest().build();
        }
        presenceService.handleUserOfflineByTTL(userId);
        log.debug("[EDGE-CMD] User disconnected via edge: userId={}", userId);
        return ResponseEntity.ok().build();
    }

    /**
     * Signals a heartbeat from the client.
     * Request body: {@code { "active": true|false }}
     */
    @PostMapping("/heartbeat")
    public ResponseEntity<Void> heartbeat(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) Map<String, Object> body) {
        UUID userId = extractUserId(jwt);
        if (userId == null) {
            return ResponseEntity.badRequest().build();
        }
        boolean active = body == null || !Boolean.FALSE.equals(body.get("active"));
        presenceService.heartbeat(userId, active);
        log.debug("[EDGE-CMD] Heartbeat from edge: userId={} active={}", userId, active);
        return ResponseEntity.ok().build();
    }

    /**
     * Signals that the client has joined a room.
     */
    @PostMapping("/rooms/{roomId}/join")
    public ResponseEntity<Void> joinRoom(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID roomId) {
        UUID userId = extractUserId(jwt);
        if (userId == null) {
            return ResponseEntity.badRequest().build();
        }
        roomAuthorizationService.ensureRoomMember(roomId, jwt.getTokenValue());
        presenceService.joinRoom(roomId, userId);
        log.debug("[EDGE-CMD] Room join via edge: userId={} roomId={}", userId, roomId);
        return ResponseEntity.ok().build();
    }

    /**
     * Signals that the client has left a room.
     */
    @PostMapping("/rooms/{roomId}/leave")
    public ResponseEntity<Void> leaveRoom(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID roomId) {
        UUID userId = extractUserId(jwt);
        if (userId == null) {
            return ResponseEntity.badRequest().build();
        }
        roomAuthorizationService.ensureRoomMember(roomId, jwt.getTokenValue());
        presenceService.leaveRoom(roomId, userId);
        log.debug("[EDGE-CMD] Room leave via edge: userId={} roomId={}", userId, roomId);
        return ResponseEntity.ok().build();
    }

    /**
     * Publishes a typing event for the user in the specified room.
     * Authorization is enforced by the realtime-edge-service: it only forwards this
     * command when the session is already subscribed to the room's presence channel,
     * which was set during the authorized presence.room.join handshake.
     * A redundant ensureRoomMember HTTP call here would add latency and a silent
     * failure point (typing dropped whenever the chat service is momentarily unreachable).
     */
    @PostMapping("/rooms/{roomId}/typing")
    public ResponseEntity<Void> typing(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID roomId) {
        UUID userId = extractUserId(jwt);
        if (userId == null) {
            return ResponseEntity.badRequest().build();
        }
        presenceRealtimePort.publishRoomEvent(
                roomId,
                PresenceEventType.ROOM_TYPING.value(),
                PresenceTypingPayload.builder()
                        .userId(userId)
                        .roomId(roomId)
                        .build(),
                RealtimeFlowId.PRESENCE_USER_TYPING
        );
        log.debug("[EDGE-CMD] Typing event via edge: userId={} roomId={}", userId, roomId);
        return ResponseEntity.ok().build();
    }

    /**
     * Publishes a stop-typing event for the user in the specified room.
     * Same trust model as typing() — no redundant auth call.
     */
    @PostMapping("/rooms/{roomId}/stop-typing")
    public ResponseEntity<Void> stopTyping(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID roomId) {
        UUID userId = extractUserId(jwt);
        if (userId == null) {
            return ResponseEntity.badRequest().build();
        }
        presenceRealtimePort.publishRoomEvent(
                roomId,
                PresenceEventType.ROOM_STOP_TYPING.value(),
                PresenceStopTypingPayload.builder()
                        .userId(userId)
                        .roomId(roomId)
                        .build(),
                RealtimeFlowId.PRESENCE_USER_STOP_TYPING
        );
        log.debug("[EDGE-CMD] Stop-typing event via edge: userId={} roomId={}", userId, roomId);
        return ResponseEntity.ok().build();
    }

    private UUID extractUserId(Jwt jwt) {
        return JwtHelper.extractUserId(jwt).orElse(null);
    }
}
