package com.example.common.web.response;

import lombok.Getter;
import org.slf4j.MDC;

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
                .traceId(MDC.get("traceId"))
                .build();
    }

    public static ApiResponse<Void> success() {
        return ApiResponse.<Void>builder()
                .success(true)
                .timestamp(Instant.now())
                .traceId(MDC.get("traceId"))
                .build();
    }

    public static ApiResponse<Void> failure(ApiError error) {
        return ApiResponse.<Void>builder()
                .success(false)
                .timestamp(Instant.now())
                .error(error)
                .traceId(MDC.get("traceId"))
                .build();
    }
}