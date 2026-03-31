package com.example.presence.redis;


import com.example.common.integration.presence.*;
import com.example.common.integration.websocket.WsEvent;
import com.example.common.redis.api.IRedisMessage;
import com.example.common.redis.api.IRedisSubscriber;
import com.example.common.redis.message.RedisMessage;
import com.example.common.websocket.session.IGlobalBroadcaster;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserOnlineSubscriber
        implements IRedisSubscriber<RedisMessage<PresenceUserOnlinePayload>> {

    private final IGlobalBroadcaster globalBroadcaster;

    @Override
    public String eventType() {
        return PresenceEventType.USER_ONLINE.value();
    }

    @Override
    public void onMessage(RedisMessage<PresenceUserOnlinePayload> message) {

        WsEvent wsEvent = WsEvent.builder()
                .type(message.getEventType())
                .payload(message.getPayload())
                .build();

        globalBroadcaster.sendToAll(wsEvent);
    }
}