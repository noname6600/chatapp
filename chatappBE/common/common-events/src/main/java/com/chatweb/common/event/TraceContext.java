package com.chatweb.common.event;

import io.opentelemetry.api.trace.Span;

public final class TraceContext {

    private TraceContext() {
    }

    public static String correlationIdOrEventId(String eventId) {
        var ctx = Span.current().getSpanContext();
        return ctx.isValid() ? ctx.getTraceId() : eventId;
    }
}
