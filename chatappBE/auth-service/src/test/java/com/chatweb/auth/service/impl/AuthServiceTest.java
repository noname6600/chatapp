package com.chatweb.auth.service.impl;

import com.chatweb.auth.dto.AuthResponse;
import com.chatweb.auth.repository.AccountRepository;
import com.chatweb.auth.service.IEmailService;
import com.chatweb.auth.service.IGoogleTokenVerifier;
import com.chatweb.auth.service.ILocalAuthService;
import com.chatweb.auth.service.IOAuthService;
import com.chatweb.auth.service.IPasswordService;
import com.chatweb.auth.service.ITokenServiceFacade;
import com.chatweb.auth.service.IVerificationTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private ILocalAuthService localAuthService;
    private IOAuthService oauthAuthService;
    private ITokenServiceFacade tokenServiceFacade;
    private IGoogleTokenVerifier googleTokenVerifier;
    private IPasswordService passwordService;
    private IVerificationTokenService verificationTokenService;
    private IEmailService emailService;
    private AccountRepository accountRepository;
    private AuthSessionService authSessionService;
    private BrowserOAuthService browserOAuthService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        localAuthService = mock(ILocalAuthService.class);
        oauthAuthService = mock(IOAuthService.class);
        tokenServiceFacade = mock(ITokenServiceFacade.class);
        googleTokenVerifier = mock(IGoogleTokenVerifier.class);
        passwordService = mock(IPasswordService.class);
        verificationTokenService = mock(IVerificationTokenService.class);
        emailService = mock(IEmailService.class);
        accountRepository = mock(AccountRepository.class);
        authSessionService = mock(AuthSessionService.class);
        browserOAuthService = mock(BrowserOAuthService.class);

        authService = new AuthService(
                localAuthService,
                oauthAuthService,
                tokenServiceFacade,
                googleTokenVerifier,
                passwordService,
                verificationTokenService,
                emailService,
            accountRepository,
            authSessionService,
            browserOAuthService
        );
    }

    @Test
    void register_issuesTokens_withoutBlockingOnProfileReadiness() {
        UUID accountId = UUID.randomUUID();
        AuthResponse expected = new AuthResponse("access-token", "refresh-token", 900L, null);

        when(localAuthService.register("new@example.com", "Password1!")).thenReturn(accountId);
        when(authSessionService.issueTokens(accountId)).thenReturn(expected);

        AuthResponse actual = authService.register("new@example.com", "Password1!");

        assertThat(actual).isEqualTo(expected);
        verify(localAuthService).register("new@example.com", "Password1!");
        verify(authSessionService).issueTokens(accountId);
        verifyNoInteractions(tokenServiceFacade, browserOAuthService);
    }

    @Test
    void login_issuesTokens_withoutBlockingOnProfileReadiness() {
        UUID accountId = UUID.randomUUID();
        AuthResponse expected = new AuthResponse("access-token", "refresh-token", 900L, null);

        when(localAuthService.login("user@example.com", "Password1!")).thenReturn(accountId);
        when(authSessionService.issueTokens(accountId)).thenReturn(expected);

        AuthResponse actual = authService.login("user@example.com", "Password1!");

        assertThat(actual).isEqualTo(expected);
        verify(localAuthService).login("user@example.com", "Password1!");
        verify(authSessionService).issueTokens(accountId);
        verifyNoInteractions(tokenServiceFacade, browserOAuthService);
    }

    @Test
    void login_issuesTokens_whenSessionServiceReturnsResponse() {
        UUID accountId = UUID.randomUUID();
        AuthResponse expected = new AuthResponse("access-token", "refresh-token", 900L, Boolean.TRUE);

        when(localAuthService.login("user@example.com", "Password1!")).thenReturn(accountId);
        when(authSessionService.issueTokens(accountId)).thenReturn(expected);

        AuthResponse actual = authService.login("user@example.com", "Password1!");

        assertThat(actual).isEqualTo(expected);
        verify(authSessionService).issueTokens(accountId);
    }
}

