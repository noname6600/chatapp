package com.example.chat.modules.message.application.service.impl;

import com.example.chat.modules.message.application.service.IMessageEventPublisher;
import com.example.chat.modules.message.domain.entity.ChatMessage;
import com.example.chat.modules.message.domain.enums.MessageType;
import com.example.chat.modules.message.domain.enums.SystemEventType;
import com.example.chat.modules.message.domain.repository.ChatMessageRepository;
import com.example.chat.modules.message.domain.service.IMessageSequenceService;
import com.example.chat.modules.room.entity.RoomMember;
import com.example.chat.modules.room.repository.RoomMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemMessageServiceTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private RoomMemberRepository roomMemberRepository;

    @Mock
    private IMessageSequenceService messageSequenceService;

    @Mock
    private IMessageEventPublisher messageEventPublisher;

    @InjectMocks
    private SystemMessageService service;

    @Test
    void sendSystemMessage_pin_usesSharedSequenceServiceAndPublishes() {
        UUID roomId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID targetMessageId = UUID.randomUUID();

        when(roomMemberRepository.findByRoomIdAndUserId(roomId, actorId))
                .thenReturn(Optional.of(RoomMember.builder().displayName("Alice").build()));
        when(messageSequenceService.nextSeq(roomId)).thenReturn(42L);
        when(chatMessageRepository.save(any(ChatMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.sendSystemMessage(roomId, SystemEventType.PIN, actorId, targetMessageId);

        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository).save(messageCaptor.capture());

        ChatMessage saved = messageCaptor.getValue();
        assertThat(saved.getRoomId()).isEqualTo(roomId);
        assertThat(saved.getSenderId()).isEqualTo(actorId);
        assertThat(saved.getTargetMessageId()).isEqualTo(targetMessageId);
        assertThat(saved.getType()).isEqualTo(MessageType.SYSTEM);
        assertThat(saved.getSystemEventType()).isEqualTo(SystemEventType.PIN);
        assertThat(saved.getSeq()).isEqualTo(42L);
        assertThat(saved.getContent()).isEqualTo("Alice pinned a message. See all pinned messages.");

        verify(messageSequenceService).nextSeq(roomId);
        verify(messageEventPublisher).publishMessageCreated(eq(saved), eq(List.of()), eq(List.of()));
    }

    @Test
    void sendSystemMessage_pin_thenSharedCounterForUserMessage_isMonotonicAndNonColliding() {
        UUID roomId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        when(roomMemberRepository.findByRoomIdAndUserId(roomId, actorId))
                .thenReturn(Optional.of(RoomMember.builder().displayName("Alice").build()));

        AtomicLong sequence = new AtomicLong(100L);
        when(messageSequenceService.nextSeq(roomId)).thenAnswer(invocation -> sequence.incrementAndGet());
        when(chatMessageRepository.save(any(ChatMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.sendSystemMessage(roomId, SystemEventType.PIN, actorId, UUID.randomUUID());
        service.sendSystemMessage(roomId, SystemEventType.PIN, actorId, UUID.randomUUID());
        service.sendSystemMessage(roomId, SystemEventType.PIN, actorId, UUID.randomUUID());

        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, times(3)).save(messageCaptor.capture());

        List<ChatMessage> savedMessages = messageCaptor.getAllValues();
        List<Long> systemSeqs = savedMessages.stream().map(ChatMessage::getSeq).toList();
        assertThat(systemSeqs).containsExactly(101L, 102L, 103L);

        long userMessageSeq = messageSequenceService.nextSeq(roomId);
        assertThat(userMessageSeq).isGreaterThan(systemSeqs.get(2));
        assertThat(Set.copyOf(systemSeqs)).doesNotContain(userMessageSeq);
        assertThat(userMessageSeq).isEqualTo(104L);
    }
}
