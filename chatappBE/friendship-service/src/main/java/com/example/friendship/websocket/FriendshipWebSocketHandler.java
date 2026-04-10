package com.example.friendship.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class FriendshipWebSocketHandler extends TextWebSocketHandler {

    private final FriendshipSessionRegistry sessionRegistry;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessionRegistry.register(session);

        UUID userId = sessionRegistry.getUserId(session);
        log.info("[FRIEND-WS] ✅ CONNECT success - userId={} sessionId={}", userId, session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionRegistry.unregister(session);
        log.info("[FRIEND-WS] ❌ DISCONNECT - sessionId={} status={}", session.getId(), status);
    }
}
