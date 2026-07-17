package com.chatweb.realtime.adapter.in.websocket;

import io.micrometer.core.instrument.Metrics;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class RealtimeSideEffectQueue {

    private final ThreadPoolExecutor executor;

    public RealtimeSideEffectQueue(
            @Value("${realtime.ws.side-effects.workers:4}") int workers,
            @Value("${realtime.ws.side-effects.queue-capacity:1000}") int queueCapacity
    ) {
        int poolSize = Math.max(1, workers);
        int capacity = Math.max(50, queueCapacity);
        this.executor = new ThreadPoolExecutor(
                poolSize,
                poolSize,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(capacity)
        );
    }

    public boolean submit(String operation, Runnable task) {
        try {
            executor.execute(() -> {
                try {
                    task.run();
                } catch (Exception ex) {
                    Metrics.counter("realtime.ws.side_effect.failure", "operation", safeTag(operation)).increment();
                    log.warn("[WS-SIDE-EFFECT] operation={} failed", operation, ex);
                }
            });
            return true;
        } catch (RejectedExecutionException ex) {
            Metrics.counter("realtime.ws.side_effect.drop", "operation", safeTag(operation)).increment();
            log.warn("[WS-SIDE-EFFECT] dropped operation={} due to full queue", operation);
            return false;
        }
    }

    private String safeTag(String operation) {
        return operation == null || operation.isBlank() ? "unknown" : operation;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
