package com.example.chat.realtime.subscriber;

import com.example.chat.modules.room.dto.RoomMessagePinEventPayload;
import com.example.common.integration.chat.ChatEventType;
import com.example.common.websocket.protocol.RealtimeWsEvent;
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
public class ChatMessagePinnedRedisSubscriber
        implements RedisEventSubscriber<RedisMessage<RoomMessagePinEventPayload>> {

    private final IRoomBroadcaster roomBroadcaster;
    private final RealtimeEventDedupeGuard dedupeGuard;

    @Override
    public String eventType() {
        return ChatEventType.MESSAGE_PINNED.value();
    }

    @Override
    public void onMessage(RedisMessage<RoomMessagePinEventPayload> message) {
        RoomMessagePinEventPayload payload = message.getPayload();

        if (dedupeGuard.isDuplicate(payload.getEventId())) {
            log.warn(
                "[realtime-fanout] duplicate pin event dropped roomId={} eventId={}",
                payload.getRoomId(),
                payload.getEventId()
            );
            return;
        }

        long lagMs = payload.getOccurredAt() == null
            ? -1
            : Duration.between(payload.getOccurredAt(), Instant.now()).toMillis();

        log.info(
            "[realtime-fanout] consume pin event roomId={} messageId={} eventId={} lagMs={}",
            payload.getRoomId(),
            payload.getMessageId(),
            payload.getEventId(),
            lagMs
        );

        roomBroadcaster.sendToRoom(
                payload.getRoomId(),
                RealtimeWsEvent.builder()
                        .type(message.getEventType())
                        .payload(payload)
                        .build()
        );
    }
}

