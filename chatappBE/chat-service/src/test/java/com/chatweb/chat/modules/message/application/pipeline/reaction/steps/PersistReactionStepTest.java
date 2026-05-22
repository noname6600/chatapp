package com.chatweb.chat.modules.message.application.pipeline.reaction.steps;

import com.chatweb.chat.modules.message.application.pipeline.reaction.ToggleReactionContext;
import com.chatweb.chat.modules.message.domain.entity.ChatMessage;
import com.chatweb.chat.modules.message.domain.entity.ChatReaction;
import com.chatweb.chat.modules.message.domain.enums.MessageType;
import com.chatweb.chat.modules.message.domain.repository.ChatMessageRepository;
import com.chatweb.chat.modules.message.domain.repository.ChatReactionRepository;
import com.chatweb.chat.modules.room.service.RoomMembershipGuard;
import com.chatweb.common.core.exception.BusinessException;
import com.chatweb.common.core.exception.CommonErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersistReactionStepTest {

    @Mock
    private ChatReactionRepository reactionRepository;

        @Mock
        private ChatMessageRepository messageRepository;

        @Mock
        private RoomMembershipGuard roomMembershipGuard;

    @InjectMocks
    private PersistReactionStep step;

    @Test
        void execute_withoutExistingReaction_addsReactionAndSetsCreatedAt() {
        ToggleReactionContext context = new ToggleReactionContext();
        context.setMessageId(UUID.randomUUID());
        context.setUserId(UUID.randomUUID());
        context.setEmoji("ðŸ”¥");

        Instant createdAt = Instant.now();
        ChatReaction saved = ChatReaction.builder()
                .id(UUID.randomUUID())
                .messageId(context.getMessageId())
                .userId(context.getUserId())
                .emoji(context.getEmoji())
                .createdAt(createdAt)
                .build();

        ChatMessage message = ChatMessage.builder()
                .id(context.getMessageId())
                .roomId(UUID.randomUUID())
                .senderId(UUID.randomUUID())
                .seq(1L)
                .type(MessageType.TEXT)
                .deleted(false)
                .build();

        when(messageRepository.findById(context.getMessageId())).thenReturn(Optional.of(message));

        when(reactionRepository.findByMessageIdAndUserIdAndEmoji(
                context.getMessageId(),
                context.getUserId(),
                context.getEmoji()
        )).thenReturn(Optional.empty());
        when(reactionRepository.save(any(ChatReaction.class))).thenReturn(saved);

        step.execute(context);

        ArgumentCaptor<ChatReaction> captor = ArgumentCaptor.forClass(ChatReaction.class);
        verify(reactionRepository).save(captor.capture());
        verify(roomMembershipGuard).ensureRoomMember(message.getRoomId(), context.getUserId());
        verify(reactionRepository, never()).deleteByMessageIdAndUserIdAndEmoji(
                context.getMessageId(),
                context.getUserId(),
                context.getEmoji()
        );

        assertThat(captor.getValue().getCreatedAt()).isNull();
        assertThat(context.isRemoved()).isFalse();
        assertThat(context.getReactionCreatedAt()).isEqualTo(createdAt);
    }

    @Test
        void execute_withExistingReaction_removesReactionAndSetsCreatedAt() {
        ToggleReactionContext context = new ToggleReactionContext();
        context.setMessageId(UUID.randomUUID());
        context.setUserId(UUID.randomUUID());
        context.setEmoji("ðŸ”¥");

        Instant createdAt = Instant.now().minusSeconds(10);
        ChatReaction existing = ChatReaction.builder()
                .id(UUID.randomUUID())
                .messageId(context.getMessageId())
                .userId(context.getUserId())
                .emoji(context.getEmoji())
                .createdAt(createdAt)
                .build();

        ChatMessage message = ChatMessage.builder()
                .id(context.getMessageId())
                .roomId(UUID.randomUUID())
                .senderId(UUID.randomUUID())
                .seq(1L)
                .type(MessageType.TEXT)
                .deleted(false)
                .build();

        when(messageRepository.findById(context.getMessageId())).thenReturn(Optional.of(message));

        when(reactionRepository.findByMessageIdAndUserIdAndEmoji(
                context.getMessageId(),
                context.getUserId(),
                context.getEmoji()
        )).thenReturn(Optional.of(existing));

        step.execute(context);

        verify(reactionRepository).deleteByMessageIdAndUserIdAndEmoji(
                context.getMessageId(),
                context.getUserId(),
                context.getEmoji()
        );
        verify(roomMembershipGuard).ensureRoomMember(message.getRoomId(), context.getUserId());
        verify(reactionRepository, never()).save(any(ChatReaction.class));

        assertThat(context.isRemoved()).isTrue();
        assertThat(context.getReactionCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void execute_duplicateAddPath_doesNotDeleteAndAddsOnce() {
        ToggleReactionContext context = new ToggleReactionContext();
        context.setMessageId(UUID.randomUUID());
        context.setUserId(UUID.randomUUID());
        context.setEmoji("ðŸ˜Ž");

        ChatReaction saved = ChatReaction.builder()
                .id(UUID.randomUUID())
                .messageId(context.getMessageId())
                .userId(context.getUserId())
                .emoji(context.getEmoji())
                .createdAt(Instant.now())
                .build();

        ChatMessage message = ChatMessage.builder()
                .id(context.getMessageId())
                .roomId(UUID.randomUUID())
                .senderId(UUID.randomUUID())
                .seq(1L)
                .type(MessageType.TEXT)
                .deleted(false)
                .build();

        when(messageRepository.findById(context.getMessageId())).thenReturn(Optional.of(message));

        when(reactionRepository.findByMessageIdAndUserIdAndEmoji(
                context.getMessageId(),
                context.getUserId(),
                context.getEmoji()
        )).thenReturn(Optional.empty());
        when(reactionRepository.save(any(ChatReaction.class))).thenReturn(saved);

        step.execute(context);

        verify(reactionRepository).save(any(ChatReaction.class));
        verify(reactionRepository, never()).deleteByMessageIdAndUserIdAndEmoji(
                context.getMessageId(),
                context.getUserId(),
                context.getEmoji()
        );
        assertThat(context.isRemoved()).isFalse();
    }

    @Test
    void execute_duplicateRemovePath_doesNotSaveAndRemovesOnce() {
        ToggleReactionContext context = new ToggleReactionContext();
        context.setMessageId(UUID.randomUUID());
        context.setUserId(UUID.randomUUID());
        context.setEmoji("ðŸ˜Ž");

        ChatReaction existing = ChatReaction.builder()
                .id(UUID.randomUUID())
                .messageId(context.getMessageId())
                .userId(context.getUserId())
                .emoji(context.getEmoji())
                .createdAt(Instant.now())
                .build();

        ChatMessage message = ChatMessage.builder()
                .id(context.getMessageId())
                .roomId(UUID.randomUUID())
                .senderId(UUID.randomUUID())
                .seq(1L)
                .type(MessageType.TEXT)
                .deleted(false)
                .build();

        when(messageRepository.findById(context.getMessageId())).thenReturn(Optional.of(message));

        when(reactionRepository.findByMessageIdAndUserIdAndEmoji(
                context.getMessageId(),
                context.getUserId(),
                context.getEmoji()
        )).thenReturn(Optional.of(existing));

        step.execute(context);

        verify(reactionRepository).deleteByMessageIdAndUserIdAndEmoji(
                context.getMessageId(),
                context.getUserId(),
                context.getEmoji()
        );
        verify(reactionRepository, never()).save(any(ChatReaction.class));
        assertThat(context.isRemoved()).isTrue();
    }

        @Test
        void execute_throwsForbidden_whenUserNotMemberOfMessageRoom() {
                ToggleReactionContext context = new ToggleReactionContext();
                context.setMessageId(UUID.randomUUID());
                context.setUserId(UUID.randomUUID());
                context.setEmoji("ðŸ”¥");

                ChatMessage message = ChatMessage.builder()
                                .id(context.getMessageId())
                                .roomId(UUID.randomUUID())
                                .senderId(UUID.randomUUID())
                                .seq(1L)
                                .type(MessageType.TEXT)
                                .deleted(false)
                                .build();

                when(messageRepository.findById(context.getMessageId())).thenReturn(Optional.of(message));
                doThrow(new BusinessException(CommonErrorCode.FORBIDDEN, "Not a room member"))
                                .when(roomMembershipGuard)
                                .ensureRoomMember(message.getRoomId(), context.getUserId());

                assertThatThrownBy(() -> step.execute(context))
                                .isInstanceOf(BusinessException.class)
                                .hasMessageContaining("Not a room member");

                verify(reactionRepository, never()).save(any(ChatReaction.class));
                verify(reactionRepository, never()).deleteByMessageIdAndUserIdAndEmoji(any(), any(), any());
        }
}
