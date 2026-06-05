package com.chatweb.common.event;

import org.slf4j.MDC;

public final class TraceContext {

    private static final String TRACE_ID_KEY = "trace_id";

    private TraceContext() {
    }

    public static String correlationIdOrEventId(String eventId) {
        String traceId = MDC.get(TRACE_ID_KEY);
        if (traceId == null || traceId.isBlank()) {
            return eventId;
        }
        return traceId;
    }
}
