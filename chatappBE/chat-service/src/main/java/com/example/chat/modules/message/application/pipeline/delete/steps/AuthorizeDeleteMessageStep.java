package com.example.chat.modules.message.application.pipeline.delete.steps;

import com.example.chat.modules.message.application.pipeline.delete.DeleteMessageContext;
import com.example.chat.modules.message.application.pipeline.edit.steps.LoadMessageAggregateStep;
import com.example.chat.modules.message.domain.model.MessageAggregate;
import com.example.common.core.pipeline.PipelineStep;
import com.example.common.web.exception.BusinessException;
import com.example.common.web.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AuthorizeDeleteMessageStep
        implements PipelineStep<DeleteMessageContext> {

    @Override
    public void execute(DeleteMessageContext context) {

        MessageAggregate aggregate = context.getAggregate();

        UUID actorId = context.getRequest().getActorId();

        UUID senderId = aggregate.getMessage().getSenderId();

        if (!senderId.equals(actorId)) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN,
                    "You are not allowed to delete this message"
            );
        }
    }

    @Override
    public Class<? extends PipelineStep<?>>[] runAfter() {
        return new Class[]{
                LoadDeleteMessageAggregateStep.class
        };
    }
}
