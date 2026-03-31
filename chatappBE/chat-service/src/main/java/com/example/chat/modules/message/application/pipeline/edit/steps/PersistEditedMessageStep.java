package com.example.chat.modules.message.application.pipeline.edit.steps;

import com.example.chat.modules.message.application.pipeline.edit.EditMessageContext;
import com.example.chat.modules.message.domain.entity.ChatAttachment;
import com.example.chat.modules.message.domain.model.MessageAggregate;
import com.example.chat.modules.message.domain.repository.ChatAttachmentRepository;
import com.example.chat.modules.message.domain.repository.ChatMessageRepository;
import com.example.common.core.pipeline.PipelineStep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PersistEditedMessageStep
        implements PipelineStep<EditMessageContext> {

    private final ChatMessageRepository messageRepository;
    private final ChatAttachmentRepository attachmentRepository;

    @Override
    public void execute(EditMessageContext context) {

        MessageAggregate aggregate =
                context.getAggregate();

        var savedMessage = messageRepository.save(
                aggregate.getMessage()
        );

        List<ChatAttachment> attachments = 
                attachmentRepository.findByMessageId(savedMessage.getId());

        context.setSavedMessage(savedMessage);
        context.setSavedAttachments(attachments);
    }

    @Override
    public Class<? extends PipelineStep<?>>[] runAfter() {
        return new Class[]{
                ApplyEditMessageStep.class
        };
    }
}
