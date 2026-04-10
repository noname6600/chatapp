package com.example.auth.service.impl;

import com.example.auth.entity.Account;
import com.example.auth.repository.AccountRepository;
import com.example.auth.service.IPasswordService;
import com.example.common.web.exception.BusinessException;
import com.example.common.web.exception.ErrorCode;
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
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Current password is required");
        }

        if (newPass == null || newPass.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "New password is required");
        }

        if (!STRONG_PASSWORD_PATTERN.matcher(newPass).matches()) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "New password must be 8-72 chars and include uppercase, lowercase, and number"
            );
        }

        if (oldPass.equals(newPass)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "New password must be different from current password"
            );
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid credentials"));

        if (account.getPasswordHash() == null || account.getPasswordHash().isBlank()) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN,
                    "Password change is not available for this account"
            );
        }

        if (!passwordEncoder.matches(oldPass, account.getPasswordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Current password is incorrect");
        }

        account.setPasswordHash(passwordEncoder.encode(newPass));
        accountRepository.save(account);

    }
}
