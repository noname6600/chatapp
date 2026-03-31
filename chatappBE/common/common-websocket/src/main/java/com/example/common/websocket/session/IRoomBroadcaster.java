package com.example.common.websocket.session;

import org.springframework.web.socket.WebSocketSession;

import java.util.UUID;

public interface IRoomBroadcaster {

    void sendToRoom(UUID roomId, Object payload);
}

