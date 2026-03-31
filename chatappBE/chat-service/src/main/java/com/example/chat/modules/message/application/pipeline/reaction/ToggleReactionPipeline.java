package com.example.chat.modules.message.application.pipeline.reaction;

import com.example.common.core.pipeline.PipelineExecutor;
import com.example.common.core.pipeline.PipelineFactory;
import com.example.common.core.pipeline.PipelineStep;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executor;

@Component
public class ToggleReactionPipeline {

    private final PipelineExecutor<ToggleReactionContext> executor;

    public ToggleReactionPipeline(
            List<PipelineStep<ToggleReactionContext>> steps,
            Executor pipelineExecutor
    ) {

        this.executor = PipelineFactory.create(
                steps,
                pipelineExecutor
        );
    }

    public ToggleReactionContext execute(
            ToggleReactionContext context
    ) {

        executor.execute(context);

        return context;
    }
}