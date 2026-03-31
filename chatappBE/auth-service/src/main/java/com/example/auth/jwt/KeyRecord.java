package com.example.auth.jwt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
public class KeyRecord {

    private final String kid;
    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final Instant createdAt;
    private final Instant expiredAt;
    private final boolean active;

    public boolean isExpired(Instant now) {
        return expiredAt != null && expiredAt.isBefore(now);
    }
}
