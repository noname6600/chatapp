package com.chatweb.realtime.adapter.in.websocket;

import com.chatweb.realtime.adapter.out.presence.EdgePresenceLifecycleBridge;
import com.chatweb.realtime.adapter.out.presence.PresenceDomainClient;
import com.chatweb.realtime.connection.RealtimeSession;
import com.chatweb.realtime.connection.RealtimeSessionRegistry;
import com.chatweb.realtime.connection.RealtimeWebSocketSessionStore;
import com.chatweb.realtime.delivery.WebSocketOutboundDeliveryQueue;
import com.chatweb.realtime.protocol.RealtimeClientMessage;
import com.chatweb.realtime.routing.command.IChatCommandRouter;
import com.chatweb.realtime.routing.CommandDispatcher;
import com.chatweb.realtime.subscription.ChannelSubscriptionManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Websocket handler for the realtime edge ingress.
 *
 * Manages connection lifecycle, receives client messages, and routes to managers.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RealtimeWebSocketHandler extends TextWebSocketHandler {

    private static final String SESSION_TOKEN_PREFIX = "ws:session-token:";

    private final RealtimeSessionRegistry sessionRegistry;
    private final RealtimeWebSocketSessionStore webSocketSessionStore;
    private final ChannelSubscriptionManager subscriptionManager;
    private final PresenceDomainClient presenceDomainClient;
    private final EdgePresenceLifecycleBridge presenceLifecycleBridge;
    private final IChatCommandRouter chatCommandRouter;
    private final CommandDispatcher commandDispatcher;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final RealtimeSideEffectQueue sideEffectQueue;
    private final WebSocketOutboundDeliveryQueue outboundDeliveryQueue;

    /**
     * Placeholder: future implementation will extract userId from handshake attributes
     * set by the JwtHandshakeInterceptor.
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // Extract userId from websocket session attributes (set by auth interceptor)
        UUID userId = (UUID) session.getAttributes().get("userId");
        if (userId == null) {
            log.warn("[WS] No userId in session attributes, closing connection");
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        RealtimeSession realtimeSession = RealtimeSession.create(userId);
        sessionRegistry.register(realtimeSession);
        webSocketSessionStore.register(realtimeSession.getSessionId(), session);

        session.getAttributes().put("realtimeEndpoint", "/realtime");
        subscribeSession(realtimeSession, "notification:" + userId);
        subscribeSession(realtimeSession, "friendship:" + userId);
        subscribeSession(realtimeSession, "presence:global");

        String accessToken = requireActiveAccessToken(session, realtimeSession, "connect");
        if (accessToken == null) {
            return;
        }

        if (accessToken != null && !accessToken.isBlank()) {
            sideEffectQueue.submit("presence.connect", () ->
                    presenceLifecycleBridge.onPresenceConnected(realtimeSession, session, accessToken)
            );
        }
        session.getAttributes().put("realtimeSession", realtimeSession);

        log.info("[WS] Connection established: sessionId={} userId={}", 
                realtimeSession.getSessionId(), userId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            RealtimeSession realtimeSession = (RealtimeSession) session.getAttributes().get("realtimeSession");
            if (realtimeSession == null) {
                log.warn("[WS] No realtime session found, closing connection");
                session.close(CloseStatus.POLICY_VIOLATION);
                return;
            }

            if (requireActiveAccessToken(session, realtimeSession, "message") == null) {
                return;
            }

            JsonNode root = objectMapper.readTree(message.getPayload());
            String type = root.path("type").asText(null);

            if (type != null && type.startsWith("presence.")) {
                if (handlePresenceMessage(session, realtimeSession, message.getPayload())) {
                    realtimeSession.updateActivity();
                    return;
                }
            }

            if (type != null && isChatMessageType(type)) {
                if (handleChatMessage(session, realtimeSession, message.getPayload())) {
                    realtimeSession.updateActivity();
                    return;
                }
            }

            RealtimeClientMessage clientMessage = objectMapper.readValue(
                    root.toString(), RealtimeClientMessage.class);

            handleClientMessage(realtimeSession, clientMessage, session);
            realtimeSession.updateActivity();
            sessionRegistry.refreshSessionLease(realtimeSession.getSessionId());
        } catch (Exception ex) {
            log.error("[WS] Error handling message", ex);
            session.sendMessage(new TextMessage("{\"type\":\"error\",\"message\":\"Invalid message format\"}"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        RealtimeSession realtimeSession = (RealtimeSession) session.getAttributes().get("realtimeSession");
        if (realtimeSession != null) {
            sessionRegistry.unregister(realtimeSession.getSessionId());
            webSocketSessionStore.unregister(realtimeSession.getSessionId());

            String accessToken = resolveAccessToken(session);
            if (accessToken != null && !accessToken.isBlank()) {
                sideEffectQueue.submit("presence.disconnect", () ->
                        presenceLifecycleBridge.onPresenceDisconnected(realtimeSession, accessToken)
                );
            }

            outboundDeliveryQueue.clearSession(realtimeSession.getSessionId());
            clearAccessToken(session);

            log.info("[WS] Connection closed: sessionId={} userId={} status={}", 
                    realtimeSession.getSessionId(), realtimeSession.getUserId(), status);
        }
    }

    private boolean handlePresenceMessage(WebSocketSession session, RealtimeSession realtimeSession, String rawPayload) {
        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            String type = root.path("type").asText(null);
            if (type == null || type.isBlank()) {
                return false;
            }

            String accessToken = resolveAccessToken(session);
            if (accessToken == null || accessToken.isBlank()) {
                sendError(session, "Missing access token for presence command");
                return true;
            }

            if ("presence.user.heartbeat".equals(type)) {
                boolean active = !root.has("active") || root.path("active").asBoolean(true);
                sideEffectQueue.submit("presence.heartbeat", () ->
                        presenceDomainClient.heartbeat(accessToken, active)
                );
                return true;
            }

            UUID roomId = parseUUID(root.path("roomId").asText(null));
            if (roomId == null) {
                return true;
            }

            String presenceChannel = "presence:" + roomId;
            String typingChannel = "typing:" + roomId;

            switch (type) {
                case "presence.room.join" -> {
                    if (!isAuthorizedRoomChannel(realtimeSession, accessToken, presenceChannel)
                            || !isAuthorizedRoomChannel(realtimeSession, accessToken, typingChannel)) {
                        sendError(session, "Not authorized to subscribe this room presence");
                        return true;
                    }

                    try {
                        subscribeSession(realtimeSession, presenceChannel);
                        subscribeSession(realtimeSession, typingChannel);
                        sideEffectQueue.submit("presence.room.join", () -> {
                            try {
                                presenceDomainClient.joinRoom(accessToken, roomId);

                                JsonNode users = presenceDomainClient.roomSnapshot(accessToken, roomId);
                                Map<String, Object> payload = new LinkedHashMap<>();
                                payload.put("roomId", roomId);
                                payload.put("users", users);

                                Map<String, Object> response = new LinkedHashMap<>();
                                response.put("type", "presence.room.snapshot");
                                response.put("payload", payload);
                                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
                            } catch (Exception ex) {
                                unsubscribeSession(realtimeSession, presenceChannel);
                                unsubscribeSession(realtimeSession, typingChannel);
                                log.warn("[PRESENCE] presence.room.join failed userId={} roomId={}",
                                        realtimeSession.getUserId(), roomId, ex);
                                try {
                                    sendError(session, "Failed to join room presence");
                                } catch (Exception sendEx) {
                                    log.debug("[PRESENCE] failed to send join failure frame", sendEx);
                                }
                            }
                        });
                    } catch (Exception ex) {
                        unsubscribeSession(realtimeSession, presenceChannel);
                        unsubscribeSession(realtimeSession, typingChannel);
                        log.warn("[PRESENCE] presence.room.join failed userId={} roomId={}",
                                realtimeSession.getUserId(), roomId, ex);
                        sendError(session, "Failed to join room presence");
                    }
                    return true;
                }
                case "presence.room.leave" -> {
                    unsubscribeSession(realtimeSession, presenceChannel);
                    unsubscribeSession(realtimeSession, typingChannel);
                    sideEffectQueue.submit("presence.room.leave", () ->
                            presenceDomainClient.leaveRoom(accessToken, roomId)
                    );
                    return true;
                }
                case "presence.room.typing" -> {
                    if (realtimeSession.isSubscribedTo(presenceChannel)) {
                        sideEffectQueue.submit("presence.room.typing", () ->
                                presenceDomainClient.typing(accessToken, roomId)
                        );
                    }
                    return true;
                }
                case "presence.room.stop_typing" -> {
                    if (realtimeSession.isSubscribedTo(presenceChannel)) {
                        sideEffectQueue.submit("presence.room.stop_typing", () ->
                                presenceDomainClient.stopTyping(accessToken, roomId)
                        );
                    }
                    return true;
                }
                default -> {
                    return false;
                }
            }
        } catch (Exception ex) {
            log.warn("[PRESENCE] Failed to handle presence message payload={}", rawPayload, ex);
            return true;
        }
    }

    private UUID parseUUID(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean handleChatMessage(WebSocketSession session, RealtimeSession realtimeSession, String rawPayload) {
        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            String type = root.path("type").asText(null);
            if (type == null || type.isBlank()) {
                return false;
            }

            String accessToken = resolveAccessToken(session);
            if (accessToken == null || accessToken.isBlank()) {
                sendError(session, "Missing access token for chat command");
                return true;
            }

            String roomId = root.path("roomId").asText(null);
            if ("JOIN".equalsIgnoreCase(type) && roomId != null && !roomId.isBlank()) {
                UUID parsedRoomId = parseUUID(roomId);
                if (parsedRoomId == null) {
                    sendError(session, "Invalid roomId for JOIN");
                    return true;
                }
                if (!isAuthorizedRoomChannel(realtimeSession, accessToken, "room:" + parsedRoomId)) {
                    sendError(session, "Not authorized to subscribe this room");
                    return true;
                }
                subscribeSession(realtimeSession, "room:" + parsedRoomId);
                return true;
            }

            if ("LEAVE".equalsIgnoreCase(type) && roomId != null && !roomId.isBlank()) {
                UUID parsedRoomId = parseUUID(roomId);
                if (parsedRoomId == null) {
                    sendError(session, "Invalid roomId for LEAVE");
                    return true;
                }
                chatCommandRouter.leaveRoom(accessToken, parsedRoomId);
                unsubscribeSession(realtimeSession, "room:" + parsedRoomId);
                return true;
            }

            if ("PING".equalsIgnoreCase(type)) {
                return true;
            }

            if ("SEND".equalsIgnoreCase(type)) {
                UUID parsedRoomId = parseUUID(roomId);
                if (parsedRoomId == null) {
                    sendError(session, "Missing roomId for SEND");
                    return true;
                }
                chatCommandRouter.sendMessage(accessToken, extractPayload(root));
                log.info("[CHAT] forwarded send session={} roomId={}", session.getId(), parsedRoomId);
                return true;
            }

            if ("EDIT".equalsIgnoreCase(type)) {
                UUID messageId = parseUUID(root.path("messageId").asText(null));
                if (messageId == null) {
                    sendError(session, "Missing messageId for EDIT");
                    return true;
                }
                chatCommandRouter.editMessage(accessToken, messageId, extractPayload(root));
                log.info("[CHAT] forwarded edit session={} messageId={}", session.getId(), messageId);
                return true;
            }

            if ("DELETE".equalsIgnoreCase(type)) {
                UUID messageId = parseUUID(root.path("messageId").asText(null));
                if (messageId == null) {
                    sendError(session, "Missing messageId for DELETE");
                    return true;
                }
                chatCommandRouter.deleteMessage(accessToken, messageId, extractPayload(root));
                log.info("[CHAT] forwarded delete session={} messageId={}", session.getId(), messageId);
                return true;
            }

            if ("REACTION".equalsIgnoreCase(type)) {
                UUID messageId = parseUUID(root.path("messageId").asText(null));
                String reaction = root.path("reaction").asText(null);
                if (messageId == null || reaction == null || reaction.isBlank()) {
                    sendError(session, "Missing messageId or reaction for REACTION");
                    return true;
                }
                chatCommandRouter.toggleReaction(accessToken, messageId, reaction);
                log.info("[CHAT] forwarded reaction session={} messageId={} reaction={}", session.getId(), messageId, reaction);
                return true;
            }

            if ("PIN".equalsIgnoreCase(type)) {
                UUID parsedRoomId = parseUUID(roomId);
                UUID messageId = parseUUID(root.path("messageId").asText(null));
                if (parsedRoomId == null || messageId == null) {
                    sendError(session, "Missing roomId or messageId for PIN");
                    return true;
                }
                chatCommandRouter.pinMessage(accessToken, parsedRoomId, messageId);
                log.info("[CHAT] forwarded pin session={} roomId={} messageId={}", session.getId(), parsedRoomId, messageId);
                return true;
            }

            if ("UNPIN".equalsIgnoreCase(type)) {
                UUID parsedRoomId = parseUUID(roomId);
                UUID messageId = parseUUID(root.path("messageId").asText(null));
                if (parsedRoomId == null || messageId == null) {
                    sendError(session, "Missing roomId or messageId for UNPIN");
                    return true;
                }
                chatCommandRouter.unpinMessage(accessToken, parsedRoomId, messageId);
                log.info("[CHAT] forwarded unpin session={} roomId={} messageId={}", session.getId(), parsedRoomId, messageId);
                return true;
            }

            return false;
        } catch (Exception ex) {
            log.warn("[CHAT] Failed to handle chat message payload={}", rawPayload, ex);
            try {
                sendError(session, "Failed to process chat command");
            } catch (Exception sendError) {
                log.debug("[CHAT] failed to send error response", sendError);
            }
            return true;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractPayload(JsonNode root) {
        Map<String, Object> payload = objectMapper.convertValue(root, LinkedHashMap.class);
        payload.remove("type");
        payload.remove("domain");
        payload.remove("command");
        return payload;
    }

    private void sendError(WebSocketSession session, String message) throws IOException {
        sendError(session, "INVALID_FRAME", message);
    }

    private void sendError(WebSocketSession session, String code, String message) throws IOException {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("type", "error");
        response.put("code", code);
        response.put("message", message);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    private boolean isAuthorizedRoomChannel(RealtimeSession session, String accessToken, String channel) {
        return subscriptionManager.isAuthorized(session, channel, accessToken);
    }

    private boolean isChatMessageType(String type) {
        return "JOIN".equalsIgnoreCase(type)
                || "LEAVE".equalsIgnoreCase(type)
                || "PING".equalsIgnoreCase(type)
                || "SEND".equalsIgnoreCase(type)
                || "EDIT".equalsIgnoreCase(type)
                || "DELETE".equalsIgnoreCase(type)
                || "REACTION".equalsIgnoreCase(type)
                || "PIN".equalsIgnoreCase(type)
                || "UNPIN".equalsIgnoreCase(type);
    }

    /**
     * Routes client messages to appropriate handlers.
     */
    private void handleClientMessage(RealtimeSession session, RealtimeClientMessage message, 
                                      WebSocketSession wsSession) throws IOException {
        if (message == null || message.getType() == null) {
            log.warn("[MSG] Null message or type");
            return;
        }

        String accessToken = resolveAccessToken(wsSession);
        try {
            if (commandDispatcher.dispatch(session, accessToken, message)) {
                return;
            }
        } catch (Exception ex) {
            log.warn("[MSG] Failed to dispatch command type={} requestId={}", message.getType(), message.getRequestId(), ex);
            sendError(wsSession, "COMMAND_FAILED", "Could not process command: " + message.getType());
            return;
        }

        switch (message.getType()) {
            case "subscribe" -> handleSubscribe(session, message, wsSession, accessToken);
            case "unsubscribe" -> handleUnsubscribe(session, message, wsSession);
            case "ping" -> handlePing(wsSession);
            default -> log.warn("[MSG] Unknown message type: {}", message.getType());
        }
    }

    private void handleSubscribe(RealtimeSession session, RealtimeClientMessage message, 
                                 WebSocketSession wsSession, String accessToken) throws IOException {
        if (message.getChannels() == null || message.getChannels().isEmpty()) {
            log.warn("[SUB] No channels specified");
            return;
        }

        int authorized = 0;
        int denied = 0;

        for (String channel : message.getChannels()) {
            if (!subscriptionManager.isValidChannelFormat(channel)) {
                log.warn("[SUB] Invalid channel format: {}", channel);
                denied++;
                continue;
            }

            if (!subscriptionManager.isAuthorized(session, channel, accessToken)) {
                log.warn("[SUB] Not authorized for channel: {} userId={}", channel, session.getUserId());
                denied++;
                continue;
            }

            subscribeSession(session, channel);
            authorized++;
        }

        String responseMsg = String.format("Subscribed to %d channels, %d denied", authorized, denied);
        wsSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                com.chatweb.realtime.protocol.RealtimeServerMessage.subscribeAck(message.getRequestId(), denied == 0, responseMsg)
        )));
    }

    private void handleUnsubscribe(RealtimeSession session, RealtimeClientMessage message, 
                                   WebSocketSession wsSession) throws IOException {
        if (message.getChannels() == null || message.getChannels().isEmpty()) {
            log.warn("[UNSUB] No channels specified");
            return;
        }

        for (String channel : message.getChannels()) {
            unsubscribeSession(session, channel);
        }
        wsSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                com.chatweb.realtime.protocol.RealtimeServerMessage.unsubscribeAck(message.getRequestId(), true, "Unsubscribed")
        )));
    }

    private void subscribeSession(RealtimeSession session, String channel) {
        session.subscribe(channel);
        sessionRegistry.addSubscription(session.getSessionId(), channel);
    }

    private void unsubscribeSession(RealtimeSession session, String channel) {
        session.unsubscribe(channel);
        sessionRegistry.removeSubscription(session.getSessionId(), channel);
    }

    private void handlePing(WebSocketSession wsSession) throws IOException {
        wsSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                com.chatweb.realtime.protocol.RealtimeServerMessage.pong()
        )));
    }

    private String resolveAccessToken(WebSocketSession session) {
        Object ref = session.getAttributes().get("accessTokenRef");
        if (!(ref instanceof String tokenRef) || tokenRef.isBlank()) {
            return null;
        }
        return redisTemplate.opsForValue().get(SESSION_TOKEN_PREFIX + tokenRef);
    }

    private String requireActiveAccessToken(WebSocketSession session, RealtimeSession realtimeSession, String stage) {
        String token = resolveAccessToken(session);
        if (token != null && !token.isBlank()) {
            return token;
        }

        log.warn("[WS][AUTH] Closing session due to missing/expired token stage={} sessionId={} userId={}",
                stage,
                realtimeSession != null ? realtimeSession.getSessionId() : "unknown",
                realtimeSession != null ? realtimeSession.getUserId() : "unknown");

        try {
            sendError(session, "TOKEN_EXPIRED", "Access token expired. Please reconnect.");
        } catch (Exception sendEx) {
            log.debug("[WS][AUTH] Failed to send token-expired frame", sendEx);
        }

        try {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("TOKEN_EXPIRED"));
        } catch (Exception closeEx) {
            log.debug("[WS][AUTH] Failed to close expired-token session", closeEx);
        }

        return null;
    }

    private void clearAccessToken(WebSocketSession session) {
        Object ref = session.getAttributes().get("accessTokenRef");
        if (ref instanceof String tokenRef && !tokenRef.isBlank()) {
            redisTemplate.delete(SESSION_TOKEN_PREFIX + tokenRef);
        }
    }
}
