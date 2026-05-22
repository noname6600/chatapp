package com.chatweb.realtime.local;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.sync.RedisCommands;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class PhaseBLocalValidationRunner {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String EDGE_WS_URL = env("PHASE_B_EDGE_WS_URL", "ws://localhost:8085/realtime");
    private static final String NOTIFICATION_WS_URL = env("PHASE_B_NOTIFICATION_WS_URL", "ws://localhost:8086/realtime");
    private static final String NOTIFICATION_HTTP_BASE = env("PHASE_B_NOTIFICATION_HTTP_BASE", "http://localhost:8086");
    private static final String REDIS_URL = env("PHASE_B_REDIS_URL", "redis://localhost:6379");

    private static final String DB_URL = env("PHASE_B_NOTIFICATION_DB_URL", "jdbc:postgresql://localhost:5436/notification_service");
    private static final String DB_USER = env("PHASE_B_NOTIFICATION_DB_USER", "notification_user");
    private static final String DB_PASSWORD = env("PHASE_B_NOTIFICATION_DB_PASSWORD", "notification_password");

    private static final Path OUTPUT_JSON = Path.of(env("PHASE_B_LOCAL_SCENARIO_JSON",
            "realtime-edge-service/build/reports/phase-b-local-scenarios.json"));

    public static void main(String[] args) throws Exception {
        Instant startedAt = Instant.now();
        List<Map<String, Object>> scenarioResults = new ArrayList<>();
        boolean overallPassed = true;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        UUID userId = UUID.randomUUID();
        String token = userId.toString();

        WsClient edgeA = null;
        WsClient edgeB = null;
        WsClient edgeC = null;
        WsClient notificationWs = null;
        RedisClient redisClient = null;

        try {
            edgeA = connectWs(EDGE_WS_URL, token);

            UUID notificationId = insertUnreadNotification(userId);
            ScenarioResult commandResult = scenarioCommandRouting(edgeA, notificationId);
            scenarioResults.add(commandResult.toMap());
            overallPassed &= commandResult.passed;

            redisClient = RedisClient.create(REDIS_URL);
            ScenarioResult redisFanoutResult = scenarioRedisFanout(edgeA, redisClient, userId);
            scenarioResults.add(redisFanoutResult.toMap());
            overallPassed &= redisFanoutResult.passed;

            edgeB = connectWs(EDGE_WS_URL, token);
            ScenarioResult multiSessionResult = scenarioMultiSession(edgeA, edgeB, redisClient, userId);
            scenarioResults.add(multiSessionResult.toMap());
            overallPassed &= multiSessionResult.passed;

            closeQuietly(edgeB);
            edgeB = null;
            edgeC = connectWs(EDGE_WS_URL, token);
            ScenarioResult reconnectResult = scenarioReconnect(edgeC, redisClient, userId);
            scenarioResults.add(reconnectResult.toMap());
            overallPassed &= reconnectResult.passed;

            closeQuietly(edgeA);
            closeQuietly(edgeC);
            edgeA = null;
            edgeC = null;

            notificationWs = connectWs(NOTIFICATION_WS_URL, token);
            ScenarioResult rollbackResult = scenarioRollbackSimulation(notificationWs, httpClient, token);
            scenarioResults.add(rollbackResult.toMap());
            overallPassed &= rollbackResult.passed;
        } finally {
            closeQuietly(edgeA);
            closeQuietly(edgeB);
            closeQuietly(edgeC);
            closeQuietly(notificationWs);
            if (redisClient != null) {
                redisClient.shutdown();
            }
        }

        Instant finishedAt = Instant.now();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("startedAt", startedAt.toString());
        report.put("finishedAt", finishedAt.toString());
        report.put("durationMs", ChronoUnit.MILLIS.between(startedAt, finishedAt));
        report.put("overallPassed", overallPassed);
        report.put("scenarios", scenarioResults);

        Files.createDirectories(OUTPUT_JSON.getParent());
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(OUTPUT_JSON.toFile(), report);

        if (!overallPassed) {
            throw new IllegalStateException("Phase B local validation scenarios failed. See " + OUTPUT_JSON);
        }
    }

    private static ScenarioResult scenarioCommandRouting(WsClient edgeWs, UUID notificationId) {
        Instant start = Instant.now();
        String requestId = "cmd-" + UUID.randomUUID();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("notificationId", notificationId.toString());

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", "notification.command");
        message.put("domain", "notification");
        message.put("command", "mark-read");
        message.put("requestId", requestId);
        message.put("payload", payload);

        try {
            edgeWs.sendJson(message);
            boolean markedRead = waitForNotificationRead(notificationId, Duration.ofSeconds(12));
            long latency = ChronoUnit.MILLIS.between(start, Instant.now());
            if (!markedRead) {
                return ScenarioResult.fail("notification command routing through edge", latency,
                        "Notification row was not marked read after command via edge websocket");
            }
            return ScenarioResult.pass("notification command routing through edge", latency,
                    "mark-read command routed to notification-service and persisted");
        } catch (Exception ex) {
            long latency = ChronoUnit.MILLIS.between(start, Instant.now());
            return ScenarioResult.fail("notification command routing through edge", latency, ex.getMessage());
        }
    }

    private static ScenarioResult scenarioRedisFanout(WsClient edgeWs, RedisClient redisClient, UUID userId) {
        Instant start = Instant.now();
        String marker = "redis-fanout-" + UUID.randomUUID();
        String channel = "realtime.notification.user." + userId;
        String payload = "{\"type\":\"notification.new\",\"payload\":{\"marker\":\"" + marker + "\"}}";

        try (var connection = redisClient.connect()) {
            RedisCommands<String, String> commands = connection.sync();
            edgeWs.drain();
            commands.publish(channel, payload);

            String wsMessage = edgeWs.awaitMessageContaining(marker, Duration.ofSeconds(10));
            long latency = ChronoUnit.MILLIS.between(start, Instant.now());
            if (wsMessage == null) {
                return ScenarioResult.fail("notification Redis event fanout through edge", latency,
                        "Edge websocket did not receive redis fanout marker");
            }
            return ScenarioResult.pass("notification Redis event fanout through edge", latency,
                    "Redis event delivered to edge websocket client");
        } catch (Exception ex) {
            long latency = ChronoUnit.MILLIS.between(start, Instant.now());
            return ScenarioResult.fail("notification Redis event fanout through edge", latency, ex.getMessage());
        }
    }

    private static ScenarioResult scenarioMultiSession(WsClient edgeA, WsClient edgeB, RedisClient redisClient, UUID userId) {
        Instant start = Instant.now();
        String marker = "multi-session-" + UUID.randomUUID();
        String channel = "realtime.notification.user." + userId;
        String payload = "{\"type\":\"notification.unread.count.updated\",\"payload\":{\"marker\":\"" + marker + "\",\"unreadCount\":5}}";

        try (var connection = redisClient.connect()) {
            RedisCommands<String, String> commands = connection.sync();
            edgeA.drain();
            edgeB.drain();
            commands.publish(channel, payload);

            String a = edgeA.awaitMessageContaining(marker, Duration.ofSeconds(10));
            String b = edgeB.awaitMessageContaining(marker, Duration.ofSeconds(10));
            long latency = ChronoUnit.MILLIS.between(start, Instant.now());

            if (a == null || b == null) {
                return ScenarioResult.fail("multi-session delivery for one user", latency,
                        "Expected both sessions to receive marker but got sessionA=" + (a != null) + " sessionB=" + (b != null));
            }
            return ScenarioResult.pass("multi-session delivery for one user", latency,
                    "Both websocket sessions received the same user-targeted notification event");
        } catch (Exception ex) {
            long latency = ChronoUnit.MILLIS.between(start, Instant.now());
            return ScenarioResult.fail("multi-session delivery for one user", latency, ex.getMessage());
        }
    }

    private static ScenarioResult scenarioReconnect(WsClient reconnectedWs, RedisClient redisClient, UUID userId) {
        Instant start = Instant.now();
        String marker = "reconnect-" + UUID.randomUUID();
        String channel = "realtime.notification.user." + userId;
        String payload = "{\"type\":\"notification.unread.count.updated\",\"payload\":{\"marker\":\"" + marker + "\",\"unreadCount\":3}}";

        try (var connection = redisClient.connect()) {
            RedisCommands<String, String> commands = connection.sync();
            reconnectedWs.drain();
            commands.publish(channel, payload);

            String message = reconnectedWs.awaitMessageContaining(marker, Duration.ofSeconds(10));
            long latency = ChronoUnit.MILLIS.between(start, Instant.now());
            if (message == null) {
                return ScenarioResult.fail("reconnect behavior", latency,
                        "Reconnected websocket did not receive post-reconnect notification");
            }
            return ScenarioResult.pass("reconnect behavior", latency,
                    "Reconnected websocket session successfully received notification event");
        } catch (Exception ex) {
            long latency = ChronoUnit.MILLIS.between(start, Instant.now());
            return ScenarioResult.fail("reconnect behavior", latency, ex.getMessage());
        }
    }

    private static ScenarioResult scenarioRollbackSimulation(WsClient notificationWs, HttpClient httpClient, String token) {
        Instant start = Instant.now();
        String requestId = "rollback-" + UUID.randomUUID();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("command", "mark-all-read");
        body.put("requestId", requestId);

        try {
            notificationWs.drain();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(NOTIFICATION_HTTP_BASE + "/api/v1/notifications/realtime/commands"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                long latency = ChronoUnit.MILLIS.between(start, Instant.now());
                return ScenarioResult.fail("local rollback simulation back to service-local notification path", latency,
                        "Notification-service command endpoint returned " + response.statusCode());
            }

            String message = notificationWs.awaitMessageContaining("notification.unread.count.updated", Duration.ofSeconds(10));
            long latency = ChronoUnit.MILLIS.between(start, Instant.now());
            if (message == null) {
                return ScenarioResult.fail("local rollback simulation back to service-local notification path", latency,
                        "No unread-count websocket event observed on service-local endpoint");
            }

            return ScenarioResult.pass("local rollback simulation back to service-local notification path", latency,
                    "Service-local websocket received notification event after direct command call");
        } catch (Exception ex) {
            long latency = ChronoUnit.MILLIS.between(start, Instant.now());
            return ScenarioResult.fail("local rollback simulation back to service-local notification path", latency, ex.getMessage());
        }
    }

    private static WsClient connectWs(String baseUrl, String token) {
        URI uri = URI.create(baseUrl + "?token=" + token);
        WsClient listener = new WsClient();
        WebSocket webSocket = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .buildAsync(uri, listener)
                .join();
        listener.attach(webSocket);
        listener.awaitOpen(Duration.ofSeconds(8));
        return listener;
    }

    private static UUID insertUnreadNotification(UUID userId) throws Exception {
        UUID notificationId = UUID.randomUUID();

        String sql = """
                INSERT INTO notifications (
                    id,
                    user_id,
                    type,
                    reference_id,
                    room_id,
                    actor_id,
                    actor_display_name,
                    sender_name,
                    preview,
                    is_read,
                    action_required,
                    created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, notificationId);
            ps.setObject(2, userId);
            ps.setString(3, "MESSAGE");
            ps.setObject(4, null);
            ps.setObject(5, null);
            ps.setObject(6, null);
            ps.setString(7, "phase-b-local");
            ps.setString(8, "phase-b-local");
            ps.setString(9, "phase-b-local-validation");
            ps.setBoolean(10, false);
            ps.setBoolean(11, false);
            ps.setTimestamp(12, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        }

        return notificationId;
    }

    private static boolean waitForNotificationRead(UUID notificationId, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (isNotificationRead(notificationId)) {
                return true;
            }
            Thread.sleep(250);
        }
        return isNotificationRead(notificationId);
    }

    private static boolean isNotificationRead(UUID notificationId) throws Exception {
        String sql = "SELECT is_read FROM notifications WHERE id = ?";
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, notificationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }
                return rs.getBoolean(1);
            }
        }
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }

    private static void closeQuietly(WsClient client) {
        if (client != null) {
            try {
                client.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static final class ScenarioResult {
        private final String name;
        private final boolean passed;
        private final long latencyMs;
        private final String detail;

        private ScenarioResult(String name, boolean passed, long latencyMs, String detail) {
            this.name = name;
            this.passed = passed;
            this.latencyMs = latencyMs;
            this.detail = detail;
        }

        private static ScenarioResult pass(String name, long latencyMs, String detail) {
            return new ScenarioResult(name, true, latencyMs, detail);
        }

        private static ScenarioResult fail(String name, long latencyMs, String detail) {
            return new ScenarioResult(name, false, latencyMs, detail);
        }

        private Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", name);
            map.put("passed", passed);
            map.put("latencyMs", latencyMs);
            map.put("detail", detail);
            return map;
        }
    }

    private static final class WsClient implements WebSocket.Listener {
        private final CompletableFuture<Void> opened = new CompletableFuture<>();
        private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        private volatile WebSocket webSocket;

        private void attach(WebSocket webSocket) {
            this.webSocket = webSocket;
        }

        private void awaitOpen(Duration timeout) {
            try {
                opened.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (Exception ex) {
                throw new IllegalStateException("WebSocket connection did not open in time", ex);
            }
        }

        private void sendJson(Map<String, Object> json) throws Exception {
            String payload = OBJECT_MAPPER.writeValueAsString(json);
            webSocket.sendText(payload, true).join();
        }

        private String awaitMessageContaining(String token, Duration timeout) throws InterruptedException {
            long deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline) {
                long remainingMs = TimeUnit.NANOSECONDS.toMillis(Math.max(0, deadline - System.nanoTime()));
                String message = messages.poll(Math.max(1, remainingMs), TimeUnit.MILLISECONDS);
                if (message == null) {
                    continue;
                }
                if (message.contains(token)) {
                    return message;
                }
            }
            return null;
        }

        private void drain() {
            messages.clear();
        }

        private void close() {
            if (webSocket != null) {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
            }
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            WebSocket.Listener.super.onOpen(webSocket);
            opened.complete(null);
            webSocket.request(1);
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            messages.offer(data.toString());
            webSocket.request(1);
            return null;
        }
    }
}
