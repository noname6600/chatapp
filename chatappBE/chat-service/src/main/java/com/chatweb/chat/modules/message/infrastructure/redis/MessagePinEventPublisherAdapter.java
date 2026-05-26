package com.chatweb.chat.modules.message.infrastructure.redis;

import com.chatweb.chat.modules.message.application.service.IMessagePinEventPublisher;
import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.event.EventMetadata;
import com.chatweb.common.event.TraceContext;
import com.chatweb.common.integration.chat.ChatEventType;
import com.chatweb.common.integration.chat.MessagePinPayload;
import com.chatweb.common.kafka.producer.KafkaEventPublisher;
import com.chatweb.common.kafka.topic.KafkaTopics;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MessagePinEventPublisherAdapter implements IMessagePinEventPublisher {

    private final ChatRedisPublisher chatRedisPublisher;
    private final KafkaEventPublisher kafkaEventPublisher;

    @Value("${spring.application.name}")
    private String sourceService;

    @Override
    public void publishMessagePinned(UUID roomId, UUID messageId, UUID actorId, Instant pinnedAt) {
        MessagePinPayload payload = MessagePinPayload.builder()
                .roomId(roomId).messageId(messageId).actorUserId(actorId).pinnedAt(pinnedAt)
                .build();
        chatRedisPublisher.publishRoomRealtimeEvent(roomId, ChatEventType.MESSAGE_PINNED.value(), payload);
        publishToKafka(roomId.toString(), ChatEventType.MESSAGE_PINNED.value(), payload);
    }

    @Override
    public void publishMessageUnpinned(UUID roomId, UUID messageId, UUID actorId, Instant unpinnedAt) {
        MessagePinPayload payload = MessagePinPayload.builder()
                .roomId(roomId).messageId(messageId).actorUserId(actorId).pinnedAt(unpinnedAt)
                .build();
        chatRedisPublisher.publishRoomRealtimeEvent(roomId, ChatEventType.MESSAGE_UNPINNED.value(), payload);
        publishToKafka(roomId.toString(), ChatEventType.MESSAGE_UNPINNED.value(), payload);
    }

    private <T> void publishToKafka(String key, String eventType, T payload) {
        String eventId = UUID.randomUUID().toString();
        kafkaEventPublisher.publish(KafkaTopics.TOPIC_CHAT_MESSAGE_EVENTS, key, new EventEnvelope<>(
                new EventMetadata(eventId, eventType, sourceService, Instant.now(), TraceContext.correlationIdOrEventId(eventId)),
                payload
        ));
    }
}
