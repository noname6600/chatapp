package com.example.auth.service;

import com.example.auth.dto.AuthResponse;

import java.util.UUID;

public interface ITokenServiceFacade {
    AuthResponse issue(UUID accountId);
    AuthResponse refresh(String refreshToken);
    void logout(String refreshToken);
    void logoutAll(UUID accountId);
    int cleanup();
}
