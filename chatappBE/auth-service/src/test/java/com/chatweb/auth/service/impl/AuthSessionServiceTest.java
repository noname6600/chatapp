package com.chatweb.auth.service.impl;

import com.chatweb.auth.dto.AuthResponse;
import com.chatweb.auth.service.ITokenServiceFacade;
import com.chatweb.auth.service.IUserProfileReadinessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthSessionServiceTest {

    private ITokenServiceFacade tokenFacade;
    private IUserProfileReadinessService userProfileReadinessService;
    private AuthSessionService authSessionService;

    @BeforeEach
    void setUp() {
        tokenFacade = mock(ITokenServiceFacade.class);
        userProfileReadinessService = mock(IUserProfileReadinessService.class);
        authSessionService = new AuthSessionService(userProfileReadinessService, tokenFacade);
    }

    @Test
    void issueTokens_returnsTokens_whenReadinessSnapshotUnavailable() {
        UUID accountId = UUID.randomUUID();
        AuthResponse issued = new AuthResponse("access-token", "refresh-token", 900L);

        when(tokenFacade.issue(accountId)).thenReturn(issued);
        when(userProfileReadinessService.getProfileReadinessSnapshot(accountId)).thenReturn(Optional.empty());

        AuthResponse actual = authSessionService.issueTokens(accountId);

        assertThat(actual.getAccessToken()).isEqualTo("access-token");
        assertThat(actual.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(actual.getAccessTokenExpiresIn()).isEqualTo(900L);
        assertThat(actual.getProfileReady()).isNull();
        verify(tokenFacade).issue(accountId);
        verify(userProfileReadinessService).getProfileReadinessSnapshot(accountId);
    }

    @Test
    void issueTokens_includesProfileReadinessHint_whenSnapshotAvailable() {
        UUID accountId = UUID.randomUUID();
        AuthResponse issued = new AuthResponse("access-token", "refresh-token", 900L);

        when(tokenFacade.issue(accountId)).thenReturn(issued);
        when(userProfileReadinessService.getProfileReadinessSnapshot(accountId)).thenReturn(Optional.of(Boolean.FALSE));

        AuthResponse actual = authSessionService.issueTokens(accountId);

        assertThat(actual.getProfileReady()).isFalse();
        verify(tokenFacade).issue(accountId);
        verify(userProfileReadinessService).getProfileReadinessSnapshot(accountId);
    }
}
