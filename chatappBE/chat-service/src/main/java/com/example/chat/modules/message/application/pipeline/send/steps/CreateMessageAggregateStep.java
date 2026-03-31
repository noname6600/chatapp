package com.example.chat.modules.message.application.pipeline.send.steps;

import com.example.chat.modules.message.application.dto.request.SendMessageRequest;
import com.example.chat.modules.message.application.mapper.MessageBlockMapper;
import com.example.chat.modules.message.application.pipeline.send.SendMessageContext;
import com.example.chat.modules.message.domain.model.AttachmentDraft;
import com.example.chat.modules.message.domain.model.MessageAggregate;
import com.example.common.core.pipeline.PipelineStep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CreateMessageAggregateStep
        implements PipelineStep<SendMessageContext> {

        private final MessageBlockMapper messageBlockMapper;

    @Override
    public void execute(SendMessageContext context) {

        SendMessageRequest request = context.getRequest();

        if (context.getSeq() <= 0) {
            throw new IllegalStateException(
                    "Message sequence not generated"
            );
        }

        List<AttachmentDraft> drafts =
                context.getAttachmentDrafts() != null
                        ? context.getAttachmentDrafts()
                        : List.of();

        String content = request.getContent();
        String blocksJson = null;

        if (messageBlockMapper.hasBlocks(request.getBlocks())) {
            content = messageBlockMapper.buildContentText(request.getBlocks());
            blocksJson = messageBlockMapper.encodeBlocks(request.getBlocks());
        }

        MessageAggregate aggregate =
                MessageAggregate.create(
                        context.getRoomId(),
                        context.getSenderId(),
                        context.getSeq(),
                        content,
                        request.getReplyToMessageId(),
                        drafts,
                        request.getClientMessageId(),
                        blocksJson
                );

        context.setAggregate(aggregate);
    }

    @Override
    public Class<? extends PipelineStep<?>>[] runAfter() {
        return new Class[]{
                ExtractMentionStep.class
        };
    }
}