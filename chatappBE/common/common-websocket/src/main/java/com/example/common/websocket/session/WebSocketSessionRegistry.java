//package com.example.common.websocket.session;
//
//import com.example.common.websocket.handshake.AbstractJwtHandshakeInterceptor;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Component;
//import org.springframework.web.socket.TextMessage;
//import org.springframework.web.socket.WebSocketSession;
//
//import java.util.*;
//import java.util.concurrent.ConcurrentHashMap;
//
//@Component
//@RequiredArgsConstructor
//@Slf4j
//public class WebSocketSessionRegistry
//        implements
//        IWebSocketSessionRegistry,
//        IRoomBroadcaster,
//        IGlobalBroadcaster,
//        IUserBroadcaster,
//        IRoomSessionRegistry{
//
//    private final ObjectMapper objectMapper;
//
//    private final Map<String, WebSocketSession> sessions =
//            new ConcurrentHashMap<>();
//
//    private final Map<String, UUID> sessionUsers =
//            new ConcurrentHashMap<>();
//
//    private final Map<UUID, Set<String>> roomSessions =
//            new ConcurrentHashMap<>();
//
//
//    @Override
//    public void register(WebSocketSession session) {
//
//        UUID userId = (UUID) session.getAttributes()
//                .get(AbstractJwtHandshakeInterceptor.ATTR_USER_ID);
//
//        sessions.put(session.getId(), session);
//        sessionUsers.put(session.getId(), userId);
//
//        log.info("[WS] CONNECT sessionId={} userId={}",
//                session.getId(), userId);
//    }
//
//    @Override
//    public void unregister(WebSocketSession session) {
//
//        String sessionId = session.getId();
//
//        sessions.remove(sessionId);
//        sessionUsers.remove(sessionId);
//
//        roomSessions.values()
//                .forEach(set -> set.remove(sessionId));
//
//        log.info("[WS] DISCONNECT sessionId={}", sessionId);
//    }
//
//    @Override
//    public UUID getUserId(WebSocketSession session) {
//        return sessionUsers.get(session.getId());
//    }
//
//
//    @Override
//    public void joinRoom(UUID roomId, WebSocketSession session) {
//
//        roomSessions
//                .computeIfAbsent(
//                        roomId,
//                        k -> ConcurrentHashMap.newKeySet()
//                )
//                .add(session.getId());
//    }
//
//    @Override
//    public void leaveRoom(UUID roomId, WebSocketSession session) {
//
//        Set<String> set = roomSessions.get(roomId);
//        if (set != null) {
//            set.remove(session.getId());
//            if (set.isEmpty()) {
//                roomSessions.remove(roomId);
//            }
//        }
//    }
//
//    @Override
//    public void sendToRoom(UUID roomId, Object payload) {
//
//        Set<String> ids = roomSessions.get(roomId);
//        if (ids == null) return;
//
//        String json = toJson(payload);
//
//        ids.stream()
//                .map(sessions::get)
//                .filter(Objects::nonNull)
//                .forEach(s -> send(s, json));
//    }
//
//
//    @Override
//    public void sendToAll(Object payload) {
//
//        String json = toJson(payload);
//
//        sessions.values()
//                .forEach(s -> send(s, json));
//    }
//
//
//    @Override
//    public void sendToUser(UUID userId, Object payload) {
//
//        String json = toJson(payload);
//
//        sessionUsers.forEach((sessionId, uid) -> {
//            if (uid.equals(userId)) {
//                WebSocketSession s = sessions.get(sessionId);
//                if (s != null) {
//                    send(s, json);
//                }
//            }
//        });
//    }
//
//
//    private void send(WebSocketSession session, String json) {
//        try {
//            session.sendMessage(new TextMessage(json));
//        } catch (Exception e) {
//            log.warn("[WS] SEND FAILED sessionId={}",
//                    session.getId(), e);
//        }
//    }
//
//    private String toJson(Object payload) {
//        try {
//            return objectMapper.writeValueAsString(payload);
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//    }
//}
//
//
//
