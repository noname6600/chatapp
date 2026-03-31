package com.example.presence.websocket.handler;


import com.example.common.integration.websocket.WsEvent;
import com.example.common.websocket.session.IUserBroadcaster;
import com.example.common.integration.presence.GlobalOnlineUsersPayload;
import com.example.common.integration.presence.RoomOnlineUsersPayload;
import com.example.presence.dto.PresenceWsCommand;
import com.example.presence.redis.PresenceRedisPublisher;
import com.example.presence.service.PresenceService;
import com.example.presence.websocket.session.PresenceSessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class PresenceWebSocketHandler extends TextWebSocketHandler {

    private final PresenceSessionRegistry sessionRegistry;
    private final PresenceService presenceService;
    private final PresenceRedisPublisher redisPublisher;
    private final IUserBroadcaster userBroadcaster;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {

        sessionRegistry.register(session);

        UUID userId = sessionRegistry.getUserId(session);

        presenceService.online(userId);
        presenceService.heartbeat(userId, true);

        userBroadcaster.sendToUser(
                userId,
                WsEvent.builder()
                        .type("presence.global.snapshot")
                .payload(
                    GlobalOnlineUsersPayload.builder()
                        .users(presenceService.getAllPresenceUsers())
                        .build()
                )
                        .build()
        );

        log.info("[PRESENCE] CONNECT userId={} session={}", userId, session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {

        UUID userId = sessionRegistry.getUserId(session);

        try {

            PresenceWsCommand cmd =
                    objectMapper.readValue(message.getPayload(), PresenceWsCommand.class);

            switch (cmd.getType()) {

                case USER_HEARTBEAT -> presenceService.heartbeat(userId, !Boolean.FALSE.equals(cmd.getActive()));

                case ROOM_JOIN -> handleJoinRoom(session, userId, cmd.getRoomId());

                case ROOM_LEAVE -> handleLeaveRoom(session, userId, cmd.getRoomId());

                case ROOM_TYPING -> {

                    UUID roomId = cmd.getRoomId();

                    if (sessionRegistry.isUserInRoom(userId, roomId)) {
                        redisPublisher.typing(userId, roomId);
                    }
                }

                case ROOM_STOP_TYPING -> {

                    UUID roomId = cmd.getRoomId();

                    if (sessionRegistry.isUserInRoom(userId, roomId)) {
                        redisPublisher.stopTyping(userId, roomId);
                    }
                }
            }

        } catch (Exception e) {

            log.warn(
                    "[PRESENCE] INVALID WS MESSAGE user={} payload={}",
                    userId,
                    message.getPayload(),
                    e
            );
        }
    }

    private void handleJoinRoom(WebSocketSession session, UUID userId, UUID roomId) {

        sessionRegistry.joinRoom(roomId, session);

        presenceService.joinRoom(roomId, userId);

        userBroadcaster.sendToUser(
                userId,
                WsEvent.builder()
                        .type("presence.room.snapshot")
                .payload(
                    RoomOnlineUsersPayload.builder()
                        .roomId(roomId)
                        .users(presenceService.getRoomPresence(roomId))
                        .build()
                )
                        .build()
        );

        presenceService.notifyRoomOnlineUsers(roomId);

        log.info("[PRESENCE] JOIN room={} user={}", roomId, userId);
    }

    private void handleLeaveRoom(WebSocketSession session, UUID userId, UUID roomId) {

        sessionRegistry.leaveRoom(roomId, session);

        presenceService.leaveRoom(roomId, userId);

        presenceService.notifyRoomOnlineUsers(roomId);

        log.info("[PRESENCE] LEAVE room={} user={}", roomId, userId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {

        UUID userId = sessionRegistry.getUserId(session);

        Set<UUID> rooms = sessionRegistry.getRoomsOfUser(userId);

        sessionRegistry.removeSessionFromAllRooms(session);

        sessionRegistry.unregister(session);

        if (!sessionRegistry.isUserOnline(userId)) {
            presenceService.offline(userId);
        }

        rooms.forEach(presenceService::notifyRoomOnlineUsers);

        log.info("[PRESENCE] DISCONNECT userId={} session={}", userId, session.getId());
    }
}