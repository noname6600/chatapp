package com.example.common.websocket.session;

import org.springframework.web.socket.WebSocketSession;

import java.util.UUID;

public interface IWebSocketSessionRegistry {

    void register(WebSocketSession session);
    void unregister(WebSocketSession session);
    UUID getUserId(WebSocketSession session);
}


