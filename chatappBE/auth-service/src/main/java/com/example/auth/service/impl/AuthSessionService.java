package com.example.auth.service.impl;

import com.example.auth.dto.AuthResponse;
import com.example.auth.service.ITokenServiceFacade;
import com.example.auth.service.IUserProfileReadinessService;
import com.example.common.core.exception.BusinessException;
import com.example.auth.exception.AuthErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthSessionService {

    public static final String INCOMPLETE_ACCOUNT_MESSAGE =
            "Account setup incomplete. Please try again in a few seconds.";

    private final IUserProfileReadinessService userProfileReadinessService;
    private final ITokenServiceFacade tokenFacade;

    public AuthResponse issueTokensWhenProfileReady(UUID accountId) {
        if (!userProfileReadinessService.waitUntilReady(accountId)) {
            log.warn("auth_issue_blocked reason=user_profile_not_ready accountId={}", accountId);
            throw new BusinessException(
                    AuthErrorCode.INCOMPLETE_ACCOUNT,
                    INCOMPLETE_ACCOUNT_MESSAGE,
                    Map.of("authCode", "incomplete_account")
            );
        }

        return tokenFacade.issue(accountId);
    }
}
