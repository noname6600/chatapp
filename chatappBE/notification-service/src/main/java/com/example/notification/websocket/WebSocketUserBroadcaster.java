package com.example.notification.websocket;

import com.example.common.websocket.broadcaster.AbstractWebSocketBroadcaster;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class WebSocketUserBroadcaster
        extends AbstractWebSocketBroadcaster {

    private final NotificationSessionRegistry registry;

    public WebSocketUserBroadcaster(
            ObjectMapper objectMapper,
            NotificationSessionRegistry registry
    ) {
        super(objectMapper);
        this.registry = registry;
    }

    public void sendToUser(UUID userId, Object payload) {
        registry.getUserSessions(userId)
                .forEach(session -> send(session, payload));
    }
}

