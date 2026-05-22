package com.chatweb.chat.modules.message.application.pipeline.edit.steps;

import com.chatweb.chat.modules.message.application.pipeline.edit.EditMessageContext;
import com.chatweb.chat.modules.message.application.service.IMessageEventPublisher;
import com.chatweb.chat.modules.room.service.IRoomService;
import com.chatweb.common.core.pipeline.PipelineStep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@RequiredArgsConstructor
public class PublishMessageEditedEventStep
        implements PipelineStep<EditMessageContext> {

    private final IMessageEventPublisher eventPublisher;
    private final IRoomService roomService;

    @Override
    public void execute(EditMessageContext context) {

        var savedMessage = context.getSavedMessage();
        if (savedMessage == null) {
            throw new IllegalStateException(
                    "Saved message missing when publishing edited event"
            );
        }

        roomService.updateLastMessagePreviewIfMatch(
            savedMessage.getRoomId(),
            savedMessage.getId(),
            savedMessage.getContent()
        );

        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    eventPublisher.publishMessageEdited(savedMessage);
                }
            });
            return;
        }

        eventPublisher.publishMessageEdited(savedMessage);
    }

    @Override
    public Class<? extends PipelineStep<?>>[] runAfter() {
        return new Class[]{
                PersistEditedMessageStep.class
        };
    }
}
