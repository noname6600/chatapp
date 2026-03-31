package com.example.chat.modules.message.application.pipeline.delete.steps;

import com.example.chat.modules.message.application.pipeline.delete.DeleteMessageContext;
import com.example.chat.modules.message.application.service.IMessageEventPublisher;
import com.example.chat.modules.room.service.IRoomService;
import com.example.common.core.pipeline.PipelineStep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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

        eventPublisher.publishMessageDeleted(
            message
        );
    }

    @Override
    public Class<? extends PipelineStep<?>>[] runAfter() {
        return new Class[]{
                PersistDeleteMessageStep.class
        };
    }
}
