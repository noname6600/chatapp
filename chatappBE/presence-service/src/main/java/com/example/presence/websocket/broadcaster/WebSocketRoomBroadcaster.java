package com.example.presence.websocket.broadcaster;

import com.example.common.websocket.broadcaster.AbstractWebSocketBroadcaster;
import com.example.common.websocket.session.IRoomBroadcaster;
import com.example.presence.websocket.session.PresenceSessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
public class WebSocketRoomBroadcaster
        extends AbstractWebSocketBroadcaster
        implements IRoomBroadcaster {

    private final PresenceSessionRegistry registry;

    public WebSocketRoomBroadcaster(
            ObjectMapper objectMapper,
            PresenceSessionRegistry registry
    ) {
        super(objectMapper);
        this.registry = registry;
    }

    @Override
    public void sendToRoom(UUID roomId, Object payload) {
        var roomSessions = registry.getRoomSessions(roomId);
        log.info("[WS] Sending to room {} sessions={}", roomId, roomSessions.size());
        roomSessions.forEach(session -> send(session, payload));
    }
}
