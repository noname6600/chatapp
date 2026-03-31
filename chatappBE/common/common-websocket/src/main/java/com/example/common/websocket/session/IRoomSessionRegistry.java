package com.example.common.websocket.session;

import org.springframework.web.socket.WebSocketSession;

import java.util.UUID;

public interface IRoomSessionRegistry {

    void joinRoom(UUID roomId, WebSocketSession session);
    void leaveRoom(UUID roomId, WebSocketSession session);
}

