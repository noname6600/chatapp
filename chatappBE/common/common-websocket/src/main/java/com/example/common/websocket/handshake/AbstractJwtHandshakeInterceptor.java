package com.example.common.websocket.handshake;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.UUID;

@Slf4j
public abstract class AbstractJwtHandshakeInterceptor
        implements HandshakeInterceptor, IJwtHandshakeInterceptor {

    public static final String ATTR_USER_ID = "userId";

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {

        if (!(request instanceof ServletServerHttpRequest servlet)) {
            log.warn("[WS] Reject: not servlet request");
            return false;
        }

        String token = servlet.getServletRequest()
                .getParameter("token");

        if (token == null || token.isBlank()) {
            log.warn("[WS] Reject: missing token");
            return false;
        }

        try {
            UUID userId = resolveUserId(token);

            if (userId == null) {
                log.warn("[WS] Reject: resolveUserId returned null");
                return false;
            }

            attributes.put(ATTR_USER_ID, userId);

            log.info("[WS] Handshake success userId={}", userId);
            return true;

        } catch (Exception e) {
            log.warn("[WS] Reject: invalid token - {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
        // no-op
    }
}
