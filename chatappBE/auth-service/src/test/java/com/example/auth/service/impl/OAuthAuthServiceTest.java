package com.example.auth.service.impl;

import com.example.auth.entity.Account;
import com.example.auth.entity.IdentityProvider;
import com.example.auth.enums.AuthProvider;
import com.example.auth.kafka.AccountCreatedEventProducer;
import com.example.auth.repository.AccountRepository;
import com.example.auth.service.IIdentityProviderService;
import com.example.common.core.exception.BusinessException;
import com.example.auth.exception.AuthErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuthAuthServiceTest {

    private AccountRepository accountRepository;
    private IIdentityProviderService identityProviderService;
    private AccountCreatedEventProducer accountCreatedEventProducer;
    private OAuthAuthService oauthAuthService;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        identityProviderService = mock(IIdentityProviderService.class);
        accountCreatedEventProducer = mock(AccountCreatedEventProducer.class);
        oauthAuthService = new OAuthAuthService(accountRepository, identityProviderService, accountCreatedEventProducer);
    }

    @Test
    void loginGoogle_returnsExistingLinkedAccount_whenProviderAlreadyLinked() {
        UUID accountId = UUID.randomUUID();
        Account account = Account.builder().id(accountId).email("user@example.com").enabled(true).emailVerified(true).createdAt(Instant.now()).build();
        IdentityProvider linked = IdentityProvider.builder()
                .account(account)
                .provider(AuthProvider.GOOGLE)
                .providerUserId("google-sub")
                .build();

        when(identityProviderService.findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google-sub"))
                .thenReturn(Optional.of(linked));

        UUID actual = oauthAuthService.loginGoogle("google-sub", "user@example.com");

        assertThat(actual).isEqualTo(accountId);
        verify(accountRepository, never()).findByEmail(any());
        verify(identityProviderService, never()).linkIfAbsent(any(), any(), any());
    }

    @Test
    void loginGoogle_reusesExistingEmailAccountAndMarksItVerified() {
        UUID accountId = UUID.randomUUID();
        Account existing = Account.builder()
                .id(accountId)
                .email("user@example.com")
                .enabled(true)
                .emailVerified(false)
                .createdAt(Instant.now())
                .build();
        IdentityProvider linked = IdentityProvider.builder()
                .account(existing)
                .provider(AuthProvider.GOOGLE)
                .providerUserId("google-sub")
                .build();

        when(identityProviderService.findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google-sub"))
                .thenReturn(Optional.empty());
        when(accountRepository.findByEmail("user@example.com")).thenReturn(Optional.of(existing));
        when(accountRepository.save(existing)).thenReturn(existing);
        when(identityProviderService.linkIfAbsent(accountId, AuthProvider.GOOGLE, "google-sub"))
                .thenReturn(linked);
        when(accountCreatedEventProducer.publish(existing)).thenReturn(true);

        UUID actual = oauthAuthService.loginGoogle("google-sub", "user@example.com");

        assertThat(actual).isEqualTo(accountId);
        assertThat(existing.isEmailVerified()).isTrue();
        verify(accountRepository).save(existing);
        verify(identityProviderService).linkIfAbsent(accountId, AuthProvider.GOOGLE, "google-sub");
        verify(accountCreatedEventProducer).publish(existing);
    }

    @Test
    void loginGoogle_createsNewAccountWhenEmailDoesNotExist() {
        UUID accountId = UUID.randomUUID();
        Account created = Account.builder()
                .id(accountId)
                .email("new@example.com")
                .enabled(true)
                .emailVerified(true)
                .createdAt(Instant.now())
                .build();
        IdentityProvider linked = IdentityProvider.builder()
                .account(created)
                .provider(AuthProvider.GOOGLE)
                .providerUserId("google-sub")
                .build();

        when(identityProviderService.findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google-sub"))
                .thenReturn(Optional.empty());
        when(accountRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(accountRepository.save(any(Account.class))).thenReturn(created);
        when(identityProviderService.linkIfAbsent(eq(accountId), eq(AuthProvider.GOOGLE), eq("google-sub")))
                .thenReturn(linked);
        when(accountCreatedEventProducer.publish(created)).thenReturn(true);

        UUID actual = oauthAuthService.loginGoogle("google-sub", "new@example.com");

        assertThat(actual).isEqualTo(accountId);
        verify(accountRepository).save(any(Account.class));
        verify(accountCreatedEventProducer).publish(created);
    }

    @Test
    void loginGoogle_throwsIncompleteAccount_whenNewAccountPublishFails() {
        UUID accountId = UUID.randomUUID();
        Account created = Account.builder()
                .id(accountId)
                .email("new@example.com")
                .enabled(true)
                .emailVerified(true)
                .createdAt(Instant.now())
                .build();
        IdentityProvider linked = IdentityProvider.builder()
                .account(created)
                .provider(AuthProvider.GOOGLE)
                .providerUserId("google-sub")
                .build();

        when(identityProviderService.findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google-sub"))
                .thenReturn(Optional.empty());
        when(accountRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(accountRepository.save(any(Account.class))).thenReturn(created);
        when(identityProviderService.linkIfAbsent(eq(accountId), eq(AuthProvider.GOOGLE), eq("google-sub")))
                .thenReturn(linked);
        when(accountCreatedEventProducer.publish(created)).thenReturn(false);

        assertThatThrownBy(() -> oauthAuthService.loginGoogle("google-sub", "new@example.com"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(AuthErrorCode.INCOMPLETE_ACCOUNT));
    }

    @Test
    void loginGoogle_throwsIncompleteAccount_whenExistingEmailPublishFails() {
        UUID accountId = UUID.randomUUID();
        Account existing = Account.builder()
                .id(accountId)
                .email("user@example.com")
                .enabled(true)
                .emailVerified(true)
                .createdAt(Instant.now())
                .build();
        IdentityProvider linked = IdentityProvider.builder()
                .account(existing)
                .provider(AuthProvider.GOOGLE)
                .providerUserId("google-sub")
                .build();

        when(identityProviderService.findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google-sub"))
                .thenReturn(Optional.empty());
        when(accountRepository.findByEmail("user@example.com")).thenReturn(Optional.of(existing));
        when(identityProviderService.linkIfAbsent(accountId, AuthProvider.GOOGLE, "google-sub"))
                .thenReturn(linked);
        when(accountCreatedEventProducer.publish(existing)).thenReturn(false);

        assertThatThrownBy(() -> oauthAuthService.loginGoogle("google-sub", "user@example.com"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(AuthErrorCode.INCOMPLETE_ACCOUNT));
    }

    @Test
    void loginGoogle_doesNotPublish_whenReturningGoogleUser() {
        UUID accountId = UUID.randomUUID();
        Account account = Account.builder().id(accountId).email("user@example.com").enabled(true).emailVerified(true).createdAt(Instant.now()).build();
        IdentityProvider linked = IdentityProvider.builder()
                .account(account)
                .provider(AuthProvider.GOOGLE)
                .providerUserId("google-sub")
                .build();

        when(identityProviderService.findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google-sub"))
                .thenReturn(Optional.of(linked));

        UUID actual = oauthAuthService.loginGoogle("google-sub", "user@example.com");

        assertThat(actual).isEqualTo(accountId);
        verify(accountCreatedEventProducer, never()).publish(any());
    }
}
