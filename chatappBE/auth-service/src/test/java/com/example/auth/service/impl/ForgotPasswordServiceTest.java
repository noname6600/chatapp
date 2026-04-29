package com.example.auth.service.impl;

import com.example.auth.entity.Account;
import com.example.auth.entity.PasswordResetToken;
import com.example.auth.repository.AccountRepository;
import com.example.auth.repository.PasswordResetTokenRepository;
import com.example.auth.service.IEmailService;
import com.example.common.core.exception.BusinessException;
import com.example.common.core.exception.CommonErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ForgotPasswordServiceTest {

    private AccountRepository accountRepository;
    private PasswordResetTokenRepository resetTokenRepository;
    private IEmailService emailService;
    private PasswordEncoder passwordEncoder;
    private Clock clock;
    private ForgotPasswordService forgotPasswordService;

    private final Instant NOW = Instant.parse("2026-04-06T10:00:00Z");

    @BeforeEach
    void setUp() {
        accountRepository = Mockito.mock(AccountRepository.class);
        resetTokenRepository = Mockito.mock(PasswordResetTokenRepository.class);
        emailService = Mockito.mock(IEmailService.class);
        passwordEncoder = Mockito.mock(PasswordEncoder.class);
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        forgotPasswordService = new ForgotPasswordService(
                accountRepository, resetTokenRepository, emailService, passwordEncoder, clock
        );
    }

    @Test
    void requestReset_issuesTokenAndSendsEmail_whenAccountExists() {
        Account account = baseAccount(UUID.randomUUID(), "user@example.com");
        when(accountRepository.findByEmail("user@example.com")).thenReturn(Optional.of(account));
        when(resetTokenRepository.findByAccountIdAndUsedFalse(account.getId())).thenReturn(Optional.empty());
        when(resetTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        forgotPasswordService.requestReset("user@example.com");

        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(resetTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getExpiresAt()).isAfter(NOW);

        verify(emailService).sendPasswordResetEmail(eq(account.getId()), any());
    }

    @Test
    void requestReset_silentlyIgnores_whenAccountNotFound() {
        when(accountRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        // Should not throw
        forgotPasswordService.requestReset("unknown@example.com");

        verify(resetTokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(any(), any());
    }

    @Test
    void requestReset_deletesOldToken_whenUnusedTokenExists() {
        Account account = baseAccount(UUID.randomUUID(), "user@example.com");
        PasswordResetToken oldToken = existingToken(account.getId());

        when(accountRepository.findByEmail("user@example.com")).thenReturn(Optional.of(account));
        when(resetTokenRepository.findByAccountIdAndUsedFalse(account.getId())).thenReturn(Optional.of(oldToken));
        when(resetTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        forgotPasswordService.requestReset("user@example.com");

        verify(resetTokenRepository).delete(oldToken);
    }

    @Test
    void resetPassword_updatesPasswordAndConsumesToken_whenTokenValid() {
        UUID accountId = UUID.randomUUID();
        Account account = baseAccount(accountId, "user@example.com");
        PasswordResetToken token = PasswordResetToken.builder()
                .id(UUID.randomUUID())
                .accountId(accountId)
                .tokenHash("mocked-hash") // will match any hash for this mock
                .expiresAt(NOW.plusSeconds(3600))
                .used(false)
                .build();

        when(resetTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(resetTokenRepository.markAsUsed(any(), any())).thenReturn(1);
        when(passwordEncoder.encode("NewPass1")).thenReturn("new-hash");

        forgotPasswordService.resetPassword("any-raw-token", "NewPass1");

        assertThat(account.getPasswordHash()).isEqualTo("new-hash");
        verify(accountRepository).save(account);
        verify(resetTokenRepository).markAsUsed(token.getId(), NOW);
    }

    @Test
    void resetPassword_rejectsWeakPassword() {
        assertThatThrownBy(() ->
                forgotPasswordService.resetPassword("some-token", "weak")
        )
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.VALIDATION_ERROR);
    }

    @Test
    void resetPassword_rejectsExpiredToken() {
        PasswordResetToken expiredToken = PasswordResetToken.builder()
                .id(UUID.randomUUID())
                .accountId(UUID.randomUUID())
                .tokenHash("hash")
                .expiresAt(NOW.minusSeconds(1)) // expired
                .used(false)
                .build();

        when(resetTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() ->
                forgotPasswordService.resetPassword("some-token", "NewPass1")
        )
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.VALIDATION_ERROR);
    }

    @Test
    void resetPassword_rejectsAlreadyUsedToken() {
        PasswordResetToken usedToken = PasswordResetToken.builder()
                .id(UUID.randomUUID())
                .accountId(UUID.randomUUID())
                .tokenHash("hash")
                .expiresAt(NOW.plusSeconds(3600))
                .used(true) // already consumed
                .build();

        when(resetTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(usedToken));

        assertThatThrownBy(() ->
                forgotPasswordService.resetPassword("some-token", "NewPass1")
        )
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.VALIDATION_ERROR);
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

    private PasswordResetToken existingToken(UUID accountId) {
        return PasswordResetToken.builder()
                .id(UUID.randomUUID())
                .accountId(accountId)
                .tokenHash("old-hash")
                .expiresAt(NOW.plusSeconds(3600))
                .used(false)
                .build();
    }
}


