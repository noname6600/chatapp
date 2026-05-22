package com.chatweb.auth.service;

import com.chatweb.auth.entity.IdentityProvider;
import com.chatweb.auth.enums.AuthProvider;

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
