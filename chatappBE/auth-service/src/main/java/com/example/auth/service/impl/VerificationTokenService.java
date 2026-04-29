package com.example.auth.service.impl;

import com.example.auth.entity.Account;
import com.example.auth.entity.VerificationToken;
import com.example.auth.repository.AccountRepository;
import com.example.auth.repository.VerificationTokenRepository;
import com.example.auth.service.IVerificationTokenService;
import com.example.common.core.exception.BusinessException;
import com.example.common.core.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationTokenService implements IVerificationTokenService {

    private static final int TOKEN_LENGTH_BYTES = 32;
    private static final long VERIFICATION_TOKEN_EXPIRY_MS = 86400000; // 24 hours

    private final VerificationTokenRepository verificationTokenRepository;
    private final AccountRepository accountRepository;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public String issueToken(UUID accountId, String email) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Account not found"));

        // If email not provided, use account's email
        String tokenEmail = email != null && !email.isBlank() ? email : account.getEmail();

        if (tokenEmail == null || tokenEmail.isBlank()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "Email is required");
        }

        // Invalidate previous unused token if any
        verificationTokenRepository.findByAccountIdAndUsedFalse(accountId)
                .ifPresent(verificationTokenRepository::delete);

        // Generate new token
        byte[] tokenBytes = new byte[TOKEN_LENGTH_BYTES];
        secureRandom.nextBytes(tokenBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        String tokenHash = DigestUtils.sha256Hex(rawToken);

        // Save token to database
        Instant now = Instant.now(clock);
        VerificationToken token = VerificationToken.builder()
                .accountId(accountId)
                .email(tokenEmail)
                .tokenHash(tokenHash)
                .expiresAt(now.plusMillis(VERIFICATION_TOKEN_EXPIRY_MS))
                .build();

        verificationTokenRepository.save(token);
        log.info("Issued verification token for account {}", accountId);

        return rawToken;
    }

    @Override
    @Transactional
    public void confirmEmail(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "Verification token is required");
        }

        String tokenHash = DigestUtils.sha256Hex(token);
        Instant now = Instant.now(clock);

        VerificationToken verificationToken = verificationTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.VALIDATION_ERROR, "Invalid verification token"));

        // Check if token is already used
        if (verificationToken.isUsed()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "Verification token has already been used");
        }

        // Check if token has expired
        if (now.isAfter(verificationToken.getExpiresAt())) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "Verification token has expired");
        }

        // Mark token as used
        verificationTokenRepository.markAsUsed(verificationToken.getId(), now);

        // Mark account's email as verified
        Account account = accountRepository.findById(verificationToken.getAccountId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Account not found"));
        account.setEmailVerified(true);
        accountRepository.save(account);

        log.info("Email verified for account {}", verificationToken.getAccountId());
    }

    @Override
    public boolean isEmailVerified(UUID accountId) {
        return accountRepository.findById(accountId)
                .map(Account::isEmailVerified)
                .orElse(false);
    }

    @Override
    public String getVerifiedEmail(UUID accountId) {
        return accountRepository.findById(accountId)
                .filter(Account::isEmailVerified)
                .map(Account::getEmail)
                .orElse(null);
    }
}


