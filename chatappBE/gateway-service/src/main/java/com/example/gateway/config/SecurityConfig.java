package com.example.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.server.ServerWebExchange;

import java.time.Duration;

@Configuration
@EnableWebFluxSecurity
@Slf4j
public class SecurityConfig {

    private static final String[] PUBLIC_AUTH_PATHS = {
            "/api/v1/auth/**",
            "/api/auth/**",
            "/oauth2/authorization/**",
            "/login/oauth2/code/**",
            "/.well-known/**"
    };

    private static final String[] PUBLIC_GENERAL_PATHS = {
            "/actuator/**",
            "/fallback/**",
            "/ws/**"
    };

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    @Value("${gateway.security.jwt.clock-skew-seconds:60}")
    private long clockSkewSeconds;

    @Bean
        public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                // stateless API gateway — CSRF not applicable
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
            // Gateway CORS is handled by CorsWebFilter in GatewayConfig.
            .cors(ServerHttpSecurity.CorsSpec::disable)
                .authorizeExchange(auth -> auth
                        .pathMatchers(PUBLIC_AUTH_PATHS).permitAll()
                        .pathMatchers(PUBLIC_GENERAL_PATHS).permitAll()
                        .anyExchange().authenticated()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((exchange, ex) -> {
                            String reason = classify401Reason(exchange, ex);
                            log.warn("gateway_auth_reject reason={} path={} message={}", reason,
                                    exchange.getRequest().getURI().getPath(), ex.getMessage());
                            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                            return exchange.getResponse().setComplete();
                        })
                        .accessDeniedHandler((exchange, ex) -> {
                            log.warn("gateway_auth_reject reason=insufficient_scope path={} message={}",
                                    exchange.getRequest().getURI().getPath(), ex.getMessage());
                            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                            return exchange.getResponse().setComplete();
                        })
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtDecoder(jwtDecoder())))
                .build();
    }

    @Bean
    public NimbusReactiveJwtDecoder jwtDecoder() {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ofSeconds(clockSkewSeconds))
        );
        decoder.setJwtValidator(validator);
        return decoder;
    }

    private String classify401Reason(ServerWebExchange exchange, AuthenticationException ex) {
        String path = exchange.getRequest().getURI().getPath();
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");

        if ((path.startsWith("/api/v1/auth/")
            || path.startsWith("/api/auth/")
            || path.startsWith("/oauth2/authorization/")
            || path.startsWith("/login/oauth2/code/"))) {
            return "route_misclassified";
        }

        if (authHeader == null || authHeader.isBlank()) {
            return "missing_token";
        }

        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        if (message.contains("expired")) {
            return "expired_token";
        }
        if (message.contains("signature")) {
            return "invalid_signature";
        }
        if (message.contains("issuer") || message.contains("aud") || message.contains("claim")) {
            return "claim_mismatch";
        }
        return "invalid_token";
    }

}
