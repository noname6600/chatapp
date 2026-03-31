package com.example.common.core.pipeline;

public interface PipelineStep<C> {

    void execute(C context);

    default Class<? extends PipelineStep<?>>[] runAfter() {
        return new Class[0];
    }

    default StepCondition<C> condition() {
        return context -> true;
    }

    default boolean isAsync() {
        return false;
    }

    default StepRetryPolicy retryPolicy() {
        return StepRetryPolicy.noRetry();
    }

    default long timeoutMs() {
        return 5000;
    }
}