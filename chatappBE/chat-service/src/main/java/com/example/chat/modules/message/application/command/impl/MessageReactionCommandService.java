package com.example.chat.modules.message.application.command.impl;

import com.example.chat.modules.message.application.command.IReactionCommandService;
import com.example.chat.modules.message.application.pipeline.reaction.ToggleReactionContext;
import com.example.chat.modules.message.application.pipeline.reaction.ToggleReactionPipeline;
import com.example.chat.modules.message.domain.entity.ChatReaction;
import com.example.chat.modules.message.domain.repository.ChatReactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MessageReactionCommandService
        implements IReactionCommandService {

    private final ToggleReactionPipeline pipeline;

    @Override
    public void toggleReaction(
            UUID messageId,
            UUID userId,
            String emoji
    ) {

        ToggleReactionContext context =
                new ToggleReactionContext();

        context.setMessageId(messageId);
        context.setUserId(userId);
        context.setEmoji(emoji);

        pipeline.execute(context);
    }
}
