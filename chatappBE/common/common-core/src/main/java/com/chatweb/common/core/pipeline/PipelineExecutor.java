package com.chatweb.common.core.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executor;

public class PipelineExecutor<C> {

    private static final Logger log = LoggerFactory.getLogger(PipelineExecutor.class);

    private final List<PipelineStepDescriptor<C>> steps;
    private final Executor executor;

    public PipelineExecutor(
            List<PipelineStepDescriptor<C>> steps,
            Executor executor
    ) {
        this.steps = steps;
        this.executor = executor;
    }

    public void execute(C context) {
        for (PipelineStepDescriptor<C> descriptor : steps) {

            if (!shouldRun(descriptor, context)) {
                continue;
            }
            runStep(descriptor, context);
        }
    }

    private boolean shouldRun(
            PipelineStepDescriptor<C> descriptor,
            C context
    ) {

        StepCondition<C> condition = descriptor.getCondition();

        return condition == null || condition.test(context);
    }

    private void runStep(
            PipelineStepDescriptor<C> descriptor,
            C context
    ) {

        String stepName = descriptor.getStepClass().getSimpleName();

        StepRetryPolicy retryPolicy = descriptor.getRetryPolicy();

        int maxAttempts = retryPolicy.getMaxAttempts();
        int attempt = 0;

        while (true) {

            try {

                runWithTimeout(descriptor, context);

                return;

            } catch (Exception e) {

                attempt++;

                if (attempt >= maxAttempts) {

                    log.error("Step {} failed permanently after {} attempt(s)", stepName, maxAttempts, e);

                    if (e instanceof RuntimeException runtime) {
                        throw runtime;
                    }

                    throw new RuntimeException(e);
                }

                log.warn("Step {} failed (attempt {}/{}), retrying in {}ms",
                        stepName, attempt, maxAttempts, retryPolicy.getBackoffMillis());

                sleep(retryPolicy.getBackoffMillis());
            }
        }
    }

    private void runWithTimeout(
            PipelineStepDescriptor<C> descriptor,
            C context
    ) {
        descriptor.getStep().execute(context);
    }

    private void sleep(long millis) {

        try {

            Thread.sleep(millis);

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new RuntimeException(e);
        }
    }
}