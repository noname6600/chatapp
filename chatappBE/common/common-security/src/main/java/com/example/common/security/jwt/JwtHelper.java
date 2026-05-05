package com.example.common.security.jwt;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;
import java.util.UUID;

public final class JwtHelper {

    private JwtHelper() {
    }

    public static Optional<UUID> extractUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(UUID.fromString(jwt.getSubject()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
