package com.example.chat.realtime.subscriber;

import com.example.common.integration.chat.ChatEventType;
import com.example.common.integration.chat.MessageUpdatedPayload;
import com.example.common.websocket.protocol.RealtimeWsEvent;
import com.example.common.redis.message.RedisMessage;
import com.example.common.websocket.session.IRoomBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatMessageEditedRedisSubscriber
        implements RedisEventSubscriber<RedisMessage<MessageUpdatedPayload>> {

    private final IRoomBroadcaster roomBroadcaster;
        private final RealtimeEventDedupeGuard dedupeGuard;

    @Override
    public String eventType() {
        return ChatEventType.MESSAGE_EDITED.value();
    }

    @Override
    public void onMessage(
            RedisMessage<MessageUpdatedPayload> message
    ) {
                if (message == null || message.getPayload() == null) {
                        return;
                }
                if (dedupeGuard.isDuplicateKey(message.getMessageId())) {
                        log.info("[realtime-fanout] duplicate redis message-edited dropped messageId={}", message.getMessageId());
                        return;
                }

        MessageUpdatedPayload payload =
                message.getPayload();

        RealtimeWsEvent wsEvent =
                RealtimeWsEvent.builder()
                        .type(message.getEventType())
                        .payload(payload)
                        .build();

        roomBroadcaster.sendToRoom(
                payload.getRoomId(),
                wsEvent
        );
    }
}

