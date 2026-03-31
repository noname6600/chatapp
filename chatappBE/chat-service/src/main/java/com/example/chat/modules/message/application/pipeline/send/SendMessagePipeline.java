package com.example.chat.modules.message.application.pipeline.send;

import com.example.common.core.pipeline.PipelineExecutor;
import com.example.common.core.pipeline.PipelineFactory;
import com.example.common.core.pipeline.PipelineStep;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executor;

@Component
public class SendMessagePipeline {

    private final PipelineExecutor<SendMessageContext> executor;

    public SendMessagePipeline(
            List<PipelineStep<SendMessageContext>> steps,
            Executor pipelineExecutor
    ) {

        this.executor = PipelineFactory.create(
                steps,
                pipelineExecutor
        );
    }

    public SendMessageContext execute(SendMessageContext context) {

        executor.execute(context);

        return context;
    }
}