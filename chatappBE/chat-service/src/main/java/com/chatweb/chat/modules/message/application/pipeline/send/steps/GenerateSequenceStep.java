package com.chatweb.chat.modules.message.application.pipeline.send.steps;

import com.chatweb.chat.modules.message.application.pipeline.send.SendMessageContext;
import com.chatweb.common.core.pipeline.PipelineStep;
import org.springframework.stereotype.Component;

@Component
public class GenerateSequenceStep
        implements PipelineStep<SendMessageContext> {

    @Override
    public void execute(SendMessageContext context) {
        // Sequence is allocated atomically together with message persistence.
        context.setSeq(0L);
    }

    @Override
    public Class<? extends PipelineStep<?>>[] runAfter() {
        return new Class[]{
                ValidateRoomPermissionStep.class
        };
    }
}