package com.chatweb.chat.modules.message.application.pipeline.delete.steps;

import com.chatweb.chat.modules.message.application.pipeline.delete.DeleteMessageContext;
import com.chatweb.chat.modules.message.application.pipeline.edit.steps.LoadMessageAggregateStep;
import com.chatweb.chat.modules.message.domain.model.MessageAggregate;
import com.chatweb.common.core.pipeline.PipelineStep;
import com.chatweb.common.core.exception.BusinessException;
import com.chatweb.common.core.exception.CommonErrorCode;
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
                    CommonErrorCode.FORBIDDEN,
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


