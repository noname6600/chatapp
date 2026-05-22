package com.chatweb.realtime.dispatch;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Metrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.locks.LockSupport;

/**
 * Publishes cross-instance websocket delivery handoff events to Redis.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EdgeDeliveryHandoffPublisher {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Value("${realtime.dispatch.handoff.enabled:false}")
    private boolean enabled;

    @Value("${realtime.dispatch.handoff.channel-prefix:realtime.edge.handoff}")
    private String channelPrefix;

    @Value("${realtime.dispatch.handoff.publish.max-attempts:3}")
    private int maxAttempts;

    @Value("${realtime.dispatch.handoff.publish.initial-backoff-ms:50}")
    private long initialBackoffMs;

    @Value("${realtime.dispatch.handoff.publish.backoff-multiplier:2.0}")
    private double backoffMultiplier;

    public boolean publish(EdgeDeliveryHandoffEvent event) {
        if (!enabled || event == null || event.getTargetInstanceId() == null || event.getTargetInstanceId().isBlank()) {
            return false;
        }

        String channel = buildChannel(event.getTargetInstanceId());
        int attempts = Math.max(1, maxAttempts);
        long backoffMs = Math.max(1L, initialBackoffMs);

        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                redis.convertAndSend(channel, objectMapper.writeValueAsString(event));
                Metrics.counter("realtime.dispatch.remote.handoff.publish.success", "targetInstanceId", event.getTargetInstanceId()).increment();
                log.debug("[HANDOFF][PUB][SUCCESS] attempt={} targetInstance={} deliveryType={} eventType={} originalEventId={} sessions={} channel={}",
                        attempt,
                        event.getTargetInstanceId(),
                        event.getDeliveryType(),
                        event.getEventType(),
                        event.getOriginalEventId(),
                        event.getTargetSessionIds() == null ? 0 : event.getTargetSessionIds().size(),
                        channel);
                return true;
            } catch (Exception ex) {
                if (attempt >= attempts) {
                    Metrics.counter("realtime.dispatch.remote.handoff.publish.failure", "targetInstanceId", event.getTargetInstanceId()).increment();
                    log.warn("[HANDOFF][PUB][FAIL] attempts={} targetInstance={} deliveryType={} eventType={} originalEventId={} channel={}",
                            attempts,
                            event.getTargetInstanceId(),
                            event.getDeliveryType(),
                            event.getEventType(),
                            event.getOriginalEventId(),
                            channel,
                            ex);
                    return false;
                }

                log.warn("[HANDOFF][PUB][RETRY] attempt={} maxAttempts={} targetInstance={} eventType={} originalEventId={} backoffMs={}",
                        attempt,
                        attempts,
                        event.getTargetInstanceId(),
                        event.getEventType(),
                        event.getOriginalEventId(),
                        backoffMs,
                        ex);
                LockSupport.parkNanos(backoffMs * 1_000_000L);
                if (Thread.currentThread().isInterrupted()) {
                    Thread.currentThread().interrupt();
                    Metrics.counter("realtime.dispatch.remote.handoff.publish.failure", "targetInstanceId", event.getTargetInstanceId()).increment();
                    log.warn("[HANDOFF][PUB][INTERRUPTED] targetInstance={} eventType={} originalEventId={}",
                            event.getTargetInstanceId(), event.getEventType(), event.getOriginalEventId());
                    return false;
                }
                backoffMs = Math.max(1L, (long) (backoffMs * Math.max(1.0d, backoffMultiplier)));
            }
        }

        return false;
    }

    public String topicPattern() {
        return normalizePrefix(channelPrefix) + ".*";
    }

    private String buildChannel(String targetInstanceId) {
        return normalizePrefix(channelPrefix) + "." + targetInstanceId;
    }

    private String normalizePrefix(String value) {
        if (value == null || value.isBlank()) {
            return "realtime.edge.handoff";
        }
        return value.endsWith(".") ? value.substring(0, value.length() - 1) : value;
    }
}
