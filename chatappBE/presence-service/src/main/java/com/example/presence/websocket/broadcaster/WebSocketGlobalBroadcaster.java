package com.example.presence.websocket.broadcaster;

import com.example.common.websocket.broadcaster.AbstractWebSocketBroadcaster;
import com.example.common.websocket.session.IGlobalBroadcaster;
import com.example.presence.websocket.session.PresenceSessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class WebSocketGlobalBroadcaster
        extends AbstractWebSocketBroadcaster
        implements IGlobalBroadcaster {

    private final PresenceSessionRegistry registry;

    public WebSocketGlobalBroadcaster(
            ObjectMapper objectMapper,
            PresenceSessionRegistry registry
    ) {
        super(objectMapper);
        this.registry = registry;
    }

    @Override
    public void sendToAll(Object payload) {
        registry.getAllSessions()
                .forEach(session -> send(session, payload));
    }
}

