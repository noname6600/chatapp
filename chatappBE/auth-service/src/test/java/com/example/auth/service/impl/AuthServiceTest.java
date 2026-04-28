package com.example.auth.service.impl;

import com.example.auth.dto.AuthResponse;
import com.example.auth.repository.AccountRepository;
import com.example.auth.service.IEmailService;
import com.example.auth.service.IGoogleTokenVerifier;
import com.example.auth.service.ILocalAuthService;
import com.example.auth.service.IOAuthService;
import com.example.auth.service.IPasswordService;
import com.example.auth.service.ITokenServiceFacade;
import com.example.auth.service.IUserProfileReadinessService;
import com.example.auth.service.IVerificationTokenService;
import com.example.common.web.exception.BusinessException;
import com.example.common.web.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    private IUserProfileReadinessService userProfileReadinessService;
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
        userProfileReadinessService = mock(IUserProfileReadinessService.class);
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
    void register_blocksTokenIssuance_whenUserProfileNotReady() {
        UUID accountId = UUID.randomUUID();
        when(localAuthService.register("new@example.com", "Password1!")).thenReturn(accountId);
        when(authSessionService.issueTokensWhenProfileReady(accountId))
            .thenThrow(new BusinessException(ErrorCode.INCOMPLETE_ACCOUNT));

        assertThatThrownBy(() -> authService.register("new@example.com", "Password1!"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INCOMPLETE_ACCOUNT);

        verify(localAuthService).register("new@example.com", "Password1!");
        verify(authSessionService).issueTokensWhenProfileReady(accountId);
        verifyNoInteractions(tokenServiceFacade, browserOAuthService);
    }

    @Test
    void login_blocksTokenIssuance_whenUserProfileNotReady() {
        UUID accountId = UUID.randomUUID();
        when(localAuthService.login("user@example.com", "Password1!")).thenReturn(accountId);
        when(authSessionService.issueTokensWhenProfileReady(accountId))
            .thenThrow(new BusinessException(ErrorCode.INCOMPLETE_ACCOUNT));

        assertThatThrownBy(() -> authService.login("user@example.com", "Password1!"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INCOMPLETE_ACCOUNT);

        verify(localAuthService).login("user@example.com", "Password1!");
        verify(authSessionService).issueTokensWhenProfileReady(accountId);
        verifyNoInteractions(tokenServiceFacade, browserOAuthService);
    }

    @Test
    void login_issuesTokens_whenUserProfileReady() {
        UUID accountId = UUID.randomUUID();
        AuthResponse expected = new AuthResponse("access-token", "refresh-token", 900L);

        when(localAuthService.login("user@example.com", "Password1!")).thenReturn(accountId);
        when(authSessionService.issueTokensWhenProfileReady(accountId)).thenReturn(expected);

        AuthResponse actual = authService.login("user@example.com", "Password1!");

        assertThat(actual).isEqualTo(expected);
        verify(authSessionService).issueTokensWhenProfileReady(accountId);
    }
}
