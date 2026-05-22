package com.chatweb.chat.modules.message.infrastructure.redis;

import com.chatweb.chat.modules.message.application.service.IMessageEventPublisher;
import com.chatweb.chat.modules.message.application.service.IReactionEventPublisher;
import com.chatweb.chat.modules.message.domain.entity.ChatAttachment;
import com.chatweb.chat.modules.message.domain.entity.ChatMessage;
import com.chatweb.chat.modules.message.event.factory.ChatMessagePayloadFactory;
import com.chatweb.chat.modules.message.event.factory.MessageDeletedPayloadFactory;
import com.chatweb.chat.modules.message.event.factory.MessageUpdatedPayloadFactory;
import com.chatweb.chat.modules.room.enums.RoomType;
import com.chatweb.chat.modules.room.repository.RoomMemberRepository;
import com.chatweb.chat.modules.room.repository.RoomRepository;
import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.event.EventMetadata;
import com.chatweb.common.event.TraceContext;
import com.chatweb.common.integration.chat.ChatEventType;
import com.chatweb.common.integration.chat.ChatMessagePayload;
import com.chatweb.common.integration.chat.ReactionPayload;
import com.chatweb.common.kafka.topic.KafkaTopics;
import com.chatweb.common.kafka.producer.KafkaEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ChatMessageEventPublisherAdapter implements IMessageEventPublisher, IReactionEventPublisher {

    private final ChatRedisPublisher chatRedisPublisher;
    private final ChatMessagePayloadFactory chatMessagePayloadFactory;
    private final MessageUpdatedPayloadFactory messageUpdatedPayloadFactory;
    private final MessageDeletedPayloadFactory messageDeletedPayloadFactory;
    private final RoomMemberRepository roomMemberRepository;
    private final RoomRepository roomRepository;
    private final KafkaEventPublisher kafkaEventPublisher;

    @Value("${spring.application.name}")
    private String sourceService;

    @Override
    public void publishMessageCreated(
            ChatMessage message,
            List<ChatAttachment> attachments,
            List<UUID> mentionedUserIds
    ) {
        List<UUID> recipientUserIds = roomMemberRepository.findUserIdsByRoomId(message.getRoomId())
                .stream()
                .filter(userId -> !userId.equals(message.getSenderId()))
                .toList();

        boolean isDirect = roomRepository.findById(message.getRoomId())
                .map(room -> room.getType() == RoomType.PRIVATE)
                .orElse(false);

        String senderDisplayName = roomMemberRepository
                .findByRoomIdAndUserId(message.getRoomId(), message.getSenderId())
                .map(member -> member.getDisplayName())
                .orElse(null);

        ChatMessagePayload payload = chatMessagePayloadFactory.from(
                message,
                attachments,
                mentionedUserIds,
                recipientUserIds,
            isDirect
        );

        chatRedisPublisher.publishMessageSent(payload);
        publishToKafka(ChatEventType.MESSAGE_SENT.value(), message.getRoomId().toString(), payload);
    }

    @Override
    public void publishMessageEdited(ChatMessage message) {
        var payload = messageUpdatedPayloadFactory.from(message);
        chatRedisPublisher.publishMessageEdited(payload);
        publishToKafka(KafkaTopics.TOPIC_CHAT_MESSAGE_EVENTS, message.getRoomId().toString(), ChatEventType.MESSAGE_EDITED.value(), payload);
    }

    @Override
    public void publishMessageDeleted(ChatMessage message) {
        UUID deletedBy = message.getDeletedBy() != null ? message.getDeletedBy() : message.getSenderId();
        var payload = messageDeletedPayloadFactory.from(message, deletedBy);
        chatRedisPublisher.publishMessageDeleted(payload);
        publishToKafka(KafkaTopics.TOPIC_CHAT_MESSAGE_EVENTS, message.getRoomId().toString(), ChatEventType.MESSAGE_DELETED.value(), payload);
    }

    @Override
    public void publishReactionUpdated(ReactionPayload payload) {
        chatRedisPublisher.publishReactionUpdated(payload);
        publishToKafka(ChatEventType.REACTION_UPDATED.value(), payload.getRoomId().toString(), payload);
    }

    private <T> void publishToKafka(String eventType, String key, T payload) {
        String eventId = UUID.randomUUID().toString();
        EventEnvelope<T> envelope = new EventEnvelope<>(
            new EventMetadata(eventId, eventType, sourceService, Instant.now(), TraceContext.correlationIdOrEventId(eventId)),
                payload
        );
        kafkaEventPublisher.publish(eventType, key, envelope);
    }

    private <T> void publishToKafka(String topic, String key, String eventType, T payload) {
        String eventId = UUID.randomUUID().toString();
        EventEnvelope<T> envelope = new EventEnvelope<>(
                new EventMetadata(eventId, eventType, sourceService, Instant.now(), TraceContext.correlationIdOrEventId(eventId)),
                payload
        );
        kafkaEventPublisher.publish(topic, key, envelope);
    }
}
