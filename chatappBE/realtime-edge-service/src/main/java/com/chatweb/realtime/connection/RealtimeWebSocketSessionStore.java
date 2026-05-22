package com.chatweb.realtime.connection;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Stores active websocket sessions by realtime session id.
 */
@Component
public class RealtimeWebSocketSessionStore {

    private final ConcurrentMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public void register(String sessionId, WebSocketSession session) {
        sessions.put(sessionId, session);
    }

    public void unregister(String sessionId) {
        sessions.remove(sessionId);
    }

    public Optional<WebSocketSession> findBySessionId(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }
}
