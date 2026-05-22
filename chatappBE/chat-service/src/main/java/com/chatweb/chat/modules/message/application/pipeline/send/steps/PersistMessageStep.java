package com.chatweb.chat.modules.message.application.pipeline.send.steps;

import com.chatweb.chat.modules.message.application.pipeline.send.SendMessageContext;
import com.chatweb.chat.modules.message.domain.entity.ChatAttachment;
import com.chatweb.chat.modules.message.domain.entity.ChatMessage;
import com.chatweb.chat.modules.message.domain.model.MessageAggregate;
import com.chatweb.chat.modules.message.domain.repository.ChatAttachmentRepository;
import com.chatweb.chat.modules.message.domain.repository.ChatMessageRepository;
import com.chatweb.chat.modules.message.domain.service.IMessageSequenceService;
import com.chatweb.common.core.pipeline.PipelineStep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PersistMessageStep
        implements PipelineStep<SendMessageContext> {

    private final ChatMessageRepository messageRepository;
    private final ChatAttachmentRepository attachmentRepository;
        private final IMessageSequenceService sequenceService;

    @Override
    @Transactional
    public void execute(SendMessageContext context) {

        MessageAggregate aggregate =
                context.getAggregate();

        if (aggregate == null) {
            throw new IllegalStateException(
                    "MessageAggregate missing"
            );
        }

        long seq = sequenceService.nextSeq(aggregate.getMessage().getRoomId());
        aggregate.getMessage().setSeq(seq);
        context.setSeq(seq);

        ChatMessage message =
                messageRepository.save(
                        aggregate.getMessage()
                );

        if (aggregate.getAttachments() != null &&
                !aggregate.getAttachments().isEmpty()) {

            attachmentRepository.saveAll(
                    aggregate.getAttachments()
            );
            
            List<ChatAttachment> persistedAttachments = 
                    attachmentRepository.findByMessageId(message.getId());
            context.setSavedAttachments(persistedAttachments);
        } else {
            context.setSavedAttachments(List.of());
        }
        context.setSavedMessage(message);
    }

    @Override
    public Class<? extends PipelineStep<?>>[] runAfter() {
        return new Class[]{
                CreateMessageAggregateStep.class
        };
    }
}