package com.example.chat.modules.message.application.pipeline.send.steps;

import com.example.chat.modules.message.application.pipeline.send.SendMessageContext;
import com.example.chat.modules.message.domain.service.IMessageSequenceService;
import com.example.common.core.pipeline.PipelineStep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GenerateSequenceStep
        implements PipelineStep<SendMessageContext> {

    private final IMessageSequenceService sequenceService;

    @Override
    public void execute(SendMessageContext context) {

        long seq =
                sequenceService.nextSeq(
                        context.getRoomId()
                );

        context.setSeq(seq);
    }

    @Override
    public Class<? extends PipelineStep<?>>[] runAfter() {
        return new Class[]{
                ValidateRoomPermissionStep.class
        };
    }
}