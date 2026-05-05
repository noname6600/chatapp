package com.example.chat.realtime.subscriber;

import com.example.common.integration.chat.ChatEventType;
import com.example.common.integration.chat.ChatMessagePayload;
import com.example.common.websocket.protocol.RealtimeWsEvent;
import com.example.common.redis.message.RedisMessage;
import com.example.common.websocket.session.IRoomBroadcaster;
import com.example.common.websocket.session.IUserBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatMessageSentRedisSubscriber
        implements RedisEventSubscriber<RedisMessage<ChatMessagePayload>> {

    private final IRoomBroadcaster roomBroadcaster;
    private final IUserBroadcaster userBroadcaster;
    private final RealtimeEventDedupeGuard dedupeGuard;

    @Override
    public String eventType() {
        return ChatEventType.MESSAGE_SENT.value();
    }

    @Override
    public void onMessage(RedisMessage<ChatMessagePayload> message) {
        if (message == null || message.getPayload() == null) {
            return;
        }
        if (dedupeGuard.isDuplicateKey(message.getMessageId())) {
            log.info("[realtime-fanout] duplicate redis message-sent dropped messageId={}", message.getMessageId());
            return;
        }

        ChatMessagePayload payload = message.getPayload();

        RealtimeWsEvent wsEvent = RealtimeWsEvent.builder()
                .type(message.getEventType())
                .payload(payload)
                .build();

        roomBroadcaster.sendToRoom(
                payload.getRoomId(),
                wsEvent
        );

        // Focused DM fix: receiver may not have joined the room yet,
        // so also fan out to recipient user sessions for direct chats.
        if (payload.isDirect()
            && payload.getRecipientUserIds() != null
            && !payload.getRecipientUserIds().isEmpty()) {
            payload.getRecipientUserIds().forEach(userId ->
                userBroadcaster.sendToUser(userId, wsEvent));
        }
    }
}
