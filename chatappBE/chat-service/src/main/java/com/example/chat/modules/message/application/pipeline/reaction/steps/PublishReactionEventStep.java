package com.example.chat.modules.message.application.pipeline.reaction.steps;

import com.example.chat.modules.message.application.pipeline.reaction.ToggleReactionContext;
import com.example.chat.modules.message.application.service.IReactionEventPublisher;
import com.example.chat.modules.message.domain.entity.ChatMessage;
import com.example.chat.modules.message.domain.repository.ChatMessageRepository;
import com.example.common.core.pipeline.PipelineStep;
import com.example.common.integration.chat.ReactionPayload;
import com.example.common.integration.enums.ReactionAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PublishReactionEventStep
        implements PipelineStep<ToggleReactionContext> {

    private final IReactionEventPublisher eventPublisher;
    private final ChatMessageRepository messageRepository;

    @Override
    public void execute(ToggleReactionContext context) {

        if (context.getReactionCreatedAt() == null) {
            throw new IllegalStateException(
                    "Reaction createdAt missing"
            );
        }

        ChatMessage message = messageRepository.findById(context.getMessageId())
                .orElseThrow(() -> new IllegalStateException("Message not found for reaction event"));

        ReactionPayload payload = ReactionPayload.builder()
                .messageId(context.getMessageId())
                .roomId(message.getRoomId())
                .userId(context.getUserId())
                .emoji(context.getEmoji())
                .action(
                        context.isRemoved()
                                ? ReactionAction.REMOVE
                                : ReactionAction.ADD
                )
                .createdAt(context.getReactionCreatedAt())
                .build();

        eventPublisher.publishReactionUpdated(payload);
    }

    @Override
    public Class<? extends PipelineStep<?>>[] runAfter() {
        return new Class[]{
                PersistReactionStep.class
        };
    }
}