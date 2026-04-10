package com.example.auth.service.impl;

import com.example.auth.entity.Account;
import com.example.auth.kafka.AccountCreatedEventProducer;
import com.example.auth.repository.AccountRepository;
import com.example.auth.service.IIdentityProviderService;
import com.example.common.web.exception.BusinessException;
import com.example.common.web.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.*;

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
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
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

        when(accountRepo.existsByEmail(email)).thenReturn(false);
        when(passwordEncoder.encode("Password1!")).thenReturn("encoded");
        when(accountRepo.save(any(Account.class))).thenReturn(saved);
        when(accountCreatedEventProducer.publish(saved)).thenReturn(false);

        assertThatThrownBy(() -> localAuthService.register(email, "Password1!"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INCOMPLETE_ACCOUNT);

        verify(idpService).linkIfAbsent(eq(accountId), any(), eq(email));
    }
}
