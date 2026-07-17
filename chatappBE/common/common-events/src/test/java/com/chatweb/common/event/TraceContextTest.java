package com.chatweb.common.event;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class TraceContextTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void correlationIdOrEventId_prefersTraceIdWhenPresent() {
        MDC.put("trace_id", "trace-123");

        assertThat(TraceContext.correlationIdOrEventId("evt-1")).isEqualTo("trace-123");
    }

    @Test
    void correlationIdOrEventId_fallsBackToEventIdWhenTraceMissing() {
        assertThat(TraceContext.correlationIdOrEventId("evt-1")).isEqualTo("evt-1");
    }
}