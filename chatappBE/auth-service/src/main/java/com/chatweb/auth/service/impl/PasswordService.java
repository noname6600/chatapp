package com.chatweb.auth.service.impl;

import com.chatweb.auth.entity.Account;
import com.chatweb.auth.repository.AccountRepository;
import com.chatweb.auth.service.IPasswordService;
import com.chatweb.common.core.exception.BusinessException;
import com.chatweb.common.core.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PasswordService implements IPasswordService {

    private static final Pattern STRONG_PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,72}$");

    private final PasswordEncoder passwordEncoder;
    private final AccountRepository accountRepository;

    @Override
    public void changePassword(UUID accountId, String oldPass, String newPass) {

        if (oldPass == null || oldPass.isBlank()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "Current password is required");
        }

        if (newPass == null || newPass.isBlank()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "New password is required");
        }

        if (!STRONG_PASSWORD_PATTERN.matcher(newPass).matches()) {
            throw new BusinessException(
                    CommonErrorCode.VALIDATION_ERROR,
                    "New password must be 8-72 chars and include uppercase, lowercase, and number"
            );
        }

        if (oldPass.equals(newPass)) {
            throw new BusinessException(
                    CommonErrorCode.VALIDATION_ERROR,
                    "New password must be different from current password"
            );
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Invalid credentials"));

        if (account.getPasswordHash() == null || account.getPasswordHash().isBlank()) {
            throw new BusinessException(
                    CommonErrorCode.FORBIDDEN,
                    "Password change is not available for this account"
            );
        }

        if (!passwordEncoder.matches(oldPass, account.getPasswordHash())) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "Current password is incorrect");
        }

        account.setPasswordHash(passwordEncoder.encode(newPass));
        accountRepository.save(account);

    }

    @Override
    public void setPassword(UUID accountId, String newPass) {
        if (newPass == null || newPass.isBlank()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "New password is required");
        }

        if (!STRONG_PASSWORD_PATTERN.matcher(newPass).matches()) {
            throw new BusinessException(
                    CommonErrorCode.VALIDATION_ERROR,
                    "Password must be 8-72 chars and include uppercase, lowercase, and number"
            );
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Invalid credentials"));

        if (account.getPasswordHash() != null && !account.getPasswordHash().isBlank()) {
            throw new BusinessException(
                    CommonErrorCode.CONFLICT,
                    "Account already has a password — use change password instead"
            );
        }

        account.setPasswordHash(passwordEncoder.encode(newPass));
        accountRepository.save(account);
    }
}


