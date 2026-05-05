package com.example.chat.modules.message.infrastructure.redis;


import com.example.chat.constants.ChatRedisChannels;
import com.example.chat.modules.room.dto.RoomMessagePinEventPayload;
import com.example.common.integration.chat.*;
import com.example.common.redis.publisher.RedisEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;


@Component
@RequiredArgsConstructor
@Slf4j
public class ChatRedisPublisher {

    private final RedisEventPublisher redisPublisher;
    private final RedisMessageFactory redisMessageFactory;

    public void publishMessageSent(ChatMessagePayload payload) {

        publish(
                payload.getRoomId(),
                ChatEventType.MESSAGE_SENT.value(),
                payload
        );
    }

        public void publishMessageSent(ChatMessagePayload payload, String eventId, String correlationId) {

                publish(
                                payload.getRoomId(),
                                ChatEventType.MESSAGE_SENT.value(),
                                payload,
                                eventId,
                                correlationId
                );
        }

    public void publishMessageEdited(MessageUpdatedPayload payload) {

        publish(
                payload.getRoomId(),
                ChatEventType.MESSAGE_EDITED.value(),
                payload
        );
    }

        public void publishMessageEdited(MessageUpdatedPayload payload, String eventId, String correlationId) {

                publish(
                                payload.getRoomId(),
                                ChatEventType.MESSAGE_EDITED.value(),
                                payload,
                                eventId,
                                correlationId
                );
        }

    public void publishMessageDeleted(MessageDeletedPayload payload) {

        publish(
                payload.getRoomId(),
                ChatEventType.MESSAGE_DELETED.value(),
                payload
        );
    }

        public void publishMessageDeleted(MessageDeletedPayload payload, String eventId, String correlationId) {

                publish(
                                payload.getRoomId(),
                                ChatEventType.MESSAGE_DELETED.value(),
                                payload,
                                eventId,
                                correlationId
                );
        }

    public void publishReactionUpdated(
            ReactionPayload payload
    ) {

        publish(
                payload.getRoomId(),
                ChatEventType.REACTION_UPDATED.value(),
                payload
        );
    }

        public void publishReactionUpdated(ReactionPayload payload, String eventId, String correlationId) {

                publish(
                                payload.getRoomId(),
                                ChatEventType.REACTION_UPDATED.value(),
                                payload,
                                eventId,
                                correlationId
                );
        }

    public void publishMessagePinned(
            RoomMessagePinEventPayload payload
    ) {

        log.info(
                "[realtime-fanout] publish pin event roomId={} messageId={} eventId={}",
                payload.getRoomId(),
                payload.getMessageId(),
                payload.getEventId()
        );

        publish(
                payload.getRoomId(),
                ChatEventType.MESSAGE_PINNED.value(),
                payload
        );
    }

    public void publishMessageUnpinned(
            RoomMessagePinEventPayload payload
    ) {

        log.info(
                "[realtime-fanout] publish unpin event roomId={} messageId={} eventId={}",
                payload.getRoomId(),
                payload.getMessageId(),
                payload.getEventId()
        );

        publish(
                payload.getRoomId(),
                ChatEventType.MESSAGE_UNPINNED.value(),
                payload
        );
    }

    private void publish(
            UUID roomId,
            String eventType,
            Object payload
    ) {
        publish(roomId, eventType, payload, null, null);
    }

    private void publish(
            UUID roomId,
            String eventType,
            Object payload,
            String eventId,
            String correlationId
    ) {

        String channel = ChatRedisChannels.roomChannel(roomId);

        redisPublisher.publish(
                channel,
                redisMessageFactory.create(
                        eventType,
                        payload,
                        eventId,
                        correlationId
                )
        );
    }
}