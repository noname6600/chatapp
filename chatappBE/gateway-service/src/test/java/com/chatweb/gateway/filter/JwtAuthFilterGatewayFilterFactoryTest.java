package com.chatweb.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthFilterGatewayFilterFactoryTest {

    @Test
    void apply_addsXUserIdHeader_whenJwtPrincipalExists() {
        JwtAuthFilterGatewayFilterFactory factory = new JwtAuthFilterGatewayFilterFactory();
        GatewayFilter filter = factory.apply(new Object());

        String userId = UUID.randomUUID().toString();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(userId)
                .issuedAt(Instant.now().minusSeconds(10))
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        SecurityContext context = new SecurityContextImpl(
            new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")))
        );
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/users/me").build());

        AtomicReference<ServerHttpRequest> forwardedRequest = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            forwardedRequest.set(ex.getRequest());
            return Mono.empty();
        };

        filter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(context)))
                .block();

        assertThat(forwardedRequest.get()).isNotNull();
        assertThat(forwardedRequest.get().getHeaders().getFirst("X-User-Id")).isEqualTo(userId);
    }

    @Test
    void apply_keepsRequestUnchanged_whenPrincipalIsNotJwt() {
        JwtAuthFilterGatewayFilterFactory factory = new JwtAuthFilterGatewayFilterFactory();
        GatewayFilter filter = factory.apply(new Object());

        SecurityContext context = new SecurityContextImpl(
                new TestingAuthenticationToken("user", "n/a", "ROLE_USER")
        );
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/users/me").build());

        AtomicReference<ServerHttpRequest> forwardedRequest = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            forwardedRequest.set(ex.getRequest());
            return Mono.empty();
        };

        filter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(context)))
                .block();

        assertThat(forwardedRequest.get()).isNotNull();
        assertThat(forwardedRequest.get().getHeaders().getFirst("X-User-Id")).isNull();
    }
}
