package com.chatweb.realtime.connection;

import io.micrometer.core.instrument.Metrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Scheduled maintenance for stale session eviction and orphan index cleanup.
 *
 * Uses a Redis skip-lock so only one realtime-edge instance runs the expensive
 * Redis SCAN per cycle. Operations are idempotent so if the lock expires
 * another instance picks it up cleanly.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "realtime.session-registry.lease.cleanup-enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class EdgeSessionMaintenanceJob {

    private static final String LOCK_KEY = "realtime:maintenance:cleanup-lock";

    private final RealtimeSessionRegistry sessionRegistry;
    private final StringRedisTemplate redisTemplate;

    @Value("${realtime.session-registry.instance-id:edge-instance}")
    private String instanceId;

    @Value("${realtime.session-registry.lease.cleanup-interval-ms:30000}")
    private long cleanupIntervalMs;

    @Scheduled(fixedDelayString = "${realtime.session-registry.lease.cleanup-interval-ms:30000}")
    public void runMaintenance() {
        Duration lockTtl = Duration.ofMillis(Math.max(cleanupIntervalMs - 2000, 5000));
        if (!Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(LOCK_KEY, instanceId, lockTtl))) {
            return;
        }

        int staleEvicted = sessionRegistry.evictStaleSessions();
        int orphanCleaned = sessionRegistry.cleanupOrphanIndexes();

        if (staleEvicted > 0) {
            Metrics.counter("realtime.session.cleanup.stale.count").increment(staleEvicted);
        }
        if (orphanCleaned > 0) {
            Metrics.counter("realtime.session.cleanup.orphan.count").increment(orphanCleaned);
        }

        if (staleEvicted > 0 || orphanCleaned > 0) {
            log.info("[SESSION-MAINT] staleEvicted={} orphanCleaned={} activeLocal={} activeGlobal={}",
                    staleEvicted, orphanCleaned,
                    sessionRegistry.getActiveLocalSessionCount(),
                    sessionRegistry.getActiveSessionCount());
        }
    }
}
