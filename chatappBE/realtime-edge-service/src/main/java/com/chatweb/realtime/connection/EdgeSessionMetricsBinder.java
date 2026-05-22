package com.chatweb.realtime.connection;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Registers ownership-aware active session gauges.
 */
@Component
@RequiredArgsConstructor
public class EdgeSessionMetricsBinder {

    private final MeterRegistry meterRegistry;
    private final RealtimeSessionRegistry sessionRegistry;

    @PostConstruct
    public void bind() {
        Gauge.builder("realtime.sessions.local.active", sessionRegistry, RealtimeSessionRegistry::getActiveLocalSessionCount)
                .description("Active websocket sessions owned by this edge instance")
                .register(meterRegistry);

        Gauge.builder("realtime.sessions.global.active", sessionRegistry, RealtimeSessionRegistry::getActiveSessionCount)
                .description("Active websocket sessions visible in session registry")
                .register(meterRegistry);
    }
}
