package com.example.presence.redis;

import com.example.common.integration.presence.PresenceEventType;
import com.example.common.integration.presence.PresenceUserOfflinePayload;
import com.example.common.websocket.protocol.RealtimeWsEvent;
import com.example.common.redis.message.RedisMessage;
import com.example.common.websocket.session.IGlobalBroadcaster;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserOfflineSubscriber
        implements RedisEventSubscriber<RedisMessage<PresenceUserOfflinePayload>> {

    private final IGlobalBroadcaster globalBroadcaster;

    @Override
    public String eventType() {
        return PresenceEventType.USER_OFFLINE.value();
    }

    @Override
    public void onMessage(RedisMessage<PresenceUserOfflinePayload> message) {

        RealtimeWsEvent wsEvent = RealtimeWsEvent.builder()
                .type(message.getEventType())
                .payload(message.getPayload())
                .build();

        globalBroadcaster.sendToAll(wsEvent);
    }
}
