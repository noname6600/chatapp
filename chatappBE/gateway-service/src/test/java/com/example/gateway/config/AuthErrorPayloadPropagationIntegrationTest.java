package com.example.gateway.config;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class AuthErrorPayloadPropagationIntegrationTest {

    private static final MockWebServer AUTH_BACKEND;

    static {
        try {
            AUTH_BACKEND = new MockWebServer();
            AUTH_BACKEND.start();
        } catch (IOException exception) {
            throw new RuntimeException("Failed to start auth backend mock server", exception);
        }
    }

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("AUTH_SERVICE_HOST", () -> "localhost");
        registry.add("AUTH_SERVICE_PORT", AUTH_BACKEND::getPort);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> "http://localhost/.well-known/jwks.json");
        registry.add("spring.cloud.gateway.server.webflux.default-filters[0]",
                () -> "DedupeResponseHeader=Access-Control-Allow-Origin Access-Control-Allow-Credentials, RETAIN_FIRST");
    }

    @AfterAll
    static void tearDown() throws IOException {
        AUTH_BACKEND.shutdown();
    }

    @Test
    void auth401Payload_isPropagatedUnchangedThroughGateway() throws Exception {
        AUTH_BACKEND.enqueue(new MockResponse()
                .setResponseCode(401)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          \"success\": false,
                          \"error\": {
                            \"code\": \"UNAUTHORIZED\",
                            \"message\": \"Invalid credentials\"
                          }
                        }
                        """));

        webTestClient.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          \"username\": \"missing@example.com\",
                          \"password\": \"Password1!\"
                        }
                        """)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("UNAUTHORIZED")
                .jsonPath("$.error.message").isEqualTo("Invalid credentials");

        RecordedRequest recordedRequest = AUTH_BACKEND.takeRequest(3, TimeUnit.SECONDS);
        assertThat(recordedRequest).isNotNull();
        assertThat(recordedRequest.getPath()).isEqualTo("/api/v1/auth/login");
        assertThat(recordedRequest.getMethod()).isEqualTo("POST");
    }
}
