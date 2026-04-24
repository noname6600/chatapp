package com.example.chat.modules.message.application.service.impl;

import com.example.chat.modules.message.application.service.IMessageEventPublisher;
import com.example.chat.modules.message.application.service.ISystemMessageService;
import com.example.chat.modules.message.domain.entity.ChatMessage;
import com.example.chat.modules.message.domain.enums.MessageType;
import com.example.chat.modules.message.domain.enums.SystemEventType;
import com.example.chat.modules.message.domain.repository.ChatMessageRepository;
import com.example.chat.modules.room.entity.RoomMember;
import com.example.chat.modules.room.repository.RoomMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class SystemMessageService implements ISystemMessageService {

    private final ChatMessageRepository chatMessageRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final IMessageEventPublisher messageEventPublisher;

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

        Long nextSeq = Optional.ofNullable(chatMessageRepository.findMaxSeqByRoomId(roomId))
                .orElse(0L) + 1;

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
                publishSystemMessageAfterCommit(systemMessage);
    }

        private void publishSystemMessageAfterCommit(ChatMessage systemMessage) {
                if (TransactionSynchronizationManager.isSynchronizationActive()) {
                        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                                @Override
                                public void afterCommit() {
                                        messageEventPublisher.publishMessageCreated(systemMessage, List.of(), List.of());
                                }
                        });
                        return;
                }

                messageEventPublisher.publishMessageCreated(systemMessage, List.of(), List.of());
        }
}
