package com.example.friendship.websocket;

import com.example.common.websocket.broadcaster.AbstractWebSocketBroadcaster;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class WebSocketFriendshipBroadcaster
        extends AbstractWebSocketBroadcaster {

    private final FriendshipSessionRegistry registry;

    public WebSocketFriendshipBroadcaster(
            ObjectMapper objectMapper,
            FriendshipSessionRegistry registry
    ) {
        super(objectMapper);
        this.registry = registry;
    }

    public void sendToUser(UUID userId, Object payload) {
        registry.getUserSessions(userId)
                .forEach(session -> send(session, payload));
    }
}
