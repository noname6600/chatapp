package com.example.auth.service;

import com.example.auth.entity.IdentityProvider;
import com.example.auth.enums.AuthProvider;

import java.util.Optional;
import java.util.UUID;

public interface IIdentityProviderService {
    Optional<IdentityProvider> findByProviderAndProviderUserId(
            AuthProvider provider,
            String providerUserId
    );

    IdentityProvider linkIfAbsent(
            UUID accountId,
            AuthProvider provider,
            String providerUserId
    );
}
