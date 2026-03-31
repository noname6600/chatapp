package com.example.auth.service.impl;

import com.example.auth.entity.Account;
import com.example.auth.entity.IdentityProvider;
import com.example.auth.enums.AuthProvider;
import com.example.auth.repository.IdentityProviderRepository;
import com.example.auth.service.IIdentityProviderService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class IdentityProviderService implements IIdentityProviderService {

    private final IdentityProviderRepository idpRepo;

    @PersistenceContext
    private EntityManager entityManager;



    @Override
    @Transactional(readOnly = true)
    public Optional<IdentityProvider> findByProviderAndProviderUserId(
            AuthProvider provider,
            String providerUserId
    ) {
        return idpRepo.findByProviderAndProviderUserId(provider, providerUserId);
    }

    @Override
    public IdentityProvider linkIfAbsent(
            UUID accountId,
            AuthProvider provider,
            String providerUserId
    ) {

        try {
            Account accountRef =
                    entityManager.getReference(Account.class, accountId);

            return idpRepo.save(
                    IdentityProvider.builder()
                            .account(accountRef)
                            .provider(provider)
                            .providerUserId(providerUserId)
                            .build()
            );

        } catch (DataIntegrityViolationException ex) {
            IdentityProvider existing =
                    idpRepo.findByProviderAndProviderUserId(provider, providerUserId)
                            .orElseThrow(() ->
                                    new IllegalStateException(
                                            "IdentityProvider exists but cannot be loaded"
                                    )
                            );

            if (!existing.getAccount().getId().equals(accountId)) {
                throw new IllegalStateException(
                        "This provider account is already linked to another account"
                );
            }

            return existing;
        }
    }
}
