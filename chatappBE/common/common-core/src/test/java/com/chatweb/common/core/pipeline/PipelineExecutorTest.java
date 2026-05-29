package com.chatweb.common.core.pipeline;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Contract tests for PipelineExecutor dependency ordering, async/sync handling, and timeout/retry semantics.
 */
class PipelineExecutorTest {

    private static class TestContext {
        final List<String> executionOrder = Collections.synchronizedList(new ArrayList<>());
    }

    // ========================================================================
    // Named step classes for use in pipeline
    // ========================================================================

    private static class StepA implements PipelineStep<TestContext> {
        @Override
        public void execute(TestContext context) {
            context.executionOrder.add("A");
        }
    }

    private static class StepB implements PipelineStep<TestContext> {
        @Override
        public void execute(TestContext context) {
            context.executionOrder.add("B");
        }

        @Override
        @SuppressWarnings("unchecked")
        public Class<? extends PipelineStep<?>>[] runAfter() {
            return new Class[]{StepA.class};
        }
    }

    private static class StepC implements PipelineStep<TestContext> {
        @Override
        public void execute(TestContext context) {
            context.executionOrder.add("C");
        }

        @Override
        @SuppressWarnings("unchecked")
        public Class<? extends PipelineStep<?>>[] runAfter() {
            return new Class[]{StepB.class};
        }
    }

    private static class StepAAsync implements PipelineStep<TestContext> {
        @Override
        public void execute(TestContext context) {
            context.executionOrder.add("A");
        }

        @Override
        public boolean isAsync() {
            return true;
        }
    }

    private static class StepBAsync implements PipelineStep<TestContext> {
        @Override
        public void execute(TestContext context) {
            context.executionOrder.add("B");
        }

        @Override
        public boolean isAsync() {
            return true;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Class<? extends PipelineStep<?>>[] runAfter() {
            return new Class[]{StepAAsync.class};
        }
    }

    private static class StepTimeout implements PipelineStep<TestContext> {
        private final long delayMs;

        StepTimeout(long delayMs) {
            this.delayMs = delayMs;
        }

        @Override
        public void execute(TestContext context) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            context.executionOrder.add("timeout-step");
        }

        @Override
        public long timeoutMs() {
            return 100;
        }
    }

    private static class StepTimeoutAsync implements PipelineStep<TestContext> {
        private final long delayMs;

        StepTimeoutAsync(long delayMs) {
            this.delayMs = delayMs;
        }

        @Override
        public void execute(TestContext context) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            context.executionOrder.add("timeout-step");
        }

        @Override
        public boolean isAsync() {
            return true;
        }

        @Override
        public long timeoutMs() {
            return 100;
        }
    }

    private static class StepFail implements PipelineStep<TestContext> {
        private final int failUntilAttempt;
        private final AtomicInteger attempts = new AtomicInteger(0);

        StepFail(int failUntilAttempt) {
            this.failUntilAttempt = failUntilAttempt;
        }

        @Override
        public void execute(TestContext context) {
            int attempt = attempts.incrementAndGet();
            if (attempt < failUntilAttempt) {
                throw new RuntimeException("Failed at attempt " + attempt);
            }
            context.executionOrder.add("fail-step");
        }

        @Override
        public StepRetryPolicy retryPolicy() {
            return new StepRetryPolicy(3, 10);
        }
    }

    private static class StepRetryExhausted implements PipelineStep<TestContext> {
        private final AtomicInteger attempts = new AtomicInteger(0);

        @Override
        public void execute(TestContext context) {
            attempts.incrementAndGet();
            throw new RuntimeException("Always fails");
        }

        @Override
        public StepRetryPolicy retryPolicy() {
            return new StepRetryPolicy(2, 0);
        }
    }

    private static class StepSkipped implements PipelineStep<TestContext> {
        @Override
        public void execute(TestContext context) {
            context.executionOrder.add("skipped-step");
        }

        @Override
        public StepCondition<TestContext> condition() {
            return ctx -> false;
        }
    }

    private static class StepConditioned implements PipelineStep<TestContext> {
        @Override
        public void execute(TestContext context) {
            context.executionOrder.add("conditioned-step");
        }

        @Override
        public StepCondition<TestContext> condition() {
            return ctx -> true;
        }
    }

    // ========================================================================
    // Tests
    // ========================================================================

    @Test
    void synchronous_steps_execute_in_dependency_order() {
        TestContext context = new TestContext();

        List<PipelineStepDescriptor<TestContext>> steps = Arrays.asList(
                new PipelineStepDescriptor<>(new StepC()),
                new PipelineStepDescriptor<>(new StepB()),
                new PipelineStepDescriptor<>(new StepA())
        );

        PipelineGraphResolver<TestContext> resolver = new PipelineGraphResolver<>();
        List<PipelineStepDescriptor<TestContext>> sorted = resolver.resolve(steps);

        PipelineExecutor<TestContext> executor = new PipelineExecutor<>(sorted, Executors.newFixedThreadPool(4));
        executor.execute(context);

        assertThat(context.executionOrder).isEqualTo(List.of("A", "B", "C"));
    }

    @Test
    void asynchronous_steps_execute_with_dependency_ordering() {
        TestContext context = new TestContext();

        List<PipelineStepDescriptor<TestContext>> steps = Arrays.asList(
                new PipelineStepDescriptor<>(new StepBAsync()),
                new PipelineStepDescriptor<>(new StepAAsync())
        );

        PipelineGraphResolver<TestContext> resolver = new PipelineGraphResolver<>();
        List<PipelineStepDescriptor<TestContext>> sorted = resolver.resolve(steps);

        PipelineExecutor<TestContext> executor = new PipelineExecutor<>(sorted, Executors.newSingleThreadExecutor());
        executor.execute(context);

        assertThat(context.executionOrder).isEqualTo(List.of("A", "B"));
    }

    @Test
    void timeout_causes_step_failure() {
        // Note: Timeout is applied to async steps via orTimeout().
        // Sync steps run directly on caller thread and are not timed out.
        // This test uses an async step with a long delay to test timeout behavior.
        TestContext context = new TestContext();

        List<PipelineStepDescriptor<TestContext>> steps = Collections.singletonList(
                new PipelineStepDescriptor<>(new StepTimeoutAsync(500))
        );

        PipelineExecutor<TestContext> executor = new PipelineExecutor<>(steps, Executors.newFixedThreadPool(2));

        assertThatThrownBy(() -> executor.execute(context))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void retry_policy_retries_failed_steps() {
        TestContext context = new TestContext();

        List<PipelineStepDescriptor<TestContext>> steps = Collections.singletonList(
                new PipelineStepDescriptor<>(new StepFail(2))
        );

        PipelineExecutor<TestContext> executor = new PipelineExecutor<>(steps, Executors.newFixedThreadPool(2));
        executor.execute(context);

        assertThat(context.executionOrder).contains("fail-step");
    }

    @Test
    void retry_exhaustion_throws_exception() {
        TestContext context = new TestContext();

        List<PipelineStepDescriptor<TestContext>> steps = Collections.singletonList(
                new PipelineStepDescriptor<>(new StepRetryExhausted())
        );

        PipelineExecutor<TestContext> executor = new PipelineExecutor<>(steps, Executors.newFixedThreadPool(2));

        assertThatThrownBy(() -> executor.execute(context))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Always fails");
    }

    @Test
    void condition_false_skips_step() {
        TestContext context = new TestContext();

        List<PipelineStepDescriptor<TestContext>> steps = Collections.singletonList(
                new PipelineStepDescriptor<>(new StepSkipped())
        );

        PipelineExecutor<TestContext> executor = new PipelineExecutor<>(steps, Executors.newFixedThreadPool(2));
        executor.execute(context);

        assertThat(context.executionOrder).isEmpty();
    }

    @Test
    void condition_true_executes_step() {
        TestContext context = new TestContext();

        List<PipelineStepDescriptor<TestContext>> steps = Collections.singletonList(
                new PipelineStepDescriptor<>(new StepConditioned())
        );

        PipelineExecutor<TestContext> executor = new PipelineExecutor<>(steps, Executors.newFixedThreadPool(2));
        executor.execute(context);

        assertThat(context.executionOrder).isEqualTo(List.of("conditioned-step"));
    }

    @Test
    void empty_pipeline_executes_successfully() {
        TestContext context = new TestContext();

        List<PipelineStepDescriptor<TestContext>> steps = Collections.emptyList();

        PipelineExecutor<TestContext> executor = new PipelineExecutor<>(steps, Executors.newFixedThreadPool(2));
        executor.execute(context);

        assertThat(context.executionOrder).isEmpty();
    }

    // ========================================================================
    // Additional tests: Async dependency correctness with multi-threaded executor
    // ========================================================================

    @Test
    void async_dependent_does_not_run_before_async_dependency_with_multi_thread_executor() {
        // This test proves that even with a multi-threaded executor,
        // B does not execute until A completes.
        TestContext context = new TestContext();

        // Create B that depends on SlowStepAsync
        List<PipelineStepDescriptor<TestContext>> steps = Arrays.asList(
                new PipelineStepDescriptor<>(new StepBAsyncWithDep()),
                new PipelineStepDescriptor<>(new SlowStepAsync(100))
        );

        PipelineGraphResolver<TestContext> resolver = new PipelineGraphResolver<>();
        List<PipelineStepDescriptor<TestContext>> sorted = resolver.resolve(steps);

        // Multi-threaded executor â€" proves correct ordering even with concurrency
        PipelineExecutor<TestContext> executor = new PipelineExecutor<>(sorted, Executors.newFixedThreadPool(4));
        long start = System.currentTimeMillis();
        executor.execute(context);
        long elapsed = System.currentTimeMillis() - start;

        // B should execute after the slow A completes, so total time >= 100ms
        assertThat(elapsed).isGreaterThanOrEqualTo(80);
        assertThat(context.executionOrder).isEqualTo(List.of("A", "B"));
    }

    @Test
    void dependency_failure_prevents_dependent_execution() {
        // This test proves that if a dependency fails (after retries), dependents do not execute.
        // We create a special test step that fails and another that depends on it.
        TestContext context = new TestContext();

        List<PipelineStepDescriptor<TestContext>> steps = Arrays.asList(
                new PipelineStepDescriptor<>(new FailStep()),
                new PipelineStepDescriptor<>(new StepAfterFail())
        );

        PipelineGraphResolver<TestContext> resolver = new PipelineGraphResolver<>();
        List<PipelineStepDescriptor<TestContext>> sorted = resolver.resolve(steps);

        PipelineExecutor<TestContext> executor = new PipelineExecutor<>(sorted, Executors.newFixedThreadPool(2));

        assertThatThrownBy(() -> executor.execute(context))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Always fails");

        // StepAfterFail should not have executed because its dependency FailStep failed
        assertThat(context.executionOrder).doesNotContain("after-fail");
    }

    // Helper step that sleeps before recording execution
    private static class SlowStepAsync implements PipelineStep<TestContext> {
        private final long delayMs;

        SlowStepAsync(long delayMs) {
            this.delayMs = delayMs;
        }

        @Override
        public void execute(TestContext context) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            context.executionOrder.add("A");
        }

        @Override
        public boolean isAsync() {
            return true;
        }
    }

    // Async B that depends on SlowStepAsync
    private static class StepBAsyncWithDep implements PipelineStep<TestContext> {
        @Override
        public void execute(TestContext context) {
            context.executionOrder.add("B");
        }

        @Override
        public boolean isAsync() {
            return true;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Class<? extends PipelineStep<?>>[] runAfter() {
            return new Class[]{SlowStepAsync.class};
        }
    }

    // Step that always fails
    private static class FailStep implements PipelineStep<TestContext> {
        private final AtomicInteger attempts = new AtomicInteger(0);

        @Override
        public void execute(TestContext context) {
            attempts.incrementAndGet();
            throw new RuntimeException("Always fails");
        }

        @Override
        public StepRetryPolicy retryPolicy() {
            return new StepRetryPolicy(2, 0);
        }
    }

    // Step that depends on FailStep
    private static class StepAfterFail implements PipelineStep<TestContext> {
        @Override
        public void execute(TestContext context) {
            context.executionOrder.add("after-fail");
        }

        @Override
        @SuppressWarnings("unchecked")
        public Class<? extends PipelineStep<?>>[] runAfter() {
            return new Class[]{FailStep.class};
        }
    }
}
