package com.chatweb.chat.modules.message.application.pipeline.delete.steps;

import com.chatweb.chat.modules.message.application.pipeline.delete.DeleteMessageContext;
import com.chatweb.chat.modules.message.application.service.IMessageEventPublisher;
import com.chatweb.chat.modules.room.service.IRoomService;
import com.chatweb.common.core.pipeline.PipelineStep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@RequiredArgsConstructor
public class PublishMessageDeletedEventStep
        implements PipelineStep<DeleteMessageContext> {

    private final IMessageEventPublisher eventPublisher;
    private final IRoomService roomService;

    @Override
    public void execute(DeleteMessageContext context) {

        var message = context.getAggregate().getMessage();

        roomService.handleMessageDeleted(
            message.getRoomId(),
            message.getId()
        );

        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    eventPublisher.publishMessageDeleted(message);
                }
            });
            return;
        }

        eventPublisher.publishMessageDeleted(message);
    }

    @Override
    public Class<? extends PipelineStep<?>>[] runAfter() {
        return new Class[]{
                PersistDeleteMessageStep.class
        };
    }
}
