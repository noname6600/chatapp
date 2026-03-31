package com.example.presence.redis;

import com.example.common.integration.presence.PresenceEventType;
import com.example.common.integration.presence.PresenceRoomJoinPayload;
import com.example.common.integration.websocket.WsEvent;
import com.example.common.redis.api.IRedisSubscriber;
import com.example.common.redis.message.RedisMessage;
import com.example.common.websocket.session.IRoomBroadcaster;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoomJoinSubscriber
        implements IRedisSubscriber<RedisMessage<PresenceRoomJoinPayload>> {

    private final IRoomBroadcaster roomBroadcaster;

    @Override
    public String eventType() {
        return PresenceEventType.ROOM_JOIN.value();
    }

    @Override
    public void onMessage(RedisMessage<PresenceRoomJoinPayload> message) {

        PresenceRoomJoinPayload payload = message.getPayload();

        WsEvent wsEvent = WsEvent.builder()
                .type(message.getEventType())
                .payload(payload)
                .build();

        roomBroadcaster.sendToRoom(payload.getRoomId(), wsEvent);
    }
}
