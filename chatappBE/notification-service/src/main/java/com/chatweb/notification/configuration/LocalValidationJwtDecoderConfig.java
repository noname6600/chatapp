package com.chatweb.notification.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Local-only JWT decoder used by Phase B validation harness.
 *
 * When enabled, the token itself is treated as user UUID so local scripts can run
 * deterministic websocket and HTTP scenarios without external auth dependencies.
 */
@Configuration
@Profile("local")
@ConditionalOnProperty(name = "phaseb.local.validation.enabled", havingValue = "true")
public class LocalValidationJwtDecoderConfig {

    @Bean
    @Primary
    public JwtDecoder localValidationJwtDecoder() {
        return token -> {
            String subject = normalizeToken(token);
            Instant now = Instant.now();
            return Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject(subject)
                    .issuedAt(now)
                    .expiresAt(now.plus(8, ChronoUnit.HOURS))
                    .claim("sub", subject)
                    .build();
        };
    }

    private String normalizeToken(String token) {
        if (token == null || token.isBlank()) {
            return UUID.randomUUID().toString();
        }

        String normalized = token.startsWith("Bearer ") ? token.substring(7) : token;
        try {
            UUID.fromString(normalized);
            return normalized;
        } catch (Exception ignored) {
            return UUID.randomUUID().toString();
        }
    }
}
