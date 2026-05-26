package com.chatweb.chat.modules.room.infrastructure.redis;

import com.chatweb.chat.modules.message.infrastructure.redis.ChatRedisPublisher;
import com.chatweb.chat.modules.room.dto.RoomMemberJoinedPayload;
import com.chatweb.chat.modules.room.dto.RoomMemberLeftPayload;
import com.chatweb.chat.modules.room.service.IRoomMembershipEventPublisher;
import com.chatweb.common.integration.chat.ChatEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RoomMembershipEventPublisherAdapter implements IRoomMembershipEventPublisher {

    private final ChatRedisPublisher chatRedisPublisher;

    @Override
    public void publishMemberJoined(UUID roomId, UUID userId, String role, Instant joinedAt) {
        chatRedisPublisher.publishRoomRealtimeEvent(
                roomId,
                ChatEventType.MEMBER_JOINED.value(),
                RoomMemberJoinedPayload.builder()
                        .roomId(roomId).userId(userId).role(role).joinedAt(joinedAt)
                        .build()
        );
    }

    @Override
    public void publishMemberLeft(UUID roomId, UUID userId) {
        chatRedisPublisher.publishRoomRealtimeEvent(
                roomId,
                ChatEventType.MEMBER_LEFT.value(),
                RoomMemberLeftPayload.builder().roomId(roomId).userId(userId).build()
        );
    }

    @Override
    public void publishMemberRemoved(UUID roomId, UUID userId) {
        chatRedisPublisher.publishRoomRealtimeEvent(
                roomId,
                ChatEventType.MEMBER_REMOVED.value(),
                RoomMemberLeftPayload.builder().roomId(roomId).userId(userId).build()
        );
    }
}
