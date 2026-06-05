package com.chatweb.common.web.response;

import io.opentelemetry.api.trace.Span;
import lombok.Getter;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;


@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private Instant timestamp;
    private T data;
    private ApiError error;
    private String traceId;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .timestamp(Instant.now())
                .data(data)
                .traceId(currentTraceId())
                .build();
    }

    public static ApiResponse<Void> success() {
        return ApiResponse.<Void>builder()
                .success(true)
                .timestamp(Instant.now())
                .traceId(currentTraceId())
                .build();
    }

    public static ApiResponse<Void> failure(ApiError error) {
        return ApiResponse.<Void>builder()
                .success(false)
                .timestamp(Instant.now())
                .error(error)
                .traceId(currentTraceId())
                .build();
    }

    private static String currentTraceId() {
        var ctx = Span.current().getSpanContext();
        return ctx.isValid() ? ctx.getTraceId() : null;
    }
}