package com.example.presence.redis;

import com.example.common.integration.presence.*;
import com.example.common.redis.api.IRedisPublisher;
import com.example.common.redis.message.RedisMessage;
import com.example.presence.constants.PresenceRedisChannels;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PresenceRedisPublisher {

    private final IRedisPublisher redisPublisher;

    @Value("${spring.application.name}")
    private String sourceService;

    private <T> RedisMessage<T> msg(String eventType, T payload) {
        return RedisMessage.<T>builder()
                .messageId(UUID.randomUUID().toString())
                .eventType(eventType)
                .sourceService(sourceService)
                .createdAt(Instant.now())
                .payload(payload)
                .build();
    }

    public void online(UUID userId, PresenceStatus status) {
        redisPublisher.publish(
                PresenceRedisChannels.PRESENCE_USER,
                msg(
                        PresenceEventType.USER_ONLINE.value(),
                        PresenceUserOnlinePayload.builder()
                                .userId(userId)
                                .roomId(null)
                                .status(status)
                                .build()
                )
        );
    }

    public void offline(UUID userId) {
        redisPublisher.publish(
                PresenceRedisChannels.PRESENCE_USER,
                msg(
                        PresenceEventType.USER_OFFLINE.value(),
                        PresenceUserOfflinePayload.builder()
                                .userId(userId)
                                .roomId(null)
                                .status(PresenceStatus.OFFLINE)
                                .build()
                )
        );
    }

    public void statusChanged(UUID userId, PresenceStatus status) {
        redisPublisher.publish(
                PresenceRedisChannels.PRESENCE_USER,
                msg(
                        PresenceEventType.USER_STATUS_CHANGED.value(),
                        PresenceUserStatePayload.builder()
                                .userId(userId)
                                .status(status)
                                .build()
                )
        );
    }

    public void typing(UUID userId, UUID roomId) {
        redisPublisher.publish(
                PresenceRedisChannels.PRESENCE_ROOM + roomId,
                msg(
                        PresenceEventType.ROOM_TYPING.value(),
                        PresenceTypingPayload.builder()
                                .userId(userId)
                                .roomId(roomId)
                                .build()
                )
        );
    }

    public void stopTyping(UUID userId, UUID roomId) {
        redisPublisher.publish(
                PresenceRedisChannels.PRESENCE_ROOM + roomId,
                msg(
                        PresenceEventType.ROOM_STOP_TYPING.value(),
                        PresenceStopTypingPayload.builder()
                                .userId(userId)
                                .roomId(roomId)
                                .build()
                )
        );
    }

    public void roomJoin(UUID userId, UUID roomId) {
        redisPublisher.publish(
                PresenceRedisChannels.PRESENCE_ROOM + roomId,
                msg(
                        PresenceEventType.ROOM_JOIN.value(),
                        PresenceRoomJoinPayload.builder()
                                .userId(userId)
                                .roomId(roomId)
                                .build()
                )
        );
    }

    public void roomLeave(UUID userId, UUID roomId) {
        redisPublisher.publish(
                PresenceRedisChannels.PRESENCE_ROOM + roomId,
                msg(
                        PresenceEventType.ROOM_LEAVE.value(),
                        PresenceRoomLeavePayload.builder()
                                .userId(userId)
                                .roomId(roomId)
                                .build()
                )
        );
    }

        public void roomOnlineUsers(UUID roomId, List<PresenceUserStatePayload> users) {
        redisPublisher.publish(
                PresenceRedisChannels.PRESENCE_ROOM + roomId,
                msg(
                        PresenceEventType.ROOM_ONLINE_USERS.value(),
                        RoomOnlineUsersPayload.builder()
                                .roomId(roomId)
                                .users(users)
                                .build()
                )
        );
    }
}