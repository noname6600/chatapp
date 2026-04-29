package com.example.presence.websocket.session;


import com.example.common.websocket.handshake.AbstractJwtHandshakeInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
@Slf4j
public class PresenceSessionRegistry implements IPresenceQuery {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, UUID> sessionUsers = new ConcurrentHashMap<>();

    private final Map<UUID, Set<String>> userSessions = new ConcurrentHashMap<>();

    private final Map<UUID, Set<String>> roomSessions = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> sessionRooms = new ConcurrentHashMap<>();

    // ================= SESSION =================

    public void register(WebSocketSession session) {

        UUID userId = (UUID) session.getAttributes()
                .get(AbstractJwtHandshakeInterceptor.ATTR_USER_ID);

        sessions.put(session.getId(), session);
        sessionUsers.put(session.getId(), userId);

        userSessions
                .computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet())
                .add(session.getId());
    }

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

        Set<UUID> rooms = sessionRooms.remove(sessionId);

        if (rooms != null) {
            for (UUID roomId : rooms) {
                Set<String> rs = roomSessions.get(roomId);
                if (rs != null) rs.remove(sessionId);
            }
        }
    }

    public UUID getUserId(WebSocketSession session) {
        return sessionUsers.get(session.getId());
    }

    // ================= ROOM =================

    public void joinRoom(UUID roomId, WebSocketSession session) {

        String sessionId = session.getId();

        roomSessions
                .computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet())
                .add(sessionId);

        sessionRooms
                .computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet())
                .add(roomId);
    }

    public void leaveRoom(UUID roomId, WebSocketSession session) {

        String sessionId = session.getId();

        Set<String> rs = roomSessions.get(roomId);
        if (rs != null) rs.remove(sessionId);

        Set<UUID> rooms = sessionRooms.get(sessionId);
        if (rooms != null) rooms.remove(roomId);
    }

    public void removeSessionFromAllRooms(WebSocketSession session) {

        String sessionId = session.getId();

        Set<UUID> rooms = sessionRooms.remove(sessionId);

        if (rooms == null) return;

        for (UUID roomId : rooms) {

            Set<String> rs = roomSessions.get(roomId);

            if (rs != null) rs.remove(sessionId);
        }
    }

    // ================= QUERY =================

    public boolean isUserOnline(UUID userId) {

        Set<String> set = userSessions.get(userId);

        return set != null && !set.isEmpty();
    }

    @Override
    public Set<UUID> getOnlineUsers() {
        return Set.copyOf(userSessions.keySet());
    }

    public Set<UUID> getRoomsOfUser(UUID userId) {

        Set<String> sIds = userSessions.get(userId);

        if (sIds == null) return Set.of();

        return sIds.stream()
                .map(sessionRooms::get)
                .filter(Objects::nonNull)
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
    }

    public boolean isUserInRoom(UUID userId, UUID roomId) {

        Set<String> sIds = userSessions.get(userId);

        if (sIds == null) return false;

        for (String sessionId : sIds) {

            Set<UUID> rooms = sessionRooms.get(sessionId);

            if (rooms != null && rooms.contains(roomId)) {
                return true;
            }
        }

        return false;
    }

    public Collection<WebSocketSession> getUserSessions(UUID userId) {

        Set<String> ids = userSessions.get(userId);

        if (ids == null) return List.of();

        return ids.stream()
                .map(sessions::get)
                .filter(Objects::nonNull)
                .toList();
    }

    public Collection<WebSocketSession> getRoomSessions(UUID roomId) {

        Set<String> ids = roomSessions.get(roomId);

        if (ids == null) return List.of();

        return ids.stream()
                .map(sessions::get)
                .filter(Objects::nonNull)
                .toList();
    }

    public Collection<WebSocketSession> getAllSessions() {
        return sessions.values();
    }
}