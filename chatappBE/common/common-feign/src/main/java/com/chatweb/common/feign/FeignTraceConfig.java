package com.chatweb.common.feign;

import feign.RequestInterceptor;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Replaced by OTel Java agent W3C traceparent propagation — no longer needed.
public class FeignTraceConfig {
}
