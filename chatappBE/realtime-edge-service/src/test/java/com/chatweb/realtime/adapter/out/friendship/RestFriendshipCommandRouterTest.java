package com.chatweb.realtime.adapter.out.friendship;

import com.chatweb.realtime.routing.command.FriendshipCommandRequest;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RestFriendshipCommandRouterTest {

    @Test
    void route_forwardsToFriendshipRealtimeCommandEndpoint() throws Exception {
        AtomicReference<CapturedRequest> captured = new AtomicReference<>();
        try (LocalHttpServer server = LocalHttpServer.start(exchange -> {
            captured.set(CapturedRequest.from(exchange));
            respondOk(exchange);
        })) {
            RestFriendshipCommandRouter router = new RestFriendshipCommandRouter(
                    RestClient.builder(),
                    server.baseUrl(),
                    1000,
                    1000
            );

            FriendshipCommandRequest request = new FriendshipCommandRequest();
            request.setCommand("accept-request");
            request.setTargetUserId(UUID.randomUUID());
            request.setRequestId("req-1");

            router.route("access-token", request);

            CapturedRequest capturedRequest = captured.get();
            assertThat(capturedRequest.method).isEqualTo("POST");
            assertThat(capturedRequest.path).isEqualTo("/api/v1/friends/realtime/commands");
            assertThat(capturedRequest.authorization).isEqualTo("Bearer access-token");
            assertThat(capturedRequest.body).contains("\"command\":\"accept-request\"");
            assertThat(capturedRequest.body).contains("\"requestId\":\"req-1\"");
        }
    }

    @Test
    void route_withoutToken_throws() {
        RestFriendshipCommandRouter router = new RestFriendshipCommandRouter(
                RestClient.builder(),
                "http://localhost:65534",
                100,
                100
        );

        FriendshipCommandRequest request = new FriendshipCommandRequest();
        request.setCommand("block");
        request.setTargetUserId(UUID.randomUUID());

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> router.route("", request));
    }

    private static void respondOk(HttpExchange exchange) throws IOException {
        byte[] response = new byte[0];
        exchange.sendResponseHeaders(200, response.length);
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
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    body
            );
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
