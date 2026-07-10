package com.chatweb.chat.modules.message.application.service.impl;

import com.chatweb.chat.modules.message.application.service.IMessageEventPublisher;
import com.chatweb.chat.modules.message.application.service.ISystemMessageService;
import com.chatweb.chat.modules.message.domain.entity.ChatMessage;
import com.chatweb.chat.modules.message.domain.enums.MessageType;
import com.chatweb.chat.modules.message.domain.enums.SystemEventType;
import com.chatweb.chat.modules.message.domain.repository.ChatMessageRepository;
import com.chatweb.chat.modules.message.domain.service.IMessageSequenceService;
import com.chatweb.chat.modules.room.entity.RoomMember;
import com.chatweb.chat.modules.room.repository.RoomMemberRepository;
import com.chatweb.chat.support.TransactionPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class SystemMessageService implements ISystemMessageService {

    private final ChatMessageRepository chatMessageRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final IMessageSequenceService messageSequenceService;
    private final IMessageEventPublisher messageEventPublisher;

    @Override
    @Transactional
    public void sendRawSystemMessage(UUID roomId, UUID senderId, SystemEventType eventType, String content) {
        long nextSeq = messageSequenceService.nextSeq(roomId);
        ChatMessage msg = ChatMessage.builder()
                .id(UUID.randomUUID())
                .roomId(roomId)
                .senderId(senderId)
                .seq(nextSeq)
                .type(MessageType.SYSTEM)
                .content(content)
                .systemEventType(eventType)
                .actorUserId(senderId)
                .deleted(false)
                .createdAt(Instant.now())
                .build();
        chatMessageRepository.save(msg);
        TransactionPublisher.publishAfterCommit(() ->
                messageEventPublisher.publishMessageCreated(msg, List.of(), List.of())
        );
    }

    @Override
    public void sendSystemMessage(
            UUID roomId,
            SystemEventType eventType,
            UUID actorUserId,
            UUID targetMessageId
    ) {
        String actorName = roomMemberRepository.findByRoomIdAndUserId(roomId, actorUserId)
                .map(RoomMember::getDisplayName)
                .filter(name -> name != null && !name.isBlank())
                .orElse("User");

        String content = switch (eventType) {
            case JOIN -> actorName + " joined the group";
            case PIN -> actorName + " pinned a message. See all pinned messages.";
        };

        long nextSeq = messageSequenceService.nextSeq(roomId);

        ChatMessage systemMessage = ChatMessage.builder()
                .id(UUID.randomUUID())
                .roomId(roomId)
                .senderId(actorUserId)
                .seq(nextSeq)
                .type(MessageType.SYSTEM)
                .content(content)
                .systemEventType(eventType)
                .actorUserId(actorUserId)
                .targetMessageId(targetMessageId)
                .deleted(false)
                .createdAt(Instant.now())
                .build();

        chatMessageRepository.save(systemMessage);
        TransactionPublisher.publishAfterCommit(() ->
                messageEventPublisher.publishMessageCreated(systemMessage, List.of(), List.of())
        );
    }
}
