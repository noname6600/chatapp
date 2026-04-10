package com.example.auth.service.impl;

import com.example.auth.dto.AuthResponse;
import com.example.auth.dto.EmailVerificationStatusResponse;
import com.example.auth.entity.Account;
import com.example.auth.repository.AccountRepository;
import com.example.auth.service.*;
import com.example.common.web.exception.BusinessException;
import com.example.common.web.exception.ErrorCode;
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
    private final IUserProfileReadinessService userProfileReadinessService;
    private final AccountRepository accountRepository;

    @Override
    public AuthResponse register(String email, String password) {
        UUID accountId = localAuthService.register(email, password);
        return issueTokensWhenProfileReady(accountId);
    }

    @Override
    public AuthResponse login(String email, String password) {
        UUID accountId = localAuthService.login(email, password);
        return issueTokensWhenProfileReady(accountId);
    }

    @Override
    public AuthResponse loginGoogle(String idToken) {

        GoogleIdToken.Payload payload = googleTokenVerifier.verify(idToken);

        UUID accountId = oauthAuthService.loginGoogle(
                payload.getSubject(),
                payload.getEmail()
        );

        return issueTokensWhenProfileReady(accountId);
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
                .orElseThrow(() -> new com.example.common.web.exception.BusinessException(
                        com.example.common.web.exception.ErrorCode.UNAUTHORIZED, "Account not found"
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

    private AuthResponse issueTokensWhenProfileReady(UUID accountId) {
        if (!userProfileReadinessService.waitUntilReady(accountId)) {
            log.warn("auth_issue_blocked reason=user_profile_not_ready accountId={}", accountId);
            throw new BusinessException(
                    ErrorCode.INCOMPLETE_ACCOUNT,
                    INCOMPLETE_ACCOUNT_MESSAGE,
                    Map.of("authCode", "incomplete_account")
            );
        }

        return tokenFacade.issue(accountId);
    }
}