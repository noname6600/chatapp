package com.chatweb.auth.service.impl;

import com.chatweb.auth.dto.AuthResponse;
import com.chatweb.auth.service.ITokenServiceFacade;
import com.chatweb.auth.service.IUserProfileReadinessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthSessionService {

    private final IUserProfileReadinessService userProfileReadinessService;
    private final ITokenServiceFacade tokenFacade;

    public AuthResponse issueTokens(UUID accountId) {
        AuthResponse response = tokenFacade.issue(accountId);
        Optional<Boolean> profileReady = userProfileReadinessService.getProfileReadinessSnapshot(accountId);

        return new AuthResponse(
                response.getAccessToken(),
                response.getRefreshToken(),
                response.getAccessTokenExpiresIn(),
                profileReady.orElse(null)
        );
    }
}
