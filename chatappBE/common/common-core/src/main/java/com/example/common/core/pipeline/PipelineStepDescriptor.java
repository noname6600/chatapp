package com.example.common.core.pipeline;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class PipelineStepDescriptor<C> {

    private final PipelineStep<C> step;

    private final Class<? extends PipelineStep<?>> stepClass;

    private final Set<Class<? extends PipelineStep<?>>> runAfter;

    private final boolean async;

    private final StepRetryPolicy retryPolicy;

    private final StepCondition<C> condition;

    private final long timeoutMs;

    @SuppressWarnings("unchecked")
    public PipelineStepDescriptor(PipelineStep<C> step) {
        this.step = step;

        this.stepClass =
                (Class<? extends PipelineStep<?>>)
                resolveStepClass(step);

        this.runAfter = new HashSet<>(
                Arrays.asList(step.runAfter())
        );

        this.async = step.isAsync();

        this.retryPolicy = step.retryPolicy();

        this.condition = step.condition();

        this.timeoutMs = step.timeoutMs();
    }

    @SuppressWarnings("unchecked")
    private Class<? extends PipelineStep<?>> resolveStepClass(PipelineStep<C> step) {
        Class<?> candidate = step.getClass();

        if (candidate.getName().contains("$$") && candidate.getSuperclass() != null) {
            candidate = candidate.getSuperclass();
        }

        return (Class<? extends PipelineStep<?>>) candidate;
    }

    public PipelineStep<C> getStep() {
        return step;
    }

    public Class<? extends PipelineStep<?>> getStepClass() {
        return stepClass;
    }

    public Set<Class<? extends PipelineStep<?>>> getRunAfter() {
        return runAfter;
    }

    public boolean isAsync() {
        return async;
    }

    public StepRetryPolicy getRetryPolicy() {
        return retryPolicy;
    }

    public StepCondition<C> getCondition() {
        return condition;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }
}