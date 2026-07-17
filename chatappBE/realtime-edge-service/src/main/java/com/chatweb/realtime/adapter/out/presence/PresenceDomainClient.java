package com.chatweb.realtime.adapter.out.presence;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.Map;
import java.util.UUID;

/**
 * HTTP adapter that delegates presence command/state operations to presence-service.
 *
 * Failure policy:
 * - Non-fatal: failures never propagate to websocket session handling.
 * - High-priority operations (connect/disconnect/snapshot) log WARN with stack traces.
 * - Low-priority high-frequency operations (heartbeat/typing) use throttled WARN logging
 *   to reduce noisy repeated logs under transient failures.
 */
@Component
@Slf4j
public class PresenceDomainClient {

    private final RestClient restClient;
    private final long lowPriorityWarnIntervalMs;
    private final ConcurrentMap<String, Long> lowPriorityWarnTimestamps = new ConcurrentHashMap<>();

    public PresenceDomainClient(@Value("${services.presence.url:http://localhost:8084}") String presenceServiceUrl,
                                @Value("${services.presence.http.connect-timeout-ms:1000}") int connectTimeoutMs,
                                @Value("${services.presence.http.read-timeout-ms:2000}") int readTimeoutMs,
                                @Value("${services.presence.http.low-priority-warn-interval-ms:60000}") long lowPriorityWarnIntervalMs,
                                @Value("${internal.auth.header:X-Internal-Service-Token}") String internalAuthHeader,
                                @Value("${internal.auth.token:}") String internalAuthToken,
                                RestClient.Builder restClientBuilder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        RestClient.Builder builder = restClientBuilder
                .baseUrl(presenceServiceUrl)
                .requestFactory(requestFactory);
        if (internalAuthToken != null && !internalAuthToken.isBlank()) {
            builder = builder.defaultHeader(internalAuthHeader, internalAuthToken);
        }
        this.restClient = builder.build();
        this.lowPriorityWarnIntervalMs = lowPriorityWarnIntervalMs;
    }

    @PostConstruct
    void logFailurePolicy() {
        log.info(
                "[PRESENCE-CLIENT] failurePolicy=non-fatal lowPriorityWarnIntervalMs={}",
                lowPriorityWarnIntervalMs
        );
    }

    public void connect(String accessToken) {
        runHighPriority("connect", () -> {
            postNoBody("/api/v1/presence/ws/connect", accessToken);
            return null;
        });
    }

    public void disconnect(String accessToken) {
        runHighPriority("disconnect", () -> {
            postNoBody("/api/v1/presence/ws/disconnect", accessToken);
            return null;
        });
    }

    public void heartbeat(String accessToken, boolean active) {
        runLowPriority("heartbeat", () -> {
            postWithBody("/api/v1/presence/ws/heartbeat", accessToken, Map.of("active", active));
            return null;
        });
    }

    public void joinRoom(String accessToken, UUID roomId) {
        runStandardPriority("joinRoom", () -> {
            postNoBody("/api/v1/presence/ws/rooms/" + roomId + "/join", accessToken);
            return null;
        });
    }

    public void leaveRoom(String accessToken, UUID roomId) {
        runStandardPriority("leaveRoom", () -> {
            postNoBody("/api/v1/presence/ws/rooms/" + roomId + "/leave", accessToken);
            return null;
        });
    }

    public void typing(String accessToken, UUID roomId) {
        runLowPriority("typing", () -> {
            postNoBody("/api/v1/presence/ws/rooms/" + roomId + "/typing", accessToken);
            return null;
        });
    }

    public void stopTyping(String accessToken, UUID roomId) {
        runLowPriority("stopTyping", () -> {
            postNoBody("/api/v1/presence/ws/rooms/" + roomId + "/stop-typing", accessToken);
            return null;
        });
    }

    public JsonNode globalSnapshot(String accessToken) {
        return runHighPriority("globalSnapshot", () ->
                getDataNode("/api/v1/presence/global", accessToken)
        );
    }

    public JsonNode roomSnapshot(String accessToken, UUID roomId) {
        return runStandardPriority("roomSnapshot", () ->
                getDataNode("/api/v1/presence/room/" + roomId, accessToken)
        );
    }

    private JsonNode getDataNode(String path, String accessToken) {
        JsonNode root = restClient.get()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .retrieve()
                .body(JsonNode.class);

        if (root == null) {
            return null;
        }
        return root.path("data");
    }

    private void postNoBody(String path, String accessToken) {
        restClient.post()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .retrieve()
                .toBodilessEntity();
    }

    private void postWithBody(String path, String accessToken, Object body) {
        restClient.post()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private <T> T runHighPriority(String operation, ThrowingSupplier<T> call) {
        try {
            return call.get();
        } catch (Exception ex) {
            log.warn("[PRESENCE-CLIENT][HIGH] operation={} failed (non-fatal)", operation, ex);
            return null;
        }
    }

    private <T> T runStandardPriority(String operation, ThrowingSupplier<T> call) {
        try {
            return call.get();
        } catch (Exception ex) {
            log.warn("[PRESENCE-CLIENT][STANDARD] operation={} failed (non-fatal): {}", operation, ex.toString());
            return null;
        }
    }

    private <T> T runLowPriority(String operation, ThrowingSupplier<T> call) {
        try {
            return call.get();
        } catch (Exception ex) {
            if (shouldEmitLowPriorityWarn(operation)) {
                log.warn(
                        "[PRESENCE-CLIENT][LOW] operation={} failed (non-fatal, warn-throttled={}ms): {}",
                        operation,
                        lowPriorityWarnIntervalMs,
                        ex.toString()
                );
            } else {
                log.debug("[PRESENCE-CLIENT][LOW] operation={} failed (throttled)", operation);
            }
            return null;
        }
    }

    private boolean shouldEmitLowPriorityWarn(String operation) {
        long now = System.currentTimeMillis();
        Long previous = lowPriorityWarnTimestamps.put(operation, now);
        return previous == null || now - previous >= lowPriorityWarnIntervalMs;
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
