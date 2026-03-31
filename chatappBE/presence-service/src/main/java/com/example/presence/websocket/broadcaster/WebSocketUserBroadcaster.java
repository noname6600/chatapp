package com.example.presence.websocket.broadcaster;

import com.example.common.websocket.broadcaster.AbstractWebSocketBroadcaster;
import com.example.common.websocket.session.IUserBroadcaster;
import com.example.presence.websocket.session.PresenceSessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class WebSocketUserBroadcaster
        extends AbstractWebSocketBroadcaster
        implements IUserBroadcaster {

    private final PresenceSessionRegistry registry;

    public WebSocketUserBroadcaster(
            ObjectMapper objectMapper,
            PresenceSessionRegistry registry
    ) {
        super(objectMapper);
        this.registry = registry;
    }
    @Override
    public void sendToUser(UUID userId, Object payload) {

        registry.getUserSessions(userId)
                .forEach(s -> send(s, payload));
    }
}
