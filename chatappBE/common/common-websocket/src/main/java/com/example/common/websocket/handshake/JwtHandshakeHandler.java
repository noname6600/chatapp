package com.example.common.websocket.handshake;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

public class JwtHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(
            ServerHttpRequest request,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {

        Object userIdObj =
                attributes.get(AbstractJwtHandshakeInterceptor.ATTR_USER_ID);

        if (!(userIdObj instanceof UUID userId)) {
            return null;
        }

        return new WsPrincipal(userId);
    }
}
