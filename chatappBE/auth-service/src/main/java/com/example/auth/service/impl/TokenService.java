package com.example.auth.service.impl;

import com.example.auth.entity.RefreshToken;
import com.example.auth.jwt.IKeyManager;
import com.example.auth.jwt.KeyRecord;
import com.example.auth.repository.RefreshTokenRepository;
import com.example.auth.service.ITokenService;
import com.example.common.web.exception.BusinessException;
import com.example.common.web.exception.ErrorCode;
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

    @Override
    public String generateAccessToken(UUID accountId) {

        KeyRecord key = keyManager.getCurrentKey();
        Instant now = Instant.now(clock);

        return Jwts.builder()
                .setSubject(accountId.toString())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusMillis(accessExpMs)))
                .setHeaderParam("kid", key.getKid())
                .signWith(key.getPrivateKey(), SignatureAlgorithm.RS256)
                .compact();
    }
}