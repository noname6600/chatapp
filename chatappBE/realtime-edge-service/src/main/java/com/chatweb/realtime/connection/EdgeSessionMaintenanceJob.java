package com.chatweb.realtime.connection;

import io.micrometer.core.instrument.Metrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled maintenance for stale session eviction and orphan index cleanup.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "realtime.session-registry.lease.cleanup-enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class EdgeSessionMaintenanceJob {

    private final RealtimeSessionRegistry sessionRegistry;

    @Scheduled(fixedDelayString = "${realtime.session-registry.lease.cleanup-interval-ms:30000}")
    public void runMaintenance() {
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
                    staleEvicted,
                    orphanCleaned,
                    sessionRegistry.getActiveLocalSessionCount(),
                    sessionRegistry.getActiveSessionCount());
        }
    }
}
