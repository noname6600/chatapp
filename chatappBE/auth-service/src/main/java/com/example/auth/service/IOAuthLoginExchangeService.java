package com.example.auth.service;

import com.example.auth.enums.AuthProvider;

import java.util.UUID;

public interface IOAuthLoginExchangeService {
    String create(UUID accountId, AuthProvider provider);
    UUID consume(String code, AuthProvider provider);
    int cleanup();
}