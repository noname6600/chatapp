package com.example.common.core.pipeline;

public class StepRetryPolicy {

    private final int maxAttempts;
    private final long backoffMillis;

    public StepRetryPolicy(int maxAttempts, long backoffMillis) {
        this.maxAttempts = maxAttempts;
        this.backoffMillis = backoffMillis;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public long getBackoffMillis() {
        return backoffMillis;
    }

    public static StepRetryPolicy noRetry() {
        return new StepRetryPolicy(1, 0);
    }

    public static StepRetryPolicy of(int attempts, long backoffMillis) {
        return new StepRetryPolicy(attempts, backoffMillis);
    }
}