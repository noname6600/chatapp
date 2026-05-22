package com.chatweb.auth.service.impl;

import com.chatweb.auth.entity.RefreshToken;
import com.chatweb.auth.jwt.IKeyManager;
import com.chatweb.auth.jwt.KeyRecord;
import com.chatweb.auth.repository.RefreshTokenRepository;
import com.chatweb.auth.service.ITokenService;
import com.chatweb.common.core.exception.BusinessException;
import com.chatweb.common.core.exception.CommonErrorCode;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TokenService implements ITokenService {

    private final IKeyManager keyManager;
    private final Clock clock;

    @Value("${auth.jwt.access-token-expiration-ms}")
    private long accessExpMs;

    @Value("${auth.jwt.issuer}")
    private String issuer;

    @Override
    public String generateAccessToken(UUID accountId) {

        KeyRecord key = keyManager.getCurrentKey();
        Instant now = Instant.now(clock);

        return Jwts.builder()
                .setSubject(accountId.toString())
                .setIssuer(issuer)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusMillis(accessExpMs)))
                .setHeaderParam("kid", key.getKid())
                .signWith(key.getPrivateKey(), SignatureAlgorithm.RS256)
                .compact();
    }
}

