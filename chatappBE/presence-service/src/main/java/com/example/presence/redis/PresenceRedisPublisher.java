package com.example.presence.redis;

import com.example.common.integration.presence.PresenceEventType;
import com.example.common.integration.presence.PresenceRoomJoinPayload;
import com.example.common.integration.presence.PresenceRoomLeavePayload;
import com.example.common.integration.presence.PresenceStatus;
import com.example.common.integration.presence.PresenceStopTypingPayload;
import com.example.common.integration.presence.PresenceTypingPayload;
import com.example.common.integration.presence.PresenceUserOfflinePayload;
import com.example.common.integration.presence.PresenceUserOnlinePayload;
import com.example.common.integration.presence.PresenceUserStatePayload;
import com.example.common.integration.presence.RoomOnlineUsersPayload;
import com.example.common.realtime.policy.RealtimeFlowClassificationPolicy;
import com.example.common.realtime.policy.RealtimeFlowId;
import com.example.common.realtime.policy.RealtimeFlowType;
import com.example.common.redis.publisher.RedisEventPublisher;
import com.example.common.redis.message.RedisMessage;
import com.example.presence.constants.PresenceRedisChannels;
import com.example.presence.realtime.port.PresenceRealtimePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PresenceRedisPublisher implements PresenceRealtimePort {

    private final RedisEventPublisher redisPublisher;

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

    @Override
    public void publishGlobalEvent(String eventType, Object payload) {
        redisPublisher.publish(globalChannel(), msg(eventType, payload));
    }

    @Override
    public void publishRoomEvent(UUID roomId, String eventType, Object payload) {
        redisPublisher.publish(roomChannel(roomId), msg(eventType, payload));
    }

    @Override
    public void publishUserEvent(String eventType, Object payload) {
        redisPublisher.publish(userChannel(), msg(eventType, payload));
    }

    @Override
    public void publishGlobalEvent(String eventType, Object payload, RealtimeFlowId flowId) {
        log.debug("Publishing global event for flow {}: eventType={}, flowType={}",
                flowId, eventType, RealtimeFlowClassificationPolicy.getFlowType(flowId));
        directPublish(eventType, payload, globalChannel());
    }

    @Override
    public void publishRoomEvent(UUID roomId, String eventType, Object payload, RealtimeFlowId flowId) {
        RealtimeFlowType flowType = RealtimeFlowClassificationPolicy.getFlowType(flowId);
        log.debug("Publishing room event for flow {}: eventType={}, flowType={}", flowId, eventType, flowType);
        directPublish(eventType, payload, roomChannel(roomId));
    }

    @Override
    public void publishUserEvent(String eventType, Object payload, RealtimeFlowId flowId) {
        RealtimeFlowType flowType = RealtimeFlowClassificationPolicy.getFlowType(flowId);
        log.debug("Publishing user event for flow {}: eventType={}, flowType={}", flowId, eventType, flowType);
        directPublish(eventType, payload, userChannel());
    }

    private void directPublish(String eventType, Object payload, String channel) {
        redisPublisher.publish(channel, msg(eventType, payload));
    }

    private String globalChannel() {
        return PresenceRedisChannels.PRESENCE_GLOBAL;
    }

    private String userChannel() {
        return PresenceRedisChannels.PRESENCE_USER;
    }

    private String roomChannel(UUID roomId) {
        return PresenceRedisChannels.roomChannel(roomId);
    }

    public void online(UUID userId, PresenceStatus status) {
        publishUserEvent(
                PresenceEventType.USER_ONLINE.value(),
                PresenceUserOnlinePayload.builder()
                        .userId(userId)
                        .roomId(null)
                        .status(status)
                        .build()
        );
    }

    public void offline(UUID userId) {
        publishUserEvent(
                PresenceEventType.USER_OFFLINE.value(),
                PresenceUserOfflinePayload.builder()
                        .userId(userId)
                        .roomId(null)
                        .status(PresenceStatus.OFFLINE)
                        .build()
        );
    }

    public void statusChanged(UUID userId, PresenceStatus status) {
        publishUserEvent(
                PresenceEventType.USER_STATUS_CHANGED.value(),
                PresenceUserStatePayload.builder()
                        .userId(userId)
                        .status(status)
                        .build()
        );
    }

    public void typing(UUID userId, UUID roomId) {
        publishRoomEvent(
                roomId,
                PresenceEventType.ROOM_TYPING.value(),
                PresenceTypingPayload.builder()
                        .userId(userId)
                        .roomId(roomId)
                        .build()
        );
    }

    public void stopTyping(UUID userId, UUID roomId) {
        publishRoomEvent(
                roomId,
                PresenceEventType.ROOM_STOP_TYPING.value(),
                PresenceStopTypingPayload.builder()
                        .userId(userId)
                        .roomId(roomId)
                        .build()
        );
    }

    public void roomJoin(UUID userId, UUID roomId) {
        publishRoomEvent(
                roomId,
                PresenceEventType.ROOM_JOIN.value(),
                PresenceRoomJoinPayload.builder()
                        .userId(userId)
                        .roomId(roomId)
                        .build()
        );
    }

    public void roomLeave(UUID userId, UUID roomId) {
        publishRoomEvent(
                roomId,
                PresenceEventType.ROOM_LEAVE.value(),
                PresenceRoomLeavePayload.builder()
                        .userId(userId)
                        .roomId(roomId)
                        .build()
        );
    }

    public void roomOnlineUsers(UUID roomId, List<PresenceUserStatePayload> users) {
        publishRoomEvent(
                roomId,
                PresenceEventType.ROOM_ONLINE_USERS.value(),
                RoomOnlineUsersPayload.builder()
                        .roomId(roomId)
                        .users(users)
                        .build()
        );
    }
}