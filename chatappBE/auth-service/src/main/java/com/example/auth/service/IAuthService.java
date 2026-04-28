package com.example.auth.service;

import com.example.auth.dto.AuthResponse;
import com.example.auth.dto.EmailVerificationStatusResponse;

import java.util.UUID;

public interface IAuthService {
    AuthResponse register(String email, String password);
    AuthResponse login(String email, String password);
    AuthResponse loginGoogle(String idToken);
    AuthResponse exchangeGoogleOAuthCode(String code);
    AuthResponse refresh(String refreshToken);
    void changePassword(UUID accountId, String oldPass, String newPass);
    void logout(String refreshToken);
    void logoutAll(UUID accountId);
    EmailVerificationStatusResponse getEmailVerificationStatus(UUID accountId);
    void sendVerificationEmail(UUID accountId);
    void confirmEmail(String token);
}
