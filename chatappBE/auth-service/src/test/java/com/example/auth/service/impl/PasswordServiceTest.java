package com.example.auth.service.impl;

import com.example.auth.entity.Account;
import com.example.auth.repository.AccountRepository;
import com.example.common.web.exception.BusinessException;
import com.example.common.web.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordServiceTest {

    private PasswordEncoder passwordEncoder;
    private AccountRepository accountRepository;
    private PasswordService passwordService;

    @BeforeEach
    void setUp() {
        passwordEncoder = Mockito.mock(PasswordEncoder.class);
        accountRepository = Mockito.mock(AccountRepository.class);
        passwordService = new PasswordService(passwordEncoder, accountRepository);
    }

    @Test
    void changePassword_updatesHash_whenCurrentPasswordMatches() {
        UUID accountId = UUID.randomUUID();
        Account account = baseAccount(accountId, "old-hash");

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("OldPass1", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("NewPass2")).thenReturn("new-hash");

        passwordService.changePassword(accountId, "OldPass1", "NewPass2");

        assertThat(account.getPasswordHash()).isEqualTo("new-hash");
        verify(accountRepository).save(account);
    }

    @Test
    void changePassword_rejectsWeakPassword() {
        assertThatThrownBy(() ->
                passwordService.changePassword(UUID.randomUUID(), "OldPass1", "weak")
        )
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void changePassword_rejectsIncorrectCurrentPassword() {
        UUID accountId = UUID.randomUUID();
        Account account = baseAccount(accountId, "old-hash");

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("WrongOld1", "old-hash")).thenReturn(false);

        assertThatThrownBy(() ->
                passwordService.changePassword(accountId, "WrongOld1", "NewPass2")
        )
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    private Account baseAccount(UUID id, String hash) {
        return Account.builder()
                .id(id)
                .email("user@example.com")
                .passwordHash(hash)
                .enabled(true)
                .createdAt(Instant.now())
                .build();
    }
}
