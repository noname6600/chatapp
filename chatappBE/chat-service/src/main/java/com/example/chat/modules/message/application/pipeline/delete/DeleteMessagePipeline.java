package com.example.chat.modules.message.application.pipeline.delete;

import com.example.common.core.pipeline.PipelineExecutor;
import com.example.common.core.pipeline.PipelineFactory;
import com.example.common.core.pipeline.PipelineStep;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executor;

@Component
public class DeleteMessagePipeline {

    private final PipelineExecutor<DeleteMessageContext> executor;

    public DeleteMessagePipeline(
            List<PipelineStep<DeleteMessageContext>> steps,
            Executor pipelineExecutor
    ) {

        this.executor = PipelineFactory.create(
                steps,
                pipelineExecutor
        );
    }

    public DeleteMessageContext execute(DeleteMessageContext context) {

        executor.execute(context);

        return context;
    }
}
