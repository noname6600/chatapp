package com.example.presence.redis;

import com.example.common.integration.presence.PresenceEventType;
import com.example.common.integration.presence.PresenceStopTypingPayload;
import com.example.common.websocket.protocol.RealtimeWsEvent;
import com.example.common.redis.message.RedisMessage;
import com.example.common.websocket.session.IRoomBroadcaster;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserStopTypingSubscriber
        implements RedisEventSubscriber<RedisMessage<PresenceStopTypingPayload>> {

    private final IRoomBroadcaster roomBroadcaster;

    @Override
    public String eventType() {
        return PresenceEventType.ROOM_STOP_TYPING.value();
    }

    @Override
    public void onMessage(RedisMessage<PresenceStopTypingPayload> message) {

        PresenceStopTypingPayload payload = message.getPayload();

        RealtimeWsEvent wsEvent = RealtimeWsEvent.builder()
                .type(message.getEventType())
                .payload(payload)
                .build();

        roomBroadcaster.sendToRoom(payload.getRoomId(), wsEvent);
    }
}

