package com.example.auth.service;

import com.example.auth.dto.AuthResponse;

import java.util.UUID;

public interface IAuthService {
    AuthResponse register(String email, String password);
    AuthResponse login(String email, String password);
    AuthResponse loginGoogle(String idToken);
    AuthResponse refresh(String refreshToken);
    void changePassword(String oldPass, String newPass);
    void logout(String refreshToken);
    void logoutAll(UUID accountId);


}
