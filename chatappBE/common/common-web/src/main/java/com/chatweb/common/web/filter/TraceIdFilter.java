package com.chatweb.common.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// Puts the container hostname into MDC so every log line shows which instance
// handled the request. Trace ID is injected automatically by the OTel Java agent.
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    private static final String INSTANCE_ID_KEY = "instanceId";
    private static final String INSTANCE_ID = System.getenv().getOrDefault("HOSTNAME", "unknown");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        MDC.put(INSTANCE_ID_KEY, INSTANCE_ID);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(INSTANCE_ID_KEY);
        }
    }
}
