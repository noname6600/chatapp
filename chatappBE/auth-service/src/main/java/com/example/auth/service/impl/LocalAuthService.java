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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LocalAuthService implements ILocalAuthService {

    private final AccountRepository accountRepo;
    private final PasswordEncoder passwordEncoder;
    private final IIdentityProviderService idpService;
    private final AccountCreatedEventProducer accountCreatedEventProducer;

    @Override
    public UUID register(String email, String password) {

        if (accountRepo.existsByEmail(email)) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "Email already registered"
            );
        }

        try {
            Account account = accountRepo.save(
                    Account.builder()
                            .email(email)
                            .passwordHash(passwordEncoder.encode(password))
                            .enabled(true)
                            .createdAt(Instant.now())
                            .build()
            );

            idpService.linkIfAbsent(
                    account.getId(),
                    AuthProvider.LOCAL,
                    email
            );

            accountCreatedEventProducer.publish(account);

            return account.getId();

        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "Email already registered"
            );
        }
    }

    @Override
    public UUID login(String email, String password) {

        Account account = accountRepo.findByEmail(email)
                .orElseThrow(() ->
                        new BusinessException(
                                ErrorCode.UNAUTHORIZED,
                                "Invalid credentials"
                        )
                );

        if (account.getPasswordHash() == null ||
                !passwordEncoder.matches(password, account.getPasswordHash())) {

            throw new BusinessException(
                    ErrorCode.UNAUTHORIZED,
                    "Invalid credentials"
            );
        }

        if (!account.isEnabled()) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN,
                    "Account disabled"
            );
        }

        return account.getId();
    }
}
