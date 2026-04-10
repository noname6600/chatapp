package com.example.common.websocket.handshake;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtHandshakeInterceptor
        extends AbstractJwtHandshakeInterceptor {

    private final JwtDecoder jwtDecoder;

    @Override
    public UUID resolveUserId(String token) {
        try {
            log.info("[JWT-DECODE] 🔄 Decoding JWT token...");
            Jwt jwt = jwtDecoder.decode(token);
            UUID userId = UUID.fromString(jwt.getSubject());
            log.info("[JWT-DECODE] ✅ Token decoded successfully - userId={}", userId);
            return userId;
        } catch (Exception e) {
            log.error("[JWT-DECODE] ❌ Token decode failed - {} - {}", e.getClass().getSimpleName(), e.getMessage());
            throw e;
        }
    }
}


