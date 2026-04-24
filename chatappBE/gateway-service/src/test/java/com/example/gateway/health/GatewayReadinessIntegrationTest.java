package com.example.gateway.health;

import com.example.gateway.GatewayApplication;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.TestConfiguration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = GatewayApplication.class)
@Import(GatewayReadinessIntegrationTest.TestSecurityConfig.class)
class GatewayReadinessIntegrationTest {

    private static final AtomicBoolean HEALTHY = new AtomicBoolean(true);
    private static MockWebServer mockWebServer;

    @Autowired
    private WebTestClient webTestClient;

    @BeforeAll
    static void startServer() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                if (path != null && path.startsWith("/actuator/health")) {
                    return HEALTHY.get()
                            ? new MockResponse().setResponseCode(200).setBody("{\"status\":\"UP\"}")
                            : new MockResponse().setResponseCode(503).setBody("{\"status\":\"DOWN\"}");
                }
                if (path != null && path.startsWith("/api/v1/test/ping")) {
                    return new MockResponse().setResponseCode(200).setBody("pong");
                }
                return new MockResponse().setResponseCode(404);
            }
        });
        mockWebServer.start();
    }

    @AfterAll
    static void stopServer() throws IOException {
        if (mockWebServer != null) {
            mockWebServer.shutdown();
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        String baseUrl = String.format("http://localhost:%d", mockWebServer.getPort());

        registry.add("spring.cloud.gateway.routes[0].id", () -> "test-service");
        registry.add("spring.cloud.gateway.routes[0].uri", () -> baseUrl);
        registry.add("spring.cloud.gateway.routes[0].predicates[0]", () -> "Path=/api/v1/test/**");
        registry.add("spring.cloud.gateway.routes[0].filters[0]", () -> "CircuitBreaker");
        registry.add("spring.cloud.gateway.routes[0].filters[0].args.name", () -> "test-cb");
        registry.add("spring.cloud.gateway.routes[0].filters[0].args.fallbackUri", () -> "forward:/fallback/service-unavailable");

        registry.add("resilience4j.circuitbreaker.instances.test-cb.baseConfig", () -> "default");
        registry.add("resilience4j.timelimiter.instances.test-cb.baseConfig", () -> "default");

        registry.add("gateway.readiness.required-services", () -> baseUrl);
        registry.add("gateway.readiness.health-path", () -> "/actuator/health");
        registry.add("gateway.readiness.timeout", () -> "2s");
        registry.add("management.endpoint.health.group.readiness.include", () -> "readinessState,downstreamRoutes");
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> "http://localhost/.well-known/jwks.json");
    }

    @Test
    void readinessRemainsDownWhenDownstreamUnhealthy() {
        HEALTHY.set(false);

        webTestClient.get()
                .uri("/actuator/health/readiness")
                .exchange()
                .expectStatus()
                .is5xxServerError();
    }

    @Test
    void firstRequestAfterReadinessDoesNotReturnFallback() {
        HEALTHY.set(true);

        webTestClient.get()
                .uri("/actuator/health/readiness")
                .exchange()
                .expectStatus()
                .isOk();

        webTestClient.get()
                .uri("/api/v1/test/ping")
                .exchange()
                .expectStatus()
                .isOk();
    }

    @TestConfiguration
    static class TestSecurityConfig {

        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE)
        SecurityWebFilterChain testSecurityWebFilterChain(ServerHttpSecurity http) {
            return http
                    .csrf(ServerHttpSecurity.CsrfSpec::disable)
                    .authorizeExchange(auth -> auth.anyExchange().permitAll())
                    .build();
        }
    }
}
