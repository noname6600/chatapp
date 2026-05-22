package com.chatweb.chat.modules.message.infrastructure.redis;


import com.chatweb.common.redis.channel.RedisChannels;
import com.chatweb.common.integration.chat.*;
import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.event.EventMetadata;
import com.chatweb.common.redis.publisher.RedisEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;


@Component
@RequiredArgsConstructor
@Slf4j
public class ChatRedisPublisher {

    private final RedisEventPublisher redisPublisher;

    @Value("${spring.application.name}")
    private String sourceService;

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
            MessagePinPayload payload
    ) {

        log.info(
                "[realtime-fanout] publish pin event roomId={} messageId={} actorUserId={}",
                payload.getRoomId(),
                payload.getMessageId(),
                payload.getActorUserId()
        );

        publish(
                payload.getRoomId(),
                ChatEventType.MESSAGE_PINNED.value(),
                payload
        );
    }

    public void publishMessageUnpinned(
            MessagePinPayload payload
    ) {

        log.info(
                "[realtime-fanout] publish unpin event roomId={} messageId={} actorUserId={}",
                payload.getRoomId(),
                payload.getMessageId(),
                payload.getActorUserId()
        );

        publish(
                payload.getRoomId(),
                ChatEventType.MESSAGE_UNPINNED.value(),
                payload
        );
    }

        public void publishRoomRealtimeEvent(UUID roomId, String eventType, Object payload) {
                publish(roomId, eventType, payload);
        }

    private <T> void publish(
            UUID roomId,
            String eventType,
            T payload
    ) {
        publish(roomId, eventType, payload, null, null);
    }

    private <T> void publish(
            UUID roomId,
            String eventType,
            T payload,
            String eventId,
            String correlationId
    ) {

        String channel = RedisChannels.chatRoom(roomId);
        String resolvedEventId = eventId != null ? eventId : UUID.randomUUID().toString();
        String resolvedCorrelationId = correlationId != null ? correlationId : resolvedEventId;

        EventMetadata metadata = new EventMetadata(
            resolvedEventId,
            eventType,
            sourceService,
            Instant.now(),
            resolvedCorrelationId
        );

        EventEnvelope<T> envelope = new EventEnvelope<>(metadata, payload);

        redisPublisher.publish(channel, envelope);
    }
}