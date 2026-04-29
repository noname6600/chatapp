package com.example.auth.service.impl;

import com.example.auth.entity.Account;
import com.example.auth.kafka.AccountCreatedEventProducer;
import com.example.auth.repository.AccountRepository;
import com.example.auth.service.IIdentityProviderService;
import com.example.common.core.exception.BusinessException;
import com.example.common.core.exception.CommonErrorCode;
import com.example.auth.exception.AuthErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocalAuthServiceTest {

    private AccountRepository accountRepo;
    private PasswordEncoder passwordEncoder;
    private IIdentityProviderService idpService;
    private AccountCreatedEventProducer accountCreatedEventProducer;
    private LocalAuthService localAuthService;

    @BeforeEach
    void setUp() {
        accountRepo = mock(AccountRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        idpService = mock(IIdentityProviderService.class);
        accountCreatedEventProducer = mock(AccountCreatedEventProducer.class);

        localAuthService = new LocalAuthService(
                accountRepo,
                passwordEncoder,
                idpService,
                accountCreatedEventProducer
        );
    }

    @Test
    void login_returnsDeterministicInvalidCredentials_whenEmailDoesNotExist() {
        when(accountRepo.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        BusinessException exception = catchThrowableOfType(
                () -> localAuthService.login("missing@example.com", "Password1!"),
                BusinessException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.UNAUTHORIZED);
        assertThat(exception.getMessage()).isEqualTo("Invalid credentials");
        assertThat(exception.getDetails()).isEqualTo(java.util.Map.of("authCode", "invalid_credentials"));
    }

    @Test
    void register_throwsIncompleteAccount_whenPublishFails() {
        String email = "new-user@example.com";
        UUID accountId = UUID.randomUUID();

        Account saved = Account.builder()
                .id(accountId)
                .email(email)
                .passwordHash("encoded")
                .enabled(true)
                .emailVerified(false)
                .build();

        when(accountRepo.findByEmail(email)).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Password1!")).thenReturn("encoded");
        when(accountRepo.save(any(Account.class))).thenReturn(saved);
        when(accountCreatedEventProducer.publish(saved)).thenReturn(false);

        assertThatThrownBy(() -> localAuthService.register(email, "Password1!"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.INCOMPLETE_ACCOUNT);

        verify(idpService).linkIfAbsent(eq(accountId), any(), eq(email));
    }

    @Test
    void register_reusesExistingAccount_whenPasswordMatches() {
        String email = "user@example.com";
        UUID accountId = UUID.randomUUID();

        Account existing = Account.builder()
                .id(accountId)
                .email(email)
                .passwordHash("stored-hash")
                .enabled(true)
                .emailVerified(false)
                .build();

        when(accountRepo.findByEmail(email)).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("Password1!", "stored-hash")).thenReturn(true);
        when(accountCreatedEventProducer.publish(existing)).thenReturn(true);

        UUID actual = localAuthService.register(email, "Password1!");

        assertThat(actual).isEqualTo(accountId);
        verify(accountRepo, never()).save(any(Account.class));
        verify(idpService).linkIfAbsent(eq(accountId), any(), eq(email));
        verify(accountCreatedEventProducer).publish(existing);
    }

    @Test
    void register_recoversFromConcurrentInsert_andReturnsCanonicalAccount() {
        String email = "race@example.com";
        UUID accountId = UUID.randomUUID();

        Account existing = Account.builder()
                .id(accountId)
                .email(email)
                .passwordHash("stored-hash")
                .enabled(true)
                .emailVerified(false)
                .build();

        when(accountRepo.findByEmail(email))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(passwordEncoder.encode("Password1!")).thenReturn("encoded");
        when(accountRepo.save(any(Account.class))).thenThrow(new DataIntegrityViolationException("duplicate email"));
        when(passwordEncoder.matches("Password1!", "stored-hash")).thenReturn(true);
        when(accountCreatedEventProducer.publish(existing)).thenReturn(true);

        UUID actual = localAuthService.register(email, "Password1!");

        assertThat(actual).isEqualTo(accountId);
        verify(idpService).linkIfAbsent(eq(accountId), any(), eq(email));
    }

    @Test
    void register_throwsConflict_whenExistingAccountPasswordDoesNotMatch() {
        String email = "user@example.com";

        Account existing = Account.builder()
                .id(UUID.randomUUID())
                .email(email)
                .passwordHash("stored-hash")
                .enabled(true)
                .emailVerified(false)
                .build();

        when(accountRepo.findByEmail(email)).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("WrongPass1!", "stored-hash")).thenReturn(false);

        assertThatThrownBy(() -> localAuthService.register(email, "WrongPass1!"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.CONFLICT);

        verify(accountCreatedEventProducer, never()).publish(existing);
    }
}


