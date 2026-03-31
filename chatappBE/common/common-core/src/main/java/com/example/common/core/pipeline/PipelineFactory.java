package com.example.common.core.pipeline;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

public class PipelineFactory {

    public static <C> PipelineExecutor<C> create(
            List<PipelineStep<C>> steps,
            Executor asyncExecutor
    ) {

        List<PipelineStepDescriptor<C>> descriptors =
                steps.stream()
                        .map(PipelineStepDescriptor::new)
                        .toList();

        PipelineGraphResolver<C> resolver =
                new PipelineGraphResolver<>();

        List<PipelineStepDescriptor<C>> sorted =
                resolver.resolve(descriptors);

        return new PipelineExecutor<>(sorted, asyncExecutor);
    }
}