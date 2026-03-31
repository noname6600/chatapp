package com.example.chat.modules.message.application.pipeline.edit.steps;

import com.example.chat.modules.message.application.dto.request.EditMessageRequest;
import com.example.chat.modules.message.application.pipeline.edit.EditMessageContext;
import com.example.chat.modules.message.domain.model.MessageAggregate;
import com.example.common.core.pipeline.PipelineStep;
import org.springframework.stereotype.Component;

@Component
public class ApplyEditMessageStep
        implements PipelineStep<EditMessageContext> {

    @Override
    public void execute(EditMessageContext context) {

        MessageAggregate aggregate =
                context.getAggregate();

        EditMessageRequest request =
                context.getRequest();

        aggregate.editText(
                request.getActorId(),
                request.getContent(),
                request.getBlocksJson()
        );
    }

    @Override
    public Class<? extends PipelineStep<?>>[] runAfter() {
        return new Class[]{
                AuthorizeEditMessageStep.class
        };
    }
}