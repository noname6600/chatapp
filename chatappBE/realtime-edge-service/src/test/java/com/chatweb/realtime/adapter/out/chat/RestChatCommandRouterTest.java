package com.chatweb.realtime.adapter.out.chat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RestChatCommandRouterTest {

    @Test
    void sendMessage_forwardsPayloadToChatService() throws Exception {
        AtomicReference<CapturedRequest> captured = new AtomicReference<>();
        try (LocalHttpServer server = LocalHttpServer.start(exchange -> {
            captured.set(CapturedRequest.from(exchange));
            respondOk(exchange);
        })) {
            RestChatCommandRouter router = new RestChatCommandRouter(
                    RestClient.builder(),
                    server.baseUrl(),
                    1000,
                    1000
            );

            UUID roomId = UUID.randomUUID();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("roomId", roomId.toString());
            payload.put("content", "hello world");
            payload.put("clientMessageId", "client-1");

            router.sendMessage("access-token", payload);

            CapturedRequest request = captured.get();
            assertThat(request.method).isEqualTo("POST");
            assertThat(request.path).isEqualTo("/api/v1/messages");
            assertThat(request.authorization).isEqualTo("Bearer access-token");
            assertThat(request.body).contains("\"content\":\"hello world\"");
            assertThat(request.body).contains(roomId.toString());
        }
    }

    @Test
    void joinRoom_forwardsToJoinEndpoint() throws Exception {
        AtomicReference<CapturedRequest> captured = new AtomicReference<>();
        try (LocalHttpServer server = LocalHttpServer.start(exchange -> {
            captured.set(CapturedRequest.from(exchange));
            respondOk(exchange);
        })) {
            RestChatCommandRouter router = new RestChatCommandRouter(
                    RestClient.builder(),
                    server.baseUrl(),
                    1000,
                    1000
            );

            UUID roomId = UUID.randomUUID();
            router.joinRoom("access-token", roomId);

            CapturedRequest request = captured.get();
            assertThat(request.method).isEqualTo("POST");
            assertThat(request.path).isEqualTo("/api/v1/rooms/" + roomId + "/join");
            assertThat(request.authorization).isEqualTo("Bearer access-token");
            assertThat(request.body).isEmpty();
        }
    }

    @Test
    void canAccessRoom_returnsTrueWhenMembershipCheckEndpointIsAccessible() throws Exception {
        AtomicReference<CapturedRequest> captured = new AtomicReference<>();
        try (LocalHttpServer server = LocalHttpServer.start(exchange -> {
            captured.set(CapturedRequest.from(exchange));
            respond(exchange, 200);
        })) {
            RestChatCommandRouter router = new RestChatCommandRouter(
                    RestClient.builder(),
                    server.baseUrl(),
                    1000,
                    1000
            );

            UUID roomId = UUID.randomUUID();
            boolean allowed = router.canAccessRoom("access-token", roomId);

            assertThat(allowed).isTrue();
            CapturedRequest request = captured.get();
            assertThat(request.method).isEqualTo("GET");
            assertThat(request.path).isEqualTo("/api/v1/rooms/" + roomId + "/member-count");
            assertThat(request.authorization).isEqualTo("Bearer access-token");
        }
    }

    @Test
    void canAccessRoom_returnsFalseWhenMembershipCheckEndpointRejects() throws Exception {
        AtomicReference<CapturedRequest> captured = new AtomicReference<>();
        try (LocalHttpServer server = LocalHttpServer.start(exchange -> {
            captured.set(CapturedRequest.from(exchange));
            respond(exchange, 403);
        })) {
            RestChatCommandRouter router = new RestChatCommandRouter(
                    RestClient.builder(),
                    server.baseUrl(),
                    1000,
                    1000
            );

            UUID roomId = UUID.randomUUID();
            boolean allowed = router.canAccessRoom("access-token", roomId);

            assertThat(allowed).isFalse();
            CapturedRequest request = captured.get();
            assertThat(request.method).isEqualTo("GET");
            assertThat(request.path).isEqualTo("/api/v1/rooms/" + roomId + "/member-count");
        }
    }

    private static void respondOk(HttpExchange exchange) throws IOException {
        respond(exchange, 200);
    }

    private static void respond(HttpExchange exchange, int status) throws IOException {
        byte[] response = new byte[0];
        exchange.sendResponseHeaders(status, response.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(response);
        }
    }

    private static final class CapturedRequest {
        private final String method;
        private final String path;
        private final String authorization;
        private final String body;

        private CapturedRequest(String method, String path, String authorization, String body) {
            this.method = method;
            this.path = path;
            this.authorization = authorization;
            this.body = body;
        }

        private static CapturedRequest from(HttpExchange exchange) throws IOException {
            String body;
            try (InputStream inputStream = exchange.getRequestBody()) {
                body = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
            return new CapturedRequest(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath() + normalizeQuery(exchange.getRequestURI().getQuery()),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    body
            );
        }

        private static String normalizeQuery(String query) {
            return query == null ? "" : "?" + query;
        }
    }

    private static final class LocalHttpServer implements AutoCloseable {
        private final HttpServer server;

        private LocalHttpServer(HttpServer server) {
            this.server = server;
        }

        static LocalHttpServer start(HttpHandler handler) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/", handler);
            server.start();
            return new LocalHttpServer(server);
        }

        String baseUrl() {
            return "http://localhost:" + server.getAddress().getPort();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}