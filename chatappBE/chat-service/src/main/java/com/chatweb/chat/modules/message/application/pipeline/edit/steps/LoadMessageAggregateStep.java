package com.chatweb.chat.modules.message.application.pipeline.edit.steps;

import com.chatweb.chat.modules.message.application.pipeline.edit.EditMessageContext;
import com.chatweb.chat.modules.message.domain.entity.ChatAttachment;
import com.chatweb.chat.modules.message.domain.entity.ChatMessage;
import com.chatweb.chat.modules.message.domain.model.MessageAggregate;
import com.chatweb.chat.modules.message.domain.repository.ChatAttachmentRepository;
import com.chatweb.chat.modules.message.domain.repository.ChatMessageRepository;
import com.chatweb.chat.exception.ChatErrorCode;
import com.chatweb.common.core.pipeline.PipelineStep;
import com.chatweb.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class LoadMessageAggregateStep
        implements PipelineStep<EditMessageContext> {

    private final ChatMessageRepository messageRepository;
    private final ChatAttachmentRepository attachmentRepository;

    @Override
    public void execute(EditMessageContext context) {

        UUID messageId =
                context.getRequest().getMessageId();

        ChatMessage message =
                messageRepository.findById(messageId)
                        .orElseThrow(() ->
                                new BusinessException(
                                        ChatErrorCode.MESSAGE_NOT_FOUND
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
                ValidateEditMessageStep.class
        };
    }
}

