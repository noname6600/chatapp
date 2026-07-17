package com.chatweb.auth.service.impl;

import com.chatweb.auth.entity.Account;
import com.chatweb.auth.entity.IdentityProvider;
import com.chatweb.auth.enums.AuthProvider;
import com.chatweb.auth.repository.IdentityProviderRepository;
import com.chatweb.auth.service.IIdentityProviderService;
import com.chatweb.common.core.exception.BusinessException;
import com.chatweb.common.core.exception.CommonErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        // SELECT-first: check before insert so a duplicate never reaches the DB
        // commit phase where DataIntegrityViolationException would be uncatchable.
        Optional<IdentityProvider> existing =
                idpRepo.findByProviderAndProviderUserId(provider, providerUserId);

        if (existing.isPresent()) {
            IdentityProvider idp = existing.get();
            if (!idp.getAccount().getId().equals(accountId)) {
                throw new BusinessException(CommonErrorCode.CONFLICT, "Email already registered");
            }
            return idp;
        }

        Account accountRef = entityManager.getReference(Account.class, accountId);
        return idpRepo.save(
                IdentityProvider.builder()
                        .account(accountRef)
                        .provider(provider)
                        .providerUserId(providerUserId)
                        .build()
        );
    }
}
