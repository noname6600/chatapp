package com.example.auth.service.impl;

import com.example.auth.entity.Account;
import com.example.auth.entity.IdentityProvider;
import com.example.auth.enums.AuthProvider;
import com.example.auth.repository.AccountRepository;
import com.example.auth.service.IIdentityProviderService;
import com.example.auth.service.IOAuthService;
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
public class OAuthAuthService implements IOAuthService {

    private final AccountRepository accountRepo;
    private final IIdentityProviderService idpService;

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
            account = accountRepo.findByEmail(email)
                    .orElseGet(() ->
                            accountRepo.save(
                                    Account.builder()
                                            .email(email)
                                            .enabled(true)
                                            .createdAt(Instant.now())
                                            .build()
                            )
                    );

        } catch (DataIntegrityViolationException ex) {
            account = accountRepo.findByEmail(email)
                    .orElseThrow(() ->
                            new IllegalStateException(
                                    "Account exists but cannot be loaded"
                            )
                    );
        }

        return idpService.linkIfAbsent(
                account.getId(),
                AuthProvider.GOOGLE,
                sub
        );
    }
}

