package com.example.auth.service.impl;

import com.example.auth.entity.OAuthLoginExchange;
import com.example.auth.enums.AuthProvider;
import com.example.auth.repository.OAuthLoginExchangeRepository;
import com.example.auth.service.IOAuthLoginExchangeService;
import com.example.auth.exception.AuthErrorCode;
import com.example.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class OAuthLoginExchangeService implements IOAuthLoginExchangeService {

    private final OAuthLoginExchangeRepository oauthLoginExchangeRepository;
    private final Clock clock;

    @Value("${auth.oauth.exchange-expiration-ms:120000}")
    private long exchangeExpirationMs;

    @Override
    public String create(UUID accountId, AuthProvider provider) {
        Instant now = Instant.now(clock);
        String rawCode = UUID.randomUUID() + "." + UUID.randomUUID();

        oauthLoginExchangeRepository.save(
                OAuthLoginExchange.builder()
                        .accountId(accountId)
                        .provider(provider)
                        .codeHash(DigestUtils.sha256Hex(rawCode))
                        .expiresAt(now.plusMillis(exchangeExpirationMs))
                        .build()
        );

        return rawCode;
    }

    @Override
    public UUID consume(String code, AuthProvider provider) {
        Instant now = Instant.now(clock);
        String codeHash = DigestUtils.sha256Hex(code);

        int updated = oauthLoginExchangeRepository.consumeIfAvailable(codeHash, provider, now);
        if (updated == 0) {
            throw new BusinessException(AuthErrorCode.TOKEN_INVALID, "OAuth login code is invalid or expired");
        }

        return oauthLoginExchangeRepository.findByCodeHashAndProvider(codeHash, provider)
                .map(OAuthLoginExchange::getAccountId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.TOKEN_INVALID, "OAuth login code is invalid or expired"));
    }

    @Override
    public int cleanup() {
        return oauthLoginExchangeRepository.deleteExpiredOrConsumed(Instant.now(clock));
    }
}
