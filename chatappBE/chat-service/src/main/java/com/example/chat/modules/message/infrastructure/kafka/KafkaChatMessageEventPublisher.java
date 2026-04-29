package com.example.chat.modules.message.infrastructure.kafka;


import com.example.chat.modules.message.application.service.IMessageEventPublisher;
import com.example.chat.modules.message.domain.entity.ChatAttachment;
import com.example.chat.modules.message.domain.entity.ChatMessage;
import com.example.chat.modules.message.event.factory.ChatMessagePayloadFactory;
import com.example.chat.modules.message.event.factory.MessageDeletedPayloadFactory;
import com.example.chat.modules.message.event.factory.MessageUpdatedPayloadFactory;
import com.example.chat.modules.room.entity.Room;
import com.example.chat.modules.room.entity.RoomMember;
import com.example.chat.modules.room.enums.RoomType;
import com.example.chat.modules.room.repository.RoomMemberRepository;
import com.example.chat.modules.room.repository.RoomRepository;
import com.example.common.integration.chat.ChatMessagePayload;
import com.example.common.integration.chat.MessageDeletedPayload;
import com.example.common.integration.chat.MessageUpdatedPayload;
import com.example.common.integration.kafka.KafkaTopics;
import com.example.common.kafka.api.KafkaEventPublisher;
import com.example.common.integration.kafka.event.ChatMessageDeletedEvent;
import com.example.common.integration.kafka.event.ChatMessageEditedEvent;
import com.example.common.integration.kafka.event.ChatMessageSentEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class KafkaChatMessageEventPublisher
        implements IMessageEventPublisher {

    private final KafkaEventPublisher kafkaEventPublisher;

    private final ChatMessagePayloadFactory payloadFactory;
    private final MessageUpdatedPayloadFactory updatedFactory;
    private final MessageDeletedPayloadFactory deletedFactory;
    private final RoomMemberRepository roomMemberRepository;
    private final RoomRepository roomRepository;

    @Value("${spring.application.name}")
    private String sourceService;

    @Override
    public void publishMessageCreated(
            ChatMessage message,
            List<ChatAttachment> attachments,
            List<UUID> mentionedUserIds
    ) {
        // Fetch all room members and exclude the sender
        List<UUID> recipientUserIds = roomMemberRepository.findByRoomId(message.getRoomId())
                .stream()
                .map(RoomMember::getUserId)
                .filter(userId -> !userId.equals(message.getSenderId()))
                .toList();

        boolean isDirect = roomRepository.findById(message.getRoomId())
                .map(Room::getType)
                .map(type -> type == RoomType.PRIVATE)
                .orElse(false);

        ChatMessagePayload payload =
                payloadFactory.from(message, attachments, mentionedUserIds, recipientUserIds, isDirect);

        ChatMessageSentEvent event =
                ChatMessageSentEvent.from(sourceService, payload);

        kafkaEventPublisher.publish(
                KafkaTopics.CHAT_MESSAGE_SENT,
                message.getRoomId().toString(),
                event
        );
    }

    @Override
    public void publishMessageEdited(ChatMessage message) {

        MessageUpdatedPayload payload =
                updatedFactory.from(message);

        ChatMessageEditedEvent event =
                ChatMessageEditedEvent.from(sourceService, payload);

        kafkaEventPublisher.publish(
                KafkaTopics.CHAT_MESSAGE_EDITED,
                message.getRoomId().toString(),
                event
        );
    }

    @Override
    public void publishMessageDeleted(ChatMessage message) {

        MessageDeletedPayload payload =
                deletedFactory.from(
                        message,
                        message.getDeletedBy()
                );

        ChatMessageDeletedEvent event =
                ChatMessageDeletedEvent.from(sourceService, payload);

        kafkaEventPublisher.publish(
                KafkaTopics.CHAT_MESSAGE_DELETED,
                message.getRoomId().toString(),
                event
        );
    }
}