package com.example.chat.realtime.websocket.broadcast;

import com.example.common.websocket.broadcaster.AbstractWebSocketBroadcaster;
import com.example.common.websocket.session.IRoomBroadcaster;
import com.example.chat.realtime.websocket.session.ChatSessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class WebSocketRoomBroadcaster
        extends AbstractWebSocketBroadcaster
        implements IRoomBroadcaster {

    private final ChatSessionRegistry registry;

    public WebSocketRoomBroadcaster(
            ObjectMapper objectMapper,
            ChatSessionRegistry registry
    ) {
        super(objectMapper);
        this.registry = registry;
    }

    @Override
    public void sendToRoom(UUID roomId, Object payload) {

        registry.getRoomSessions(roomId)
                .forEach(session -> send(session, payload));
    }
}


