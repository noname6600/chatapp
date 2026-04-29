package com.example.chat.modules.message.application.pipeline.edit.steps;

import com.example.chat.modules.message.application.pipeline.edit.EditMessageContext;
import com.example.chat.modules.message.domain.model.MessageAggregate;
import com.example.common.core.pipeline.PipelineStep;
import com.example.common.core.exception.BusinessException;
import com.example.common.core.exception.CommonErrorCode;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AuthorizeEditMessageStep
        implements PipelineStep<EditMessageContext> {

    @Override
    public void execute(EditMessageContext context) {

        MessageAggregate aggregate = context.getAggregate();

        UUID actorId = context.getRequest().getActorId();

        UUID senderId = aggregate.getMessage().getSenderId();

        if (!senderId.equals(actorId)) {
            throw new BusinessException(
                    CommonErrorCode.FORBIDDEN,
                    "You are not allowed to edit this message"
            );
        }
    }

    @Override
    public Class<? extends PipelineStep<?>>[] runAfter() {
        return new Class[]{
                LoadMessageAggregateStep.class
        };
    }
}


