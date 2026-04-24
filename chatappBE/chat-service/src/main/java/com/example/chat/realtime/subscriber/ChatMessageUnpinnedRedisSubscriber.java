package com.example.chat.realtime.subscriber;

import com.example.chat.modules.room.dto.RoomMessagePinEventPayload;
import com.example.common.integration.chat.ChatEventType;
import com.example.common.integration.websocket.WsEvent;
import com.example.common.redis.api.IRedisSubscriber;
import com.example.common.redis.message.RedisMessage;
import com.example.common.websocket.session.IRoomBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatMessageUnpinnedRedisSubscriber
        implements IRedisSubscriber<RedisMessage<RoomMessagePinEventPayload>> {

    private final IRoomBroadcaster roomBroadcaster;
    private final RealtimeEventDedupeGuard dedupeGuard;

    @Override
    public String eventType() {
        return ChatEventType.MESSAGE_UNPINNED.value();
    }

    @Override
    public void onMessage(RedisMessage<RoomMessagePinEventPayload> message) {
        RoomMessagePinEventPayload payload = message.getPayload();

        if (dedupeGuard.isDuplicate(payload.getEventId())) {
            log.warn(
                "[realtime-fanout] duplicate unpin event dropped roomId={} eventId={}",
                payload.getRoomId(),
                payload.getEventId()
            );
            return;
        }

        long lagMs = payload.getOccurredAt() == null
            ? -1
            : Duration.between(payload.getOccurredAt(), Instant.now()).toMillis();

        log.info(
            "[realtime-fanout] consume unpin event roomId={} messageId={} eventId={} lagMs={}",
            payload.getRoomId(),
            payload.getMessageId(),
            payload.getEventId(),
            lagMs
        );

        roomBroadcaster.sendToRoom(
                payload.getRoomId(),
                WsEvent.builder()
                        .type(message.getEventType())
                        .payload(payload)
                        .build()
        );
    }
}
