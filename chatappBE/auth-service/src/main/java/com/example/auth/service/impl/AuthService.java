package com.example.auth.service.impl;

import com.example.auth.dto.AuthResponse;
import com.example.auth.dto.EmailVerificationStatusResponse;
import com.example.auth.entity.Account;
import com.example.auth.repository.AccountRepository;
import com.example.auth.service.*;
import com.example.common.core.exception.BusinessException;
import com.example.common.core.exception.CommonErrorCode;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AuthService implements IAuthService {

    private static final String INCOMPLETE_ACCOUNT_MESSAGE =
            "Account setup incomplete. Please try again in a few seconds.";

    private final ILocalAuthService localAuthService;
    private final IOAuthService oauthAuthService;
    private final ITokenServiceFacade tokenFacade;
    private final IGoogleTokenVerifier googleTokenVerifier;
    private final IPasswordService passwordService;
    private final IVerificationTokenService verificationTokenService;
    private final IEmailService emailService;
    private final AccountRepository accountRepository;
    private final AuthSessionService authSessionService;
    private final BrowserOAuthService browserOAuthService;

    @Override
    public AuthResponse register(String email, String password) {
        UUID accountId = localAuthService.register(email, password);
        return authSessionService.issueTokensWhenProfileReady(accountId);
    }

    @Override
    public AuthResponse login(String email, String password) {
        UUID accountId = localAuthService.login(email, password);
        return authSessionService.issueTokensWhenProfileReady(accountId);
    }

    @Override
    public AuthResponse loginGoogle(String idToken) {

        GoogleIdToken.Payload payload = googleTokenVerifier.verify(idToken);

        UUID accountId = oauthAuthService.loginGoogle(
                payload.getSubject(),
                payload.getEmail()
        );

        return authSessionService.issueTokensWhenProfileReady(accountId);
    }

    @Override
    public AuthResponse exchangeGoogleOAuthCode(String code) {
        return browserOAuthService.exchangeGoogleLogin(code);
    }

    @Override
    public AuthResponse refresh(String refreshToken) {
        return tokenFacade.refresh(refreshToken);
    }

    @Override
    public void changePassword(UUID accountId, String oldPass, String newPass) {
        passwordService.changePassword(accountId, oldPass, newPass);
    }

    @Override
    public void logout(String refreshToken) {
        tokenFacade.logout(refreshToken);
    }

    @Override
    public void logoutAll(UUID accountId) {
        tokenFacade.logoutAll(accountId);
    }

    @Override
    public EmailVerificationStatusResponse getEmailVerificationStatus(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new com.example.common.core.exception.BusinessException(
                        com.example.common.core.exception.CommonErrorCode.UNAUTHORIZED, "Account not found"
                ));
        boolean isVerified = verificationTokenService.isEmailVerified(accountId);
        return new EmailVerificationStatusResponse(account.getEmail(), isVerified);
    }

    @Override
    public void sendVerificationEmail(UUID accountId) {
        // Issue token for account (will use account's email if not provided)
        String verificationToken = verificationTokenService.issueToken(accountId, null);
        // Send email with token
        emailService.sendVerificationEmail(accountId, verificationToken);
    }

    @Override
    public void confirmEmail(String token) {
        verificationTokenService.confirmEmail(token);
    }

}

