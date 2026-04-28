package com.example.chat.modules.message.application.pipeline.reaction.steps;

import com.example.chat.modules.message.application.pipeline.reaction.ToggleReactionContext;
import com.example.chat.modules.message.application.service.IReactionEventPublisher;
import com.example.chat.modules.message.domain.entity.ChatMessage;
import com.example.chat.modules.message.domain.repository.ChatMessageRepository;
import com.example.chat.modules.message.domain.enums.MessageType;
import com.example.chat.modules.room.repository.RoomMemberRepository;
import com.example.common.integration.chat.ReactionPayload;
import com.example.common.integration.enums.ReactionAction;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublishReactionEventStepTest {

    @Mock
    private IReactionEventPublisher eventPublisher;

    @Mock
    private ChatMessageRepository messageRepository;

    @Mock
    private RoomMemberRepository roomMemberRepository;

    @InjectMocks
    private PublishReactionEventStep step;

    @Test
    void execute_publishUsesContextReactionCreatedAt() {
        Instant createdAt = Instant.now();
        UUID messageId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();

        ToggleReactionContext context = new ToggleReactionContext();
        context.setMessageId(messageId);
        context.setUserId(UUID.randomUUID());
        context.setEmoji("👍");
        context.setRemoved(false);
        context.setReactionCreatedAt(createdAt);

        ChatMessage message = ChatMessage.builder()
                .id(messageId)
                .roomId(roomId)
                .senderId(UUID.randomUUID())
                .seq(1L)
                .type(MessageType.TEXT)
                .build();

        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));
        when(roomMemberRepository.findByRoomIdAndUserId(roomId, context.getUserId()))
            .thenReturn(Optional.empty());

        step.execute(context);

        ArgumentCaptor<ReactionPayload> captor = ArgumentCaptor.forClass(ReactionPayload.class);
        verify(eventPublisher).publishReactionUpdated(captor.capture());

        ReactionPayload payload = captor.getValue();
        assertThat(payload.getCreatedAt()).isEqualTo(createdAt);
        assertThat(payload.getAction()).isEqualTo(ReactionAction.ADD);
        assertThat(payload.getRoomId()).isEqualTo(roomId);
    }

    @Test
    void execute_withoutReactionCreatedAt_throws() {
        ToggleReactionContext context = new ToggleReactionContext();
        context.setMessageId(UUID.randomUUID());
        context.setUserId(UUID.randomUUID());
        context.setEmoji("👍");

        assertThatThrownBy(() -> step.execute(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("createdAt");
    }

    @Test
    void execute_withoutMessageForReactionEvent_throws() {
        UUID messageId = UUID.randomUUID();

        ToggleReactionContext context = new ToggleReactionContext();
        context.setMessageId(messageId);
        context.setUserId(UUID.randomUUID());
        context.setEmoji("👍");
        context.setReactionCreatedAt(Instant.now());

        when(messageRepository.findById(messageId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> step.execute(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Message not found");
    }
}
