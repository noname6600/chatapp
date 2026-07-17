package com.chatweb.realtime.delivery;

import io.micrometer.core.instrument.Metrics;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
public class WebSocketOutboundDeliveryQueue {

    private final int queueCapacity;
    private final ExecutorService executor;
    private final Map<String, SessionQueue> sessionQueues = new ConcurrentHashMap<>();

    public WebSocketOutboundDeliveryQueue(
            @Value("${realtime.delivery.queue.capacity:500}") int queueCapacity,
            @Value("${realtime.delivery.queue.workers:4}") int workers
    ) {
        this.queueCapacity = Math.max(10, queueCapacity);
        this.executor = Executors.newFixedThreadPool(Math.max(1, workers));
    }

    public boolean enqueue(String sessionId, WebSocketSession session, String payload, String deliveryType) {
        if (sessionId == null || session == null || payload == null || !session.isOpen()) {
            return false;
        }

        SessionQueue queue = sessionQueues.computeIfAbsent(sessionId, key -> new SessionQueue(queueCapacity));
        boolean offered = queue.messages.offer(new TextMessage(payload));
        if (!offered) {
            Metrics.counter("realtime.delivery.queue.drop", "deliveryType", safeTag(deliveryType)).increment();
            log.warn("[DELIVERY-QUEUE] Drop message due to full queue sessionId={} deliveryType={}", sessionId, deliveryType);
            return false;
        }

        tryScheduleDrain(sessionId, session, queue, deliveryType);
        return true;
    }

    public void clearSession(String sessionId) {
        if (sessionId != null) {
            sessionQueues.remove(sessionId);
        }
    }

    private void tryScheduleDrain(String sessionId, WebSocketSession session, SessionQueue queue, String deliveryType) {
        if (!queue.draining.compareAndSet(false, true)) {
            return;
        }

        executor.execute(() -> drain(sessionId, session, queue, deliveryType));
    }

    private void drain(String sessionId, WebSocketSession session, SessionQueue queue, String deliveryType) {
        try {
            TextMessage message;
            while ((message = queue.messages.poll()) != null) {
                if (!session.isOpen()) {
                    clearSession(sessionId);
                    break;
                }

                try {
                    session.sendMessage(message);
                    Metrics.counter("realtime.delivery.local.success", "deliveryType", safeTag(deliveryType)).increment();
                } catch (Exception ex) {
                    Metrics.counter("realtime.delivery.local.failure", "deliveryType", safeTag(deliveryType)).increment();
                    log.warn("[DELIVERY-QUEUE] Send failed sessionId={} deliveryType={}", sessionId, deliveryType, ex);
                    clearSession(sessionId);
                    break;
                }
            }
        } finally {
            queue.draining.set(false);
            if (!queue.messages.isEmpty() && session.isOpen()) {
                tryScheduleDrain(sessionId, session, queue, deliveryType);
            } else if (!session.isOpen() || queue.messages.isEmpty()) {
                clearSession(sessionId);
            }
        }
    }

    private String safeTag(String deliveryType) {
        return deliveryType == null || deliveryType.isBlank() ? "unknown" : deliveryType;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    private static final class SessionQueue {
        private final LinkedBlockingQueue<TextMessage> messages;
        private final AtomicBoolean draining = new AtomicBoolean(false);

        private SessionQueue(int capacity) {
            this.messages = new LinkedBlockingQueue<>(capacity);
        }
    }
}
