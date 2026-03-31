package com.example.chat.modules.message.application.pipeline.send.steps;

import com.example.chat.modules.message.application.pipeline.send.SendMessageContext;
import com.example.chat.modules.message.application.dto.request.SendMessageRequest;
import com.example.chat.modules.message.domain.service.MentionParser;
import com.example.common.core.pipeline.PipelineStep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ExtractMentionStep
        implements PipelineStep<SendMessageContext> {

    @Override
    public void execute(SendMessageContext context) {

        SendMessageRequest request = context.getRequest();
        List<UUID> requestMentions = request.getMentionedUserIds();

        if (requestMentions != null && !requestMentions.isEmpty()) {
            context.setMentionedUsers(
                    requestMentions.stream().distinct().toList()
            );
            return;
        }

        String content = request.getContent();

        if (content == null || content.isBlank()) {
            context.setMentionedUsers(List.of());
            return;
        }

        Set<UUID> userIds =
                MentionParser.parseUserIds(content);

        context.setMentionedUsers(
                new ArrayList<>(userIds)
        );
    }

    @Override
    public Class<? extends PipelineStep<?>>[] runAfter() {
        return new Class[]{
                MapAttachmentDraftStep.class
        };
    }
}