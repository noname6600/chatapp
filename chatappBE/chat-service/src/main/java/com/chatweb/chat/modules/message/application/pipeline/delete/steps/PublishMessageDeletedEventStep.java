package com.chatweb.chat.modules.message.application.pipeline.delete.steps;

import com.chatweb.chat.modules.message.application.pipeline.delete.DeleteMessageContext;
import com.chatweb.chat.modules.message.application.service.IMessageEventPublisher;
import com.chatweb.chat.modules.room.service.IRoomService;
import com.chatweb.common.core.pipeline.PipelineStep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import com.chatweb.chat.support.TransactionPublisher;

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

        TransactionPublisher.publishAfterCommit(() -> eventPublisher.publishMessageDeleted(message));
    }

    @Override
    public Class<? extends PipelineStep<?>>[] runAfter() {
        return new Class[]{
                PersistDeleteMessageStep.class
        };
    }
}
