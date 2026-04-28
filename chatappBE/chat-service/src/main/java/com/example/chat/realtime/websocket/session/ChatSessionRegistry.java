package com.example.chat.realtime.websocket.session;


import com.example.common.websocket.handshake.AbstractJwtHandshakeInterceptor;
import com.example.common.websocket.session.IRoomBroadcaster;
import com.example.common.websocket.session.IWebSocketSessionRegistry;
import com.example.common.websocket.session.IRoomSessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class ChatSessionRegistry
        implements IWebSocketSessionRegistry, IRoomSessionRegistry {

    /* ================= STATE ================= */

    private final Map<String, WebSocketSession> sessions =
            new ConcurrentHashMap<>();

    private final Map<String, UUID> sessionUsers =
            new ConcurrentHashMap<>();

    private final Map<UUID, Set<String>> userSessions =
            new ConcurrentHashMap<>();

    private final Map<UUID, Set<String>> roomSessions =
            new ConcurrentHashMap<>();

    private final Map<String, Set<UUID>> sessionRooms =
            new ConcurrentHashMap<>();

    /* ================= SESSION LIFECYCLE ================= */

    @Override
    public void register(WebSocketSession session) {

        UUID userId = (UUID) session.getAttributes()
                .get(AbstractJwtHandshakeInterceptor.ATTR_USER_ID);

        String sessionId = session.getId();

        sessions.put(sessionId, session);
        sessionUsers.put(sessionId, userId);
        if (userId != null) {
            userSessions
                .computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet())
                .add(sessionId);
        }

        log.info("[CHAT] CONNECT user={} session={}", userId, sessionId);
    }

    @Override
    public void unregister(WebSocketSession session) {

        String sessionId = session.getId();

        sessions.remove(sessionId);
        UUID userId = sessionUsers.remove(sessionId);

        if (userId != null) {
            Set<String> userSet = userSessions.get(userId);
            if (userSet != null) {
                userSet.remove(sessionId);
                if (userSet.isEmpty()) {
                    userSessions.remove(userId);
                }
            }
        }

        Set<UUID> rooms = sessionRooms.remove(sessionId);

        if (rooms != null) {
            for (UUID roomId : rooms) {

                Set<String> set = roomSessions.get(roomId);
                if (set == null) continue;

                set.remove(sessionId);

                if (set.isEmpty()) {
                    roomSessions.remove(roomId);
                }
            }
        }

        log.info("[CHAT] DISCONNECT session={}", sessionId);
    }

    @Override
    public UUID getUserId(WebSocketSession session) {
        return sessionUsers.get(session.getId());
    }

    /* ================= ROOM MEMBERSHIP ================= */

    @Override
    public void joinRoom(UUID roomId, WebSocketSession session) {

        String sessionId = session.getId();

        roomSessions
                .computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet())
                .add(sessionId);

        sessionRooms
                .computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet())
                .add(roomId);

        log.debug("[CHAT] JOIN room={} session={}", roomId, sessionId);
    }

    @Override
    public void leaveRoom(UUID roomId, WebSocketSession session) {

        String sessionId = session.getId();

        Set<String> set = roomSessions.get(roomId);
        if (set != null) {

            set.remove(sessionId);

            if (set.isEmpty()) {
                roomSessions.remove(roomId);
            }
        }

        Set<UUID> rooms = sessionRooms.get(sessionId);
        if (rooms != null) {
            rooms.remove(roomId);
        }

        log.debug("[CHAT] LEAVE room={} session={}", roomId, sessionId);
    }

    /* ================= QUERY ================= */

    public Collection<WebSocketSession> getRoomSessions(UUID roomId) {

        Set<String> ids = roomSessions.get(roomId);

        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        return ids.stream()
                .map(sessions::get)
                .filter(Objects::nonNull)
                .toList();
    }

    public Collection<WebSocketSession> getUserSessions(UUID userId) {

        Set<String> ids = userSessions.get(userId);

        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        return ids.stream()
                .map(sessions::get)
                .filter(Objects::nonNull)
                .toList();
    }
}
