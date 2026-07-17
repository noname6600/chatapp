package com.chatweb.auth.service.impl;

import com.chatweb.auth.entity.Account;
import com.chatweb.auth.entity.IdentityProvider;
import com.chatweb.auth.enums.AuthProvider;
import com.chatweb.auth.repository.AccountRepository;
import com.chatweb.auth.service.event.AccountCreatedAfterCommitPublisher;
import com.chatweb.auth.service.IIdentityProviderService;
import com.chatweb.auth.service.IOAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class OAuthAuthService implements IOAuthService {

    private final AccountRepository accountRepo;
    private final IIdentityProviderService idpService;
    private final AccountCreatedAfterCommitPublisher accountCreatedAfterCommitPublisher;

    @Override
    public UUID loginGoogle(String googleSub, String email) {
        Optional<IdentityProvider> existingIdp =
                idpService.findByProviderAndProviderUserId(
                        AuthProvider.GOOGLE,
                        googleSub
                );

        if (existingIdp.isPresent()) {
            return existingIdp.get()
                    .getAccount()
                    .getId();
        }

        IdentityProvider linked = linkGoogleAccount(googleSub, email);

        return linked.getAccount().getId();
    }


    private IdentityProvider linkGoogleAccount(String sub, String email) {

        Account account;

        try {
            Optional<Account> existing = accountRepo.findByEmail(email);
            if (existing.isPresent()) {
                account = existing.get();
            } else {
                account = accountRepo.save(
                        Account.builder()
                                .email(email)
                                .enabled(true)
                                .emailVerified(true)
                                .createdAt(Instant.now())
                                .build()
                );
            }

        } catch (DataIntegrityViolationException ex) {
            account = accountRepo.findByEmail(email)
                    .orElseThrow(() ->
                            new IllegalStateException(
                                    "Account exists but cannot be loaded"
                            )
                    );
        }

        // Ensure the account is marked email-verified (handles existing local accounts that gain Google link)
        if (!account.isEmailVerified()) {
            account.setEmailVerified(true);
            accountRepo.save(account);
        }

        IdentityProvider linked = idpService.linkIfAbsent(
                account.getId(),
                AuthProvider.GOOGLE,
                sub
        );

        accountCreatedAfterCommitPublisher.publishAfterCommit(account);

        return linked;
    }
}


