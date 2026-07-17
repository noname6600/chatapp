package com.chatweb.realtime.adapter.out.notification;

import com.chatweb.realtime.routing.command.NotificationCommandRequest;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestNotificationCommandRouterTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void forwardsCommandToNotificationServiceEndpoint() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> auth = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();

        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/notifications/realtime/commands", exchange -> handleOk(exchange, method, path, auth, body));
        server.start();

        String baseUrl = "http://localhost:" + server.getAddress().getPort();
        RestNotificationCommandRouter router = new RestNotificationCommandRouter(RestClient.builder(), baseUrl);

        NotificationCommandRequest request = new NotificationCommandRequest();
        request.setCommand("mark-read");
        request.setNotificationId(UUID.randomUUID());

        router.route("edge-token", request);

        assertThat(method.get()).isEqualTo("POST");
        assertThat(path.get()).isEqualTo("/api/v1/notifications/realtime/commands");
        assertThat(auth.get()).isEqualTo("Bearer edge-token");
        assertThat(body.get()).contains("mark-read");
        assertThat(body.get()).contains("notificationId");
    }

    @Test
    void non2xxResponseIsReportedAsFailure() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/notifications/realtime/commands", exchange -> handleStatus(exchange, 401, "unauthorized"));
        server.start();

        String baseUrl = "http://localhost:" + server.getAddress().getPort();
        RestNotificationCommandRouter router = new RestNotificationCommandRouter(RestClient.builder(), baseUrl);

        NotificationCommandRequest request = new NotificationCommandRequest();
        request.setCommand("mark-all-read");

        assertThatThrownBy(() -> router.route("bad-token", request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to forward notification command");
    }

    private void handleOk(HttpExchange exchange,
                          AtomicReference<String> method,
                          AtomicReference<String> path,
                          AtomicReference<String> auth,
                          AtomicReference<String> body) throws IOException {
        method.set(exchange.getRequestMethod());
        path.set(exchange.getRequestURI().getPath());
        auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
        body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, response.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(response);
        }
    }

    private void handleStatus(HttpExchange exchange, int status, String message) throws IOException {
        byte[] response = message.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, response.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(response);
        }
    }
}
