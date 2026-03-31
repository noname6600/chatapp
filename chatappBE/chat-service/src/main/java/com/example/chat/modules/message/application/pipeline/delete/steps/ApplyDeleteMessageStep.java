package com.example.chat.modules.message.application.pipeline.delete.steps;

import com.example.chat.modules.message.application.pipeline.delete.DeleteMessageContext;
import com.example.chat.modules.message.domain.model.MessageAggregate;
import com.example.common.core.pipeline.PipelineStep;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ApplyDeleteMessageStep
        implements PipelineStep<DeleteMessageContext> {

    @Override
    public void execute(DeleteMessageContext context) {

        MessageAggregate aggregate =
                context.getAggregate();

        UUID actorId =
                context.getRequest().getActorId();

        aggregate.delete(actorId);
    }

    @Override
    public Class<? extends PipelineStep<?>>[] runAfter() {
        return new Class[]{
                AuthorizeDeleteMessageStep.class
        };
    }
}
