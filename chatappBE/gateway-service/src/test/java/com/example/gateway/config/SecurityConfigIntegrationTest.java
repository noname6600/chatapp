package com.example.gateway.config;

import com.example.gateway.controller.FallbackController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(classes = {
        SecurityConfig.class,
        SecurityConfigIntegrationTest.TestConfig.class,
        SecurityConfigIntegrationTest.ProtectedTestController.class,
        FallbackController.class
})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost/.well-known/jwks.json",
        "gateway.security.jwt.clock-skew-seconds=60"
})
class SecurityConfigIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private NimbusReactiveJwtDecoder jwtDecoder;

    @Test
    void authBootstrapEndpoint_doesNotReturn401_withoutAuthorizationHeader() {
        webTestClient.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus()
                .value(status -> org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(401));
    }

    @Test
    void oauthAuthorizationEndpoint_doesNotReturn401_withoutAuthorizationHeader() {
        webTestClient.get()
                .uri("/oauth2/authorization/google")
                .exchange()
                .expectStatus()
                .value(status -> org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(401));
    }

    @Test
    void oauthCallbackEndpoint_doesNotReturn401_withoutAuthorizationHeader() {
        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/login/oauth2/code/google")
                        .queryParam("code", "sample-code")
                        .queryParam("state", "sample-state")
                        .build())
                .exchange()
                .expectStatus()
                .value(status -> org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(401));
    }

    @Test
    void protectedEndpoint_returns401_whenAuthorizationMissing() {
        webTestClient.get()
                .uri("/api/users/ping")
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void protectedEndpoint_returns401_whenTokenInvalid() {
        when(jwtDecoder.decode("invalid-token"))
                                .thenReturn(Mono.error(new BadJwtException("invalid signature")));

        webTestClient.get()
                .uri("/api/users/ping")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void protectedEndpoint_doesNotReturn401_whenTokenValid() {
        Jwt jwt = Jwt.withTokenValue("valid-token")
                .header("alg", "RS256")
                .subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now().minusSeconds(10))
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        when(jwtDecoder.decode("valid-token"))
                .thenReturn(Mono.just(jwt));

        webTestClient.get()
                .uri("/api/users/ping")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .exchange()
                .expectStatus()
                .isOk();
    }

    @RestController
    static class ProtectedTestController {

        @GetMapping("/api/users/ping")
        String ping() {
            return "ok";
        }
    }

        @Configuration
        @EnableWebFlux
        @Import(SecurityConfig.class)
        static class TestConfig {

                @Bean
                NimbusReactiveJwtDecoder jwtDecoder() {
                        return mock(NimbusReactiveJwtDecoder.class);
                }

                @Bean
                WebTestClient webTestClient(ApplicationContext context) {
                        return WebTestClient.bindToApplicationContext(context)
                                        .apply(SecurityMockServerConfigurers.springSecurity())
                                        .configureClient()
                                        .build();
                }
        }
}
