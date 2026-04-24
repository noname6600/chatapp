package com.example.auth.service.impl;

import com.example.auth.entity.Account;
import com.example.auth.enums.AuthProvider;
import com.example.auth.kafka.AccountCreatedEventProducer;
import com.example.auth.repository.AccountRepository;
import com.example.auth.service.IIdentityProviderService;
import com.example.auth.service.ILocalAuthService;
import com.example.common.web.exception.BusinessException;
import com.example.common.web.exception.ErrorCode;
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
        private static final String INCOMPLETE_ACCOUNT_MESSAGE =
                        "Account setup incomplete. Please try again in a few seconds.";

    private final AccountRepository accountRepo;
    private final PasswordEncoder passwordEncoder;
    private final IIdentityProviderService idpService;
    private final AccountCreatedEventProducer accountCreatedEventProducer;

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

            boolean published = accountCreatedEventProducer.publish(account);
            if (!published) {
                log.warn(
                        "auth_registration_incomplete reason=account_created_event_not_published accountId={} email={}",
                        account.getId(),
                        email
                );
                throw incompleteAccountException();
            }

            return account.getId();

        } catch (DataIntegrityViolationException ex) {
            Account concurrent = accountRepo.findByEmail(email).orElse(null);
            if (concurrent != null) {
                return reuseExistingRegistration(concurrent, password);
            }
            throw new BusinessException(
                    ErrorCode.CONFLICT,
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
                    ErrorCode.FORBIDDEN,
                    "Account disabled"
            );
        }

        return account.getId();
    }

    private BusinessException invalidCredentialsException() {
        log.info("auth_failure reason=invalid_credentials");
        return new BusinessException(
                ErrorCode.UNAUTHORIZED,
                INVALID_CREDENTIALS_MESSAGE,
                Map.of("authCode", "invalid_credentials")
        );
    }

    private UUID reuseExistingRegistration(Account account, String password) {
        if (account.getPasswordHash() == null || !passwordEncoder.matches(password, account.getPasswordHash())) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "Email already registered"
            );
        }

        idpService.linkIfAbsent(
                account.getId(),
                AuthProvider.LOCAL,
                account.getEmail()
        );

        boolean published = accountCreatedEventProducer.publish(account);
        if (!published) {
            log.warn(
                    "auth_registration_incomplete reason=account_created_event_not_published accountId={} email={}",
                    account.getId(),
                    account.getEmail()
            );
            throw incompleteAccountException();
        }

        return account.getId();
    }

    private BusinessException incompleteAccountException() {
        return new BusinessException(
                ErrorCode.INCOMPLETE_ACCOUNT,
                INCOMPLETE_ACCOUNT_MESSAGE,
                Map.of("authCode", "incomplete_account")
        );
    }
}
