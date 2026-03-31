package com.example.chat.modules.message.application.pipeline.send.steps;

import com.example.chat.modules.message.application.pipeline.send.SendMessageContext;
import com.example.chat.modules.message.domain.entity.ChatMessageMention;
import com.example.chat.modules.message.domain.repository.ChatMessageMentionRepository;
import com.example.common.core.pipeline.PipelineStep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PersistMentionStep
        implements PipelineStep<SendMessageContext> {

    private final ChatMessageMentionRepository repository;

    @Override
    public void execute(SendMessageContext context) {

        if (context.getMentionedUsers().isEmpty()) {
            return;
        }

        UUID messageId = context.getSavedMessage().getId();

        List<ChatMessageMention> mentions =
                context.getMentionedUsers()
                        .stream()
                        .map(userId ->
                                ChatMessageMention.builder()
                                        .messageId(messageId)
                                        .mentionedUserId(userId)
                                        .build()
                        )
                        .toList();

        repository.saveAll(mentions);
    }

    @Override
    public Class<? extends PipelineStep<?>>[] runAfter() {
        return new Class[]{
                PersistMessageStep.class
        };
    }
}
