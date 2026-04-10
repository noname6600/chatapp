package com.example.auth.service.impl;

import com.example.auth.entity.Account;
import com.example.auth.entity.PasswordResetToken;
import com.example.auth.repository.AccountRepository;
import com.example.auth.repository.PasswordResetTokenRepository;
import com.example.auth.service.IEmailService;
import com.example.auth.service.IForgotPasswordService;
import com.example.common.web.exception.BusinessException;
import com.example.common.web.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ForgotPasswordService implements IForgotPasswordService {

    private static final int TOKEN_LENGTH_BYTES = 32;
    private static final long RESET_TOKEN_EXPIRY_MS = 3600000; // 1 hour
    private static final Pattern STRONG_PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,72}$");

    private final AccountRepository accountRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final IEmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public void requestReset(String email) {
        // Always return silently — never leak account existence
        Optional<Account> accountOpt = accountRepository.findByEmail(email);
        if (accountOpt.isEmpty()) {
            log.info("Password reset requested for non-existent email: masked");
            return;
        }

        Account account = accountOpt.get();
        if (!account.isEnabled()) {
            log.info("Password reset requested for disabled account: masked");
            return;
        }

        // Invalidate any existing unused reset token for this account
        resetTokenRepository.findByAccountIdAndUsedFalse(account.getId())
                .ifPresent(resetTokenRepository::delete);

        // Generate new token
        byte[] tokenBytes = new byte[TOKEN_LENGTH_BYTES];
        secureRandom.nextBytes(tokenBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        String tokenHash = DigestUtils.sha256Hex(rawToken);

        Instant now = Instant.now(clock);
        PasswordResetToken token = PasswordResetToken.builder()
                .accountId(account.getId())
                .tokenHash(tokenHash)
                .expiresAt(now.plusMillis(RESET_TOKEN_EXPIRY_MS))
                .build();

        resetTokenRepository.save(token);
        log.info("Password reset token issued for account {}", account.getId());

        emailService.sendPasswordResetEmail(account.getId(), rawToken);
    }

    @Override
    @Transactional
    public void resetPassword(String token, String newPassword) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Reset token is required");
        }

        if (newPassword == null || newPassword.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "New password is required");
        }

        if (!STRONG_PASSWORD_PATTERN.matcher(newPassword).matches()) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Password must be 8-72 chars and include uppercase, lowercase, and number"
            );
        }

        String tokenHash = DigestUtils.sha256Hex(token);
        Instant now = Instant.now(clock);

        PasswordResetToken resetToken = resetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid or expired reset token"));

        if (resetToken.isUsed()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Reset token has already been used");
        }

        if (now.isAfter(resetToken.getExpiresAt())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Reset token has expired");
        }

        Account account = accountRepository.findById(resetToken.getAccountId())
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid or expired reset token"));

        // Mark token as consumed (replay prevention)
        resetTokenRepository.markAsUsed(resetToken.getId(), now);

        // Update account password
        account.setPasswordHash(passwordEncoder.encode(newPassword));
        accountRepository.save(account);

        log.info("Password reset successful for account {}", account.getId());
    }
}
