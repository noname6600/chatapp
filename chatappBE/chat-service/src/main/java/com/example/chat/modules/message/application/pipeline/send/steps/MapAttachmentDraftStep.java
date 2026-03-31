package com.example.chat.modules.message.application.pipeline.send.steps;

import com.example.chat.modules.message.application.dto.request.SendMessageRequest;
import com.example.chat.modules.message.application.mapper.AttachmentDraftMapper;
import com.example.chat.modules.message.application.mapper.MessageBlockMapper;
import com.example.chat.modules.message.application.pipeline.send.SendMessageContext;
import com.example.chat.modules.message.domain.model.AttachmentDraft;
import com.example.common.core.pipeline.PipelineStep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MapAttachmentDraftStep
        implements PipelineStep<SendMessageContext> {

    private final AttachmentDraftMapper attachmentDraftMapper;
    private final MessageBlockMapper messageBlockMapper;

    @Override
    public void execute(SendMessageContext context) {

        SendMessageRequest request = context.getRequest();

        if (messageBlockMapper.hasBlocks(request.getBlocks())) {
            context.setAttachmentDrafts(
                messageBlockMapper.toAttachmentDrafts(request.getBlocks())
            );
            return;
        }

        if (request.getAttachments() == null ||
                request.getAttachments().isEmpty()) {

            context.setAttachmentDrafts(List.of());
            return;
        }

        List<AttachmentDraft> drafts =
                attachmentDraftMapper.map(
                        request.getAttachments()
                );

        context.setAttachmentDrafts(drafts);
    }

    @Override
    public Class<? extends PipelineStep<?>>[] runAfter() {
        return new Class[]{
                GenerateSequenceStep.class
        };
    }
}