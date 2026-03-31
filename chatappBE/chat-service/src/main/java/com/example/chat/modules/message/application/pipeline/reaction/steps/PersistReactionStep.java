package com.example.chat.modules.message.application.pipeline.reaction.steps;

import com.example.chat.modules.message.application.pipeline.reaction.ToggleReactionContext;
import com.example.chat.modules.message.domain.entity.ChatReaction;
import com.example.chat.modules.message.domain.repository.ChatReactionRepository;
import com.example.common.core.pipeline.PipelineStep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Transactional
public class PersistReactionStep
        implements PipelineStep<ToggleReactionContext> {

    private final ChatReactionRepository reactionRepository;

    @Override
    public void execute(ToggleReactionContext context) {

        Optional<ChatReaction> existing =
                reactionRepository.findByMessageIdAndUserIdAndEmoji(
                        context.getMessageId(),
                        context.getUserId(),
                        context.getEmoji()
                );

        if (existing.isPresent()) {
            // Reaction exists → delete it (toggle OFF)
            reactionRepository.deleteByMessageIdAndUserIdAndEmoji(
                    context.getMessageId(),
                    context.getUserId(),
                    context.getEmoji()
            );
            context.setRemoved(true);
            context.setReactionCreatedAt(existing.get().getCreatedAt());

        } else {
            // Reaction doesn't exist → create it (toggle ON)
            ChatReaction reaction =
                    ChatReaction.builder()
                            .messageId(context.getMessageId())
                            .userId(context.getUserId())
                            .emoji(context.getEmoji())
                            .build();

            ChatReaction saved = reactionRepository.save(reaction);
            context.setRemoved(false);
            context.setReactionCreatedAt(saved.getCreatedAt());
        }
    }
}