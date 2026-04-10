package com.example.auth.service.impl;

import com.example.auth.entity.Account;
import com.example.auth.entity.VerificationToken;
import com.example.auth.repository.AccountRepository;
import com.example.auth.repository.VerificationTokenRepository;
import com.example.common.web.exception.BusinessException;
import com.example.common.web.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VerificationTokenServiceTest {

    private VerificationTokenRepository verificationTokenRepository;
    private AccountRepository accountRepository;
    private Clock clock;
    private VerificationTokenService verificationTokenService;

    private final Instant NOW = Instant.parse("2026-04-05T12:00:00Z");

    @BeforeEach
    void setUp() {
        verificationTokenRepository = Mockito.mock(VerificationTokenRepository.class);
        accountRepository = Mockito.mock(AccountRepository.class);
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        verificationTokenService = new VerificationTokenService(verificationTokenRepository, accountRepository, clock);
    }

    @Test
    void issueToken_createsNewToken_whenNoExistingToken() {
        UUID accountId = UUID.randomUUID();
        Account account = baseAccount(accountId, "user@example.com");

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(verificationTokenRepository.findByAccountIdAndUsedFalse(accountId)).thenReturn(Optional.empty());
        when(verificationTokenRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        String rawToken = verificationTokenService.issueToken(accountId, null);

        assertThat(rawToken).isNotNull().isNotBlank();

        ArgumentCaptor<VerificationToken> captor = ArgumentCaptor.forClass(VerificationToken.class);
        verify(verificationTokenRepository).save(captor.capture());

        VerificationToken saved = captor.getValue();
        assertThat(saved.getAccountId()).isEqualTo(accountId);
        assertThat(saved.getEmail()).isEqualTo("user@example.com");
        assertThat(saved.getExpiresAt()).isAfter(NOW);
    }

    @Test
    void issueToken_deletesOldToken_whenUnusedTokenExists() {
        UUID accountId = UUID.randomUUID();
        Account account = baseAccount(accountId, "user@example.com");
        VerificationToken existingToken = existingToken(accountId);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(verificationTokenRepository.findByAccountIdAndUsedFalse(accountId))
                .thenReturn(Optional.of(existingToken));
        when(verificationTokenRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        verificationTokenService.issueToken(accountId, null);

        verify(verificationTokenRepository).delete(existingToken);
    }

    @Test
    void confirmEmail_marksTokenUsedAndVerifiesAccount_whenTokenValid() {
        UUID accountId = UUID.randomUUID();
        Account account = baseAccount(accountId, "user@example.com");
        VerificationToken token = VerificationToken.builder()
                .id(UUID.randomUUID())
                .accountId(accountId)
                .email("user@example.com")
                .tokenHash("some-hash")
                .expiresAt(NOW.plusSeconds(3600))
                .used(false)
                .build();

        // We need to capture what the service looks for by hash
        // VerificationTokenService computes sha256 of rawToken, so we mock findByTokenHash
        when(verificationTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(verificationTokenRepository.markAsUsed(any(), any())).thenReturn(1);

        // Use a non-empty string (service will hash it)
        verificationTokenService.confirmEmail("any-raw-token");

        verify(verificationTokenRepository).markAsUsed(token.getId(), NOW);
        assertThat(account.isEmailVerified()).isTrue();
        verify(accountRepository).save(account);
    }

    @Test
    void confirmEmail_rejectsExpiredToken() {
        VerificationToken expiredToken = VerificationToken.builder()
                .id(UUID.randomUUID())
                .accountId(UUID.randomUUID())
                .email("user@example.com")
                .tokenHash("hash")
                .expiresAt(NOW.minusSeconds(1)) // expired
                .used(false)
                .build();

        when(verificationTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> verificationTokenService.confirmEmail("some-raw-token"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void confirmEmail_rejectsAlreadyUsedToken() {
        VerificationToken usedToken = VerificationToken.builder()
                .id(UUID.randomUUID())
                .accountId(UUID.randomUUID())
                .email("user@example.com")
                .tokenHash("hash")
                .expiresAt(NOW.plusSeconds(3600))
                .used(true)
                .build();

        when(verificationTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(usedToken));

        assertThatThrownBy(() -> verificationTokenService.confirmEmail("some-raw-token"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void isEmailVerified_returnsTrueWhenAccountEmailIsVerified() {
        UUID accountId = UUID.randomUUID();
        Account verifiedAccount = baseAccount(accountId, "user@example.com");
        verifiedAccount.setEmailVerified(true);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(verifiedAccount));

        boolean result = verificationTokenService.isEmailVerified(accountId);

        assertThat(result).isTrue();
    }

    @Test
    void isEmailVerified_returnsFalseWhenEmailNotYetVerified() {
        UUID accountId = UUID.randomUUID();
        Account unverifiedAccount = baseAccount(accountId, "user@example.com");
        // emailVerified defaults to false

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(unverifiedAccount));

        boolean result = verificationTokenService.isEmailVerified(accountId);

        assertThat(result).isFalse();
    }

    private Account baseAccount(UUID id, String email) {
        return Account.builder()
                .id(id)
                .email(email)
                .enabled(true)
                .emailVerified(false)
                .createdAt(NOW)
                .build();
    }

    private VerificationToken existingToken(UUID accountId) {
        return VerificationToken.builder()
                .id(UUID.randomUUID())
                .accountId(accountId)
                .email("user@example.com")
                .tokenHash("old-hash")
                .expiresAt(NOW.plusSeconds(3600))
                .used(false)
                .build();
    }
}
