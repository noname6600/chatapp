package com.chatweb.presence.infrastructure.redis;

import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.event.EventMetadata;
import com.chatweb.common.event.TraceContext;
import com.chatweb.common.integration.presence.PresenceEventType;
import com.chatweb.common.integration.presence.PresenceRoomJoinPayload;
import com.chatweb.common.integration.presence.PresenceRoomLeavePayload;
import com.chatweb.common.integration.presence.PresenceStatus;
import com.chatweb.common.integration.presence.PresenceStopTypingPayload;
import com.chatweb.common.integration.presence.PresenceTypingPayload;
import com.chatweb.common.integration.presence.PresenceUserOfflinePayload;
import com.chatweb.common.integration.presence.PresenceUserOnlinePayload;
import com.chatweb.common.integration.presence.PresenceUserStatePayload;
import com.chatweb.common.integration.presence.RoomOnlineUsersPayload;
import com.chatweb.common.realtime.policy.RealtimeFlowClassificationPolicy;
import com.chatweb.common.realtime.policy.RealtimeFlowId;
import com.chatweb.common.realtime.policy.RealtimeFlowType;
import com.chatweb.common.redis.publisher.RedisEventPublisher;
import com.chatweb.common.redis.channel.RedisChannels;
import com.chatweb.presence.realtime.port.PresenceRealtimePort;
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

    private <T> EventEnvelope<T> msg(String eventType, T payload) {
        String eventId = UUID.randomUUID().toString();
        return new EventEnvelope<>(
            new EventMetadata(eventId, eventType, sourceService, Instant.now(), TraceContext.correlationIdOrEventId(eventId)),
                payload
        );
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
        return RedisChannels.PRESENCE_GLOBAL;
    }

    private String userChannel() {
        return RedisChannels.PRESENCE_USER;
    }

    private String roomChannel(UUID roomId) {
        return RedisChannels.presenceRoom(roomId);
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