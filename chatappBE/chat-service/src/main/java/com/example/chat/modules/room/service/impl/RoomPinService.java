package com.example.chat.modules.room.service.impl;

import com.example.chat.modules.message.application.dto.response.MessageResponse;
import com.example.chat.modules.message.application.mapper.MessageMapper;
import com.example.chat.modules.message.application.service.ISystemMessageService;
import com.example.chat.modules.message.domain.entity.ChatAttachment;
import com.example.chat.modules.message.domain.entity.ChatMessage;
import com.example.chat.modules.message.domain.entity.RoomPinnedMessage;
import com.example.chat.modules.message.domain.enums.SystemEventType;
import com.example.chat.modules.message.infrastructure.redis.ChatRedisPublisher;
import com.example.chat.modules.message.domain.repository.ChatAttachmentRepository;
import com.example.chat.modules.message.domain.repository.ChatMessageRepository;
import com.example.chat.modules.message.domain.repository.RoomPinnedMessageRepository;
import com.example.chat.modules.room.dto.RoomMessagePinEventPayload;
import com.example.chat.modules.room.repository.RoomMemberRepository;
import com.example.chat.modules.room.service.IRoomPinService;
import com.example.common.integration.chat.ChatEventType;
import com.example.common.web.exception.BusinessException;
import com.example.common.web.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class RoomPinService implements IRoomPinService {

    private final RoomMemberRepository roomMemberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatAttachmentRepository chatAttachmentRepository;
    private final RoomPinnedMessageRepository roomPinnedMessageRepository;
        private final ISystemMessageService systemMessageService;
    private final MessageMapper messageMapper;
        private final ChatRedisPublisher chatRedisPublisher;

    @Override
    public void pinMessage(UUID roomId, UUID actorId, UUID messageId) {
        ensureMember(roomId, actorId);

        ChatMessage targetMessage = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Message not found"
                ));

        if (!roomId.equals(targetMessage.getRoomId())) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "Message does not belong to the room"
            );
        }

        if (Boolean.TRUE.equals(targetMessage.getDeleted())) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "Cannot pin deleted message"
            );
        }

        roomPinnedMessageRepository.findByRoomIdAndMessageId(roomId, messageId)
                .ifPresent(existing -> {
                    throw new BusinessException(
                            ErrorCode.CONFLICT,
                            "Message already pinned"
                    );
                });

        roomPinnedMessageRepository.save(
                RoomPinnedMessage.builder()
                        .id(UUID.randomUUID())
                        .roomId(roomId)
                        .messageId(messageId)
                        .pinnedBy(actorId)
                        .pinnedAt(Instant.now())
                        .build()
        );

        systemMessageService.sendSystemMessage(
                roomId,
                SystemEventType.PIN,
                actorId,
                messageId
        );
        publishPinRealtimeEventAfterCommit(roomId, messageId, actorId, ChatEventType.MESSAGE_PINNED);
    }

    @Override
    public void unpinMessage(UUID roomId, UUID actorId, UUID messageId) {
        ensureMember(roomId, actorId);

        RoomPinnedMessage existing = roomPinnedMessageRepository
                .findByRoomIdAndMessageId(roomId, messageId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Pinned message not found"
                ));

        roomPinnedMessageRepository.delete(existing);
        publishPinRealtimeEventAfterCommit(roomId, messageId, actorId, ChatEventType.MESSAGE_UNPINNED);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MessageResponse> getPinnedMessages(UUID roomId, UUID actorId) {
        ensureMember(roomId, actorId);

        List<RoomPinnedMessage> pins = roomPinnedMessageRepository
                .findByRoomIdOrderByPinnedAtDesc(roomId);

        if (pins.isEmpty()) {
            return List.of();
        }

        List<UUID> pinnedMessageIds = pins.stream()
                .map(RoomPinnedMessage::getMessageId)
                .toList();

        Map<UUID, ChatMessage> messagesById = chatMessageRepository.findAllById(pinnedMessageIds)
                .stream()
                .filter(message -> !Boolean.TRUE.equals(message.getDeleted()))
                .collect(Collectors.toMap(ChatMessage::getId, Function.identity()));

        Map<UUID, List<ChatAttachment>> attachmentsByMessageId = chatAttachmentRepository
                .findByMessageIdIn(pinnedMessageIds)
                .stream()
                .collect(Collectors.groupingBy(ChatAttachment::getMessageId));

        List<MessageResponse> responses = new ArrayList<>();

        for (RoomPinnedMessage pin : pins) {
            ChatMessage message = messagesById.get(pin.getMessageId());
            if (message == null) {
                continue;
            }

            responses.add(
                    messageMapper.toResponse(
                            message,
                            attachmentsByMessageId.getOrDefault(message.getId(), List.of()),
                            List.of()
                    )
            );
        }

        return responses;
    }

    private void ensureMember(UUID roomId, UUID actorId) {
        if (!roomMemberRepository.existsByRoomIdAndUserId(roomId, actorId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Not a room member");
        }
    }

    private void publishPinRealtimeEvent(
            UUID roomId,
            UUID messageId,
            UUID actorId,
            ChatEventType eventType
    ) {
        RoomMessagePinEventPayload payload = RoomMessagePinEventPayload.builder()
                                .eventId(UUID.randomUUID())
                .roomId(roomId)
                .messageId(messageId)
                .actorId(actorId)
                .occurredAt(java.time.Instant.now())
                .build();

                if (eventType == ChatEventType.MESSAGE_PINNED) {
                        chatRedisPublisher.publishMessagePinned(payload);
                        return;
                }

                if (eventType == ChatEventType.MESSAGE_UNPINNED) {
                        chatRedisPublisher.publishMessageUnpinned(payload);
                }
    }

        private void publishPinRealtimeEventAfterCommit(
                        UUID roomId,
                        UUID messageId,
                        UUID actorId,
                        ChatEventType eventType
        ) {
                if (TransactionSynchronizationManager.isSynchronizationActive()) {
                        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                                @Override
                                public void afterCommit() {
                                        publishPinRealtimeEvent(roomId, messageId, actorId, eventType);
                                }
                        });
                        return;
                }

                publishPinRealtimeEvent(roomId, messageId, actorId, eventType);
        }
}
