package com.example.auth.service.impl;

import com.example.auth.dto.AuthResponse;
import com.example.auth.service.*;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService implements IAuthService {

    private final ILocalAuthService localAuthService;
    private final IOAuthService oauthAuthService;
    private final ITokenServiceFacade tokenFacade;
    private final IGoogleTokenVerifier googleTokenVerifier;
    private final IPasswordService passwordService;

    @Override
    public AuthResponse register(String email, String password) {
        UUID accountId = localAuthService.register(email, password);
        return tokenFacade.issue(accountId);
    }

    @Override
    public AuthResponse login(String email, String password) {
        UUID accountId = localAuthService.login(email, password);
        return tokenFacade.issue(accountId);
    }

    @Override
    public AuthResponse loginGoogle(String idToken) {

        GoogleIdToken.Payload payload = googleTokenVerifier.verify(idToken);

        UUID accountId = oauthAuthService.loginGoogle(
                payload.getSubject(),
                payload.getEmail()
        );

        return tokenFacade.issue(accountId);
    }

    @Override
    public AuthResponse refresh(String refreshToken) {
        return tokenFacade.refresh(refreshToken);
    }

    @Override
    public void changePassword(String oldPass, String newPass) {

    }

    @Override
    public void logout(String refreshToken) {
        tokenFacade.logout(refreshToken);
    }

    @Override
    public void logoutAll(UUID accountId) {
        tokenFacade.logoutAll(accountId);
    }
}