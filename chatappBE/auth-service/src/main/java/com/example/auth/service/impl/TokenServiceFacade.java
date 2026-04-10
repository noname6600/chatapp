package com.example.auth.service.impl;

import com.example.auth.dto.AuthResponse;
import com.example.auth.entity.RefreshToken;
import com.example.auth.repository.RefreshTokenRepository;
import com.example.auth.service.ITokenService;
import com.example.auth.service.ITokenServiceFacade;
import com.example.common.web.exception.BusinessException;
import com.example.common.web.exception.ErrorCode;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class TokenServiceFacade implements ITokenServiceFacade {

    private final RefreshTokenRepository refreshRepo;
    private final ITokenService tokenService;
    private final Clock clock;


    @Value("${auth.jwt.access-token-expiration-ms}")
    private long accessExpMs;
    @Value("${auth.jwt.refresh-token-expiration-ms}")
    private long refreshExpMs;


    public AuthResponse issue(UUID accountId) {

        String accessToken = tokenService.generateAccessToken(accountId);
        String refreshToken = createRefreshToken(accountId);

        return buildAuthResponse(accountId, accessToken, refreshToken);
    }

    public AuthResponse refresh(String rawRefreshToken) {

        String hash = DigestUtils.sha256Hex(rawRefreshToken);
        Instant now = Instant.now(clock);

        RefreshToken token = refreshRepo
                .findByTokenHash(hash)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.TOKEN_INVALID)
                );

        if (token.isRevoked()) {

            refreshRepo.revokeAllByAccountId(token.getAccountId(), now);

            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }

        if (token.getExpiresAt().isBefore(now)) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        }

        int updated = refreshRepo.revokeIfNotRevoked(hash, now);

        if (updated == 0) {
            refreshRepo.revokeAllByAccountId(token.getAccountId(), now);
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }

        return issue(token.getAccountId());
    }

    public void logout(String rawRefreshToken) {

        String hash = DigestUtils.sha256Hex(rawRefreshToken);
        Instant now = Instant.now(clock);

        refreshRepo.findByTokenHashAndRevokedFalse(hash)
                .ifPresent(token -> {
                    token.setRevoked(true);
                    token.setRevokedAt(now);
                });
    }

    public void logoutAll(UUID accountId) {
        refreshRepo.revokeAllByAccountId(accountId, Instant.now(clock));
    }

    private String createRefreshToken(UUID accountId) {

        Instant now = Instant.now(clock);

        String rawToken = UUID.randomUUID() + "." + UUID.randomUUID();
        String hash = DigestUtils.sha256Hex(rawToken);

        RefreshToken entity = RefreshToken.builder()
                .accountId(accountId)
                .tokenHash(hash)
                .expiresAt(now.plusMillis(refreshExpMs))
                .revoked(false)
                .createdAt(now)
                .build();

        refreshRepo.save(entity);

        return rawToken;
    }

    public int cleanup() {
        return refreshRepo.deleteExpiredOrRevoked(Instant.now(clock));
    }

    private AuthResponse buildAuthResponse(UUID accountId, String accessToken, String refreshToken) {
        if (!StringUtils.hasText(accessToken) || !StringUtils.hasText(refreshToken)) {
            log.error("auth_contract_violation reason=missing_refresh_token_on_success accountId={} hasAccessToken={} hasRefreshToken={}",
                    accountId,
                    StringUtils.hasText(accessToken),
                    StringUtils.hasText(refreshToken));
            throw new IllegalStateException("Auth success payload missing required token fields");
        }

        return new AuthResponse(
                accessToken,
                refreshToken,
                accessExpMs / 1000
        );
    }
}
