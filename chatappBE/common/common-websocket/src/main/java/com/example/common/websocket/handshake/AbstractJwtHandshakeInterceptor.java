package com.example.common.websocket.handshake;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

@Slf4j
public abstract class AbstractJwtHandshakeInterceptor
        implements HandshakeInterceptor, IJwtHandshakeInterceptor {

    public static final String ATTR_USER_ID = "userId";

    /** Optional — not all services include Micrometer; safe to leave null. */
    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    /** Structured rejection reasons for logging, metrics, and response headers. */
    public enum RejectionReason {
        SERVLET_NOT_FOUND,
        MISSING_TOKEN,
        INVALID_FORMAT,
        DECODE_FAILED,
        NULL_USER_ID
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        log.info("[WS-HANDSHAKE] Incoming WebSocket connection attempt uri={}", request.getURI());

        // --- Task 3.1/3.2: Validate request type and log query params ---
        if (!(request instanceof ServletServerHttpRequest servlet)) {
            log.warn("[WS-HANDSHAKE] REJECT reason={} uri={}", RejectionReason.SERVLET_NOT_FOUND, request.getURI());
            handleRejection(response, RejectionReason.SERVLET_NOT_FOUND, null);
            return false;
        }

        HttpServletRequest httpRequest = servlet.getServletRequest();
        java.util.List<String> paramNames = Collections.list(httpRequest.getParameterNames());
        log.info("[WS-HANDSHAKE] Query parameters present: {}", paramNames);

        // --- Task 1.2 / 3.3: Token presence check ---
        String token = httpRequest.getParameter("token");
        response.getHeaders().set("X-WebSocket-Token-Present", token != null ? "true" : "false");

        if (token == null) {
            log.warn("[WS-HANDSHAKE] REJECT reason={} presentParams={}", RejectionReason.MISSING_TOKEN, paramNames);
            handleRejection(response, RejectionReason.MISSING_TOKEN, null);
            return false;
        }

        if (token.isBlank()) {
            log.warn("[WS-HANDSHAKE] REJECT reason={} tokenLength={}", RejectionReason.INVALID_FORMAT, token.length());
            handleRejection(response, RejectionReason.INVALID_FORMAT, null);
            return false;
        }

        // Task 3.2: log token length for gateway truncation detection
        log.info("[WS-HANDSHAKE] Token found tokenLength={} tokenPrefix={}",
                token.length(),
                token.length() > 20 ? token.substring(0, 20) + "..." : "short-token");

        // --- Task 1.3: Resolve userId and log decode failures ---
        UUID userId;
        try {
            userId = resolveUserId(token);
        } catch (Exception e) {
            log.warn("[WS-HANDSHAKE] REJECT reason={} exceptionType={} message={}",
                    RejectionReason.DECODE_FAILED, e.getClass().getSimpleName(), e.getMessage());
            handleRejection(response, RejectionReason.DECODE_FAILED, e.getClass().getSimpleName());
            return false;
        }

        // --- Task 1.4: Null userId check ---
        if (userId == null) {
            log.warn("[WS-HANDSHAKE] REJECT reason={}", RejectionReason.NULL_USER_ID);
            handleRejection(response, RejectionReason.NULL_USER_ID, null);
            return false;
        }

        // --- Task 1.5: Successful handshake ---
        attributes.put(ATTR_USER_ID, userId);
        log.info("[WS-HANDSHAKE] ACCEPT userId={}", userId);
        if (meterRegistry != null) {
            meterRegistry.counter("websocket.handshake.success").increment();
        }
        onHandshakeAccepted(userId);
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
        // no-op
    }

    // --- Tasks 2.1-2.3: Set diagnostic response headers + record rejection metric ---
    private void handleRejection(ServerHttpResponse response, RejectionReason reason, String errorDetail) {
        try {
            response.getHeaders().set("X-WebSocket-Rejection-Reason", reason.name());
            if (errorDetail != null) {
                response.getHeaders().set("X-WebSocket-Error-Details", errorDetail);
            }
        } catch (Exception ignored) {
            // Headers may not be settable after response is committed; safe to ignore
        }
        if (meterRegistry != null) {
            meterRegistry.counter("websocket.handshake.rejected", "reason", reason.name()).increment();
        }
        onHandshakeRejected(reason, errorDetail);
    }

    /**
     * Hook for subclasses to record metrics on successful handshakes.
     * Default: no-op.
     */
    protected void onHandshakeAccepted(UUID userId) {}

    /**
     * Hook for subclasses to record metrics on rejected handshakes.
     * Default: no-op.
     *
     * @param reason     the structured rejection reason
     * @param errorDetail optional detail (e.g. exception type name)
     */
    protected void onHandshakeRejected(RejectionReason reason, String errorDetail) {}
}
