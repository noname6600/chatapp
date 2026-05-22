package com.chatweb.auth.service.impl;

import com.chatweb.auth.entity.Account;
import com.chatweb.auth.enums.AuthProvider;
import com.chatweb.auth.repository.AccountRepository;
import com.chatweb.auth.service.event.AccountCreatedAfterCommitPublisher;
import com.chatweb.auth.service.IIdentityProviderService;
import com.chatweb.auth.service.ILocalAuthService;
import com.chatweb.common.core.exception.BusinessException;
import com.chatweb.common.core.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LocalAuthService implements ILocalAuthService {

        private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid credentials";

    private final AccountRepository accountRepo;
    private final PasswordEncoder passwordEncoder;
    private final IIdentityProviderService idpService;
    private final AccountCreatedAfterCommitPublisher accountCreatedAfterCommitPublisher;

    @Override
    @Transactional
    public UUID register(String email, String password) {
        Account existing = accountRepo.findByEmail(email).orElse(null);
        if (existing != null) {
            return reuseExistingRegistration(existing, password);
        }

        String passwordHash = passwordEncoder.encode(password);
        try {
            Account account = accountRepo.save(
                    Account.builder()
                            .email(email)
                            .passwordHash(passwordHash)
                            .enabled(true)
                            .emailVerified(false)
                            .createdAt(Instant.now())
                            .build()
            );

            idpService.linkIfAbsent(
                    account.getId(),
                    AuthProvider.LOCAL,
                    email
            );

                accountCreatedAfterCommitPublisher.publishAfterCommit(account);

            return account.getId();

        } catch (DataIntegrityViolationException ex) {
            Account concurrent = accountRepo.findByEmail(email).orElse(null);
            if (concurrent != null) {
                return reuseExistingRegistration(concurrent, password);
            }
            throw new BusinessException(
                    CommonErrorCode.CONFLICT,
                    "Email already registered"
            );
        }
    }

    @Override
    public UUID login(String email, String password) {

        Account account = accountRepo.findByEmail(email)
                .orElseThrow(this::invalidCredentialsException);

        if (account.getPasswordHash() == null ||
                !passwordEncoder.matches(password, account.getPasswordHash())) {
                        throw invalidCredentialsException();
        }

        if (!account.isEnabled()) {
            throw new BusinessException(
                    CommonErrorCode.FORBIDDEN,
                    "Account disabled"
            );
        }

        return account.getId();
    }

    private BusinessException invalidCredentialsException() {
        log.info("auth_failure reason=invalid_credentials");
        return new BusinessException(
                CommonErrorCode.UNAUTHORIZED,
                INVALID_CREDENTIALS_MESSAGE,
                Map.of("authCode", "invalid_credentials")
        );
    }

    private UUID reuseExistingRegistration(Account account, String password) {
        if (account.getPasswordHash() == null || !passwordEncoder.matches(password, account.getPasswordHash())) {
            throw new BusinessException(
                    CommonErrorCode.CONFLICT,
                    "Email already registered"
            );
        }

        idpService.linkIfAbsent(
                account.getId(),
                AuthProvider.LOCAL,
                account.getEmail()
        );

        accountCreatedAfterCommitPublisher.publishAfterCommit(account);

        return account.getId();
    }
}


