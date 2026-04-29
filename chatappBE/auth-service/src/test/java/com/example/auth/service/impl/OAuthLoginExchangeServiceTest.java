package com.example.auth.service.impl;

import com.example.auth.entity.OAuthLoginExchange;
import com.example.auth.enums.AuthProvider;
import com.example.auth.repository.OAuthLoginExchangeRepository;
import com.example.auth.exception.AuthErrorCode;
import com.example.common.core.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuthLoginExchangeServiceTest {

    private final Instant now = Instant.parse("2026-04-24T12:00:00Z");

    private OAuthLoginExchangeRepository repository;
    private OAuthLoginExchangeService service;

    @BeforeEach
    void setUp() {
        repository = mock(OAuthLoginExchangeRepository.class);
        service = new OAuthLoginExchangeService(repository, Clock.fixed(now, ZoneOffset.UTC));
        ReflectionTestUtils.setField(service, "exchangeExpirationMs", 120000L);
    }

    @Test
    void create_persistsHashedExchangeCode() {
        UUID accountId = UUID.randomUUID();
        when(repository.save(any(OAuthLoginExchange.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String rawCode = service.create(accountId, AuthProvider.GOOGLE);

        assertThat(rawCode).isNotBlank();
        verify(repository).save(any(OAuthLoginExchange.class));
    }

    @Test
    void consume_returnsAccountId_whenExchangeCodeAvailable() {
        UUID accountId = UUID.randomUUID();
        String rawCode = "raw.handoff.code";
        String codeHash = org.apache.commons.codec.digest.DigestUtils.sha256Hex(rawCode);
        OAuthLoginExchange exchange = OAuthLoginExchange.builder()
                .accountId(accountId)
                .provider(AuthProvider.GOOGLE)
                .codeHash(codeHash)
                .expiresAt(now.plusSeconds(60))
                .build();

        when(repository.consumeIfAvailable(eq(codeHash), eq(AuthProvider.GOOGLE), eq(now))).thenReturn(1);
        when(repository.findByCodeHashAndProvider(codeHash, AuthProvider.GOOGLE)).thenReturn(Optional.of(exchange));

        UUID actual = service.consume(rawCode, AuthProvider.GOOGLE);

        assertThat(actual).isEqualTo(accountId);
    }

    @Test
    void consume_rejectsInvalidOrReplayedExchangeCode() {
        String rawCode = "used.code";
        String codeHash = org.apache.commons.codec.digest.DigestUtils.sha256Hex(rawCode);

        when(repository.consumeIfAvailable(eq(codeHash), eq(AuthProvider.GOOGLE), eq(now))).thenReturn(0);

        assertThatThrownBy(() -> service.consume(rawCode, AuthProvider.GOOGLE))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.TOKEN_INVALID);
    }
}
