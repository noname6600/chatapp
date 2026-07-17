package com.chatweb.chat.modules.message.application.pipeline.edit;

import com.chatweb.common.core.pipeline.PipelineExecutor;
import com.chatweb.common.core.pipeline.PipelineFactory;
import com.chatweb.common.core.pipeline.PipelineStep;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executor;

@Component
public class EditMessagePipeline {

    private final PipelineExecutor<EditMessageContext> executor;

    public EditMessagePipeline(
            List<PipelineStep<EditMessageContext>> steps,
            Executor pipelineExecutor
    ) {

        this.executor = PipelineFactory.create(
                steps,
                pipelineExecutor
        );
    }

    public EditMessageContext execute(EditMessageContext context) {

        executor.execute(context);

        return context;
    }
}
