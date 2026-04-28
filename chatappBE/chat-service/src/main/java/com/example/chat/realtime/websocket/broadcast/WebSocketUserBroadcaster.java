package com.example.chat.realtime.websocket.broadcast;

import com.example.chat.realtime.websocket.session.ChatSessionRegistry;
import com.example.common.websocket.broadcaster.AbstractWebSocketBroadcaster;
import com.example.common.websocket.session.IUserBroadcaster;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class WebSocketUserBroadcaster
        extends AbstractWebSocketBroadcaster
        implements IUserBroadcaster {

    private final ChatSessionRegistry registry;

    public WebSocketUserBroadcaster(
            ObjectMapper objectMapper,
            ChatSessionRegistry registry
    ) {
        super(objectMapper);
        this.registry = registry;
    }

    @Override
    public void sendToUser(UUID userId, Object payload) {

        registry.getUserSessions(userId)
                .forEach(session -> send(session, payload));
    }
}
