package com.example.auth.jwt.impl;

import com.example.auth.jwt.IJwtVerifierService;
import com.example.auth.jwt.IKeyManager;
import com.example.auth.jwt.KeyRecord;
import io.jsonwebtoken.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import com.example.common.web.exception.BusinessException;
import com.example.common.web.exception.ErrorCode;


import java.security.Key;

@Service
@RequiredArgsConstructor
public class JwtVerifierService implements IJwtVerifierService {

    private final IKeyManager keyManager;
    private final Clock clock;

    @Override
    public UUID verify(String token) {

        try {

            Claims claims = Jwts.parserBuilder()
                    .setClock(() -> Date.from(Instant.now(clock)))
                    .setSigningKeyResolver(new SigningKeyResolverAdapter() {

                        @Override
                        public Key resolveSigningKey(JwsHeader header, Claims claims) {

                            String kid = header.getKeyId();
                            if (kid == null) {
                                throw new BusinessException(
                                        ErrorCode.TOKEN_INVALID,
                                        "Missing key id"
                                );
                            }

                            KeyRecord key = keyManager.getByKid(kid);

                            if (key == null || key.isExpired(Instant.now(clock))) {
                                throw new BusinessException(
                                        ErrorCode.TOKEN_INVALID,
                                        "Invalid signing key"
                                );
                            }

                            return key.getPublicKey();
                        }
                    })
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            return UUID.fromString(claims.getSubject());

        } catch (ExpiredJwtException ex) {
            throw new BusinessException(
                    ErrorCode.TOKEN_EXPIRED,
                    "Access token expired"
            );

        } catch (JwtException | IllegalArgumentException ex) {
            throw new BusinessException(
                    ErrorCode.TOKEN_INVALID,
                    "Invalid access token"
            );
        }
    }
}