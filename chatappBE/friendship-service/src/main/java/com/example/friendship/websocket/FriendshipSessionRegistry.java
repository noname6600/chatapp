package com.example.friendship.websocket;

import com.example.common.websocket.handshake.AbstractJwtHandshakeInterceptor;
import com.example.common.websocket.session.IWebSocketSessionRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class FriendshipSessionRegistry
        implements IWebSocketSessionRegistry {

    /* ================= STATE ================= */

    private final Map<String, WebSocketSession> sessions =
            new ConcurrentHashMap<>();

    private final Map<String, UUID> sessionUsers =
            new ConcurrentHashMap<>();

    private final Map<UUID, Set<String>> userSessions =
            new ConcurrentHashMap<>();

    /* ================= SESSION LIFECYCLE ================= */

    @Override
    public void register(WebSocketSession session) {

        UUID userId = (UUID) session.getAttributes()
                .get(AbstractJwtHandshakeInterceptor.ATTR_USER_ID);

        String sessionId = session.getId();

        sessions.put(sessionId, session);
        sessionUsers.put(sessionId, userId);

        userSessions
                .computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet())
                .add(sessionId);

        log.info("[FRIEND] CONNECT user={} session={}", userId, sessionId);
    }

    @Override
    public void unregister(WebSocketSession session) {

        String sessionId = session.getId();
        UUID userId = sessionUsers.remove(sessionId);

        sessions.remove(sessionId);

        if (userId != null) {
            Set<String> set = userSessions.get(userId);
            if (set != null) {
                set.remove(sessionId);
                if (set.isEmpty()) {
                    userSessions.remove(userId);
                }
            }
        }

        log.info("[FRIEND] DISCONNECT session={}", sessionId);
    }

    @Override
    public UUID getUserId(WebSocketSession session) {
        return sessionUsers.get(session.getId());
    }

    /* ================= QUERY ================= */

    public Collection<WebSocketSession> getUserSessions(UUID userId) {
        return getSessions(userSessions.get(userId));
    }

    private Collection<WebSocketSession> getSessions(Set<String> ids) {
        if (ids == null || ids.isEmpty()) return List.of();

        return ids.stream()
                .map(sessions::get)
                .filter(Objects::nonNull)
                .toList();
    }
}
