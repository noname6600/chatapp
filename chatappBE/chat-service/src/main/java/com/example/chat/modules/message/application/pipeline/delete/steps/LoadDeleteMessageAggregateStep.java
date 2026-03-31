package com.example.chat.modules.message.application.pipeline.delete.steps;

import com.example.chat.modules.message.application.pipeline.delete.DeleteMessageContext;
import com.example.chat.modules.message.domain.entity.ChatAttachment;
import com.example.chat.modules.message.domain.entity.ChatMessage;
import com.example.chat.modules.message.domain.model.MessageAggregate;
import com.example.chat.modules.message.domain.repository.ChatAttachmentRepository;
import com.example.chat.modules.message.domain.repository.ChatMessageRepository;
import com.example.common.core.pipeline.PipelineStep;
import com.example.common.web.exception.BusinessException;
import com.example.common.web.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class LoadDeleteMessageAggregateStep
        implements PipelineStep<DeleteMessageContext> {

    private final ChatMessageRepository messageRepository;
    private final ChatAttachmentRepository attachmentRepository;

    @Override
    public void execute(DeleteMessageContext context) {

        UUID messageId =
                context.getRequest().getMessageId();

        ChatMessage message =
                messageRepository.findById(messageId)
                        .orElseThrow(() ->
                                new BusinessException(
                                        ErrorCode.MESSAGE_NOT_FOUND
                                )
                        );

        List<ChatAttachment> attachments =
                attachmentRepository.findByMessageId(
                        messageId
                );

        MessageAggregate aggregate =
                MessageAggregate.builder()
                        .message(message)
                        .attachments(new ArrayList<>(attachments))
                        .build();

        context.setAggregate(aggregate);
    }

    @Override
    public Class<? extends PipelineStep<?>>[] runAfter() {
        return new Class[]{
                ValidateDeleteMessageStep.class
        };
    }
}
