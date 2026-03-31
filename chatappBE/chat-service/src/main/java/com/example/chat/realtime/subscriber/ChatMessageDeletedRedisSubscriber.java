package com.example.chat.realtime.subscriber;

import com.example.common.integration.chat.ChatEventType;
import com.example.common.integration.chat.MessageDeletedPayload;
import com.example.common.integration.websocket.WsEvent;
import com.example.common.redis.api.IRedisSubscriber;
import com.example.common.redis.message.RedisMessage;
import com.example.common.websocket.session.IRoomBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatMessageDeletedRedisSubscriber
        implements IRedisSubscriber<RedisMessage<MessageDeletedPayload>> {

    private final IRoomBroadcaster roomBroadcaster;

    @Override
    public String eventType() {
        return ChatEventType.MESSAGE_DELETED.value();
    }

    @Override
    public void onMessage(
            RedisMessage<MessageDeletedPayload> message
    ) {

        MessageDeletedPayload payload =
                message.getPayload();

        WsEvent wsEvent =
                WsEvent.builder()
                        .type(message.getEventType())
                        .payload(payload)
                        .build();

        roomBroadcaster.sendToRoom(
                payload.getRoomId(),
                wsEvent
        );
    }
}
