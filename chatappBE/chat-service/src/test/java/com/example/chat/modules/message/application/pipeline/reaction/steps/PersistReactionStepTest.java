package com.example.chat.modules.message.application.pipeline.reaction.steps;

import com.example.chat.modules.message.application.pipeline.reaction.ToggleReactionContext;
import com.example.chat.modules.message.domain.entity.ChatReaction;
import com.example.chat.modules.message.domain.repository.ChatReactionRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersistReactionStepTest {

    @Mock
    private ChatReactionRepository reactionRepository;

    @InjectMocks
    private PersistReactionStep step;

    @Test
        void execute_withoutExistingReaction_addsReactionAndSetsCreatedAt() {
        ToggleReactionContext context = new ToggleReactionContext();
        context.setMessageId(UUID.randomUUID());
        context.setUserId(UUID.randomUUID());
        context.setEmoji("🔥");

        Instant createdAt = Instant.now();
        ChatReaction saved = ChatReaction.builder()
                .id(UUID.randomUUID())
                .messageId(context.getMessageId())
                .userId(context.getUserId())
                .emoji(context.getEmoji())
                .createdAt(createdAt)
                .build();

        when(reactionRepository.findByMessageIdAndUserIdAndEmoji(
                context.getMessageId(),
                context.getUserId(),
                context.getEmoji()
        )).thenReturn(Optional.empty());
        when(reactionRepository.save(any(ChatReaction.class))).thenReturn(saved);

        step.execute(context);

        ArgumentCaptor<ChatReaction> captor = ArgumentCaptor.forClass(ChatReaction.class);
        verify(reactionRepository).save(captor.capture());
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
        context.setEmoji("🔥");

        Instant createdAt = Instant.now().minusSeconds(10);
        ChatReaction existing = ChatReaction.builder()
                .id(UUID.randomUUID())
                .messageId(context.getMessageId())
                .userId(context.getUserId())
                .emoji(context.getEmoji())
                .createdAt(createdAt)
                .build();

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
        assertThat(context.getReactionCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void execute_duplicateAddPath_doesNotDeleteAndAddsOnce() {
        ToggleReactionContext context = new ToggleReactionContext();
        context.setMessageId(UUID.randomUUID());
        context.setUserId(UUID.randomUUID());
        context.setEmoji("😎");

        ChatReaction saved = ChatReaction.builder()
                .id(UUID.randomUUID())
                .messageId(context.getMessageId())
                .userId(context.getUserId())
                .emoji(context.getEmoji())
                .createdAt(Instant.now())
                .build();

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
        context.setEmoji("😎");

        ChatReaction existing = ChatReaction.builder()
                .id(UUID.randomUUID())
                .messageId(context.getMessageId())
                .userId(context.getUserId())
                .emoji(context.getEmoji())
                .createdAt(Instant.now())
                .build();

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
}
