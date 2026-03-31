package com.example.common.websocket.broadcaster;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractWebSocketBroadcaster {

    protected final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Object> sessionLocks = new ConcurrentHashMap<>();

    protected void send(WebSocketSession session, Object payload) {
        if (session == null) return;

        if (!session.isOpen()) {
            handleDeadSession(session, null);
            return;
        }

        Object lock = sessionLocks.computeIfAbsent(session.getId(), id -> new Object());

        synchronized (lock) {
            try {
                if (!session.isOpen()) {
                    handleDeadSession(session, null);
                    return;
                }

                String json = objectMapper.writeValueAsString(payload);
                session.sendMessage(new TextMessage(json));

            } catch (IOException e) {
                log.debug("[WS] IO error / session closed. session={}", session.getId());
                handleDeadSession(session, e);

            } catch (IllegalStateException e) {
                log.debug("[WS] IllegalState (likely closed). session={}", session.getId());
                handleDeadSession(session, e);

            } catch (Exception e) {
                log.warn("[WS] Unexpected send error session={}", session.getId(), e);
            }
        }
    }

    protected void handleDeadSession(WebSocketSession session, Exception cause) {
        try {
            if (session.isOpen()) {
                session.close();
            }
        } catch (Exception ignored) {}

        sessionLocks.remove(session.getId());
    }
}

