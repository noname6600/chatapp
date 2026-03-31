package com.example.common.core.pipeline;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Slf4j
public class PipelineExecutor<C> {

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

        List<CompletableFuture<?>> asyncFutures = new ArrayList<>();

        for (PipelineStepDescriptor<C> descriptor : steps) {

            if (!shouldRun(descriptor, context)) {
                continue;
            }

            if (descriptor.isAsync()) {

                CompletableFuture<Void> future =
                        CompletableFuture.runAsync(
                                () -> runStep(descriptor, context),
                                executor
                        );

                asyncFutures.add(future);

            } else {

                runStep(descriptor, context);

            }
        }

        if (!asyncFutures.isEmpty()) {

            CompletableFuture
                    .allOf(asyncFutures.toArray(new CompletableFuture[0]))
                    .join();

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

            long start = System.nanoTime();

            try {

                runWithTimeout(descriptor, context);

                long duration = TimeUnit.NANOSECONDS.toMillis(
                        System.nanoTime() - start
                );

                log.debug("Step {} completed in {} ms", stepName, duration);

                return;

            } catch (Exception e) {

                attempt++;

                log.warn(
                        "Step {} failed (attempt {}/{})",
                        stepName,
                        attempt,
                        maxAttempts
                );

                if (attempt >= maxAttempts) {

                    log.error("Step {} failed permanently", stepName, e);

                    if (e instanceof RuntimeException runtime) {
                        throw runtime;
                    }

                    throw new RuntimeException(e);
                }

                sleep(retryPolicy.getBackoffMillis());
            }
        }
    }

    private void runWithTimeout(
            PipelineStepDescriptor<C> descriptor,
            C context
    ) {

        try {

            CompletableFuture
                    .runAsync(
                            () -> descriptor.getStep().execute(context),
                            executor
                    )
                    .orTimeout(descriptor.getTimeoutMs(), TimeUnit.MILLISECONDS)
                    .join();

        } catch (CompletionException e) {

            Throwable cause = e.getCause();

            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }

            throw new RuntimeException(cause);
        }
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