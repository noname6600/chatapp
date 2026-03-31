package com.example.chat.modules.message.application.pipeline.delete.steps;

import com.example.chat.modules.message.application.pipeline.delete.DeleteMessageContext;
import com.example.chat.modules.message.domain.repository.ChatMessageRepository;
import com.example.common.core.pipeline.PipelineStep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PersistDeleteMessageStep
        implements PipelineStep<DeleteMessageContext> {

    private final ChatMessageRepository messageRepository;

    @Override
    public void execute(DeleteMessageContext context) {

        messageRepository.save(
                context.getAggregate().getMessage()
        );
    }

    @Override
    public Class<? extends PipelineStep<?>>[] runAfter() {
        return new Class[]{
                ApplyDeleteMessageStep.class
        };
    }
}
