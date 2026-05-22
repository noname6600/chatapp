package com.chatweb.auth.service.impl;

import com.chatweb.auth.client.UserServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserProfileReadinessServiceTest {

    private UserServiceClient userServiceClient;
    private UserProfileReadinessService userProfileReadinessService;

    @BeforeEach
    void setUp() {
        userServiceClient = mock(UserServiceClient.class);
        userProfileReadinessService = new UserProfileReadinessService(userServiceClient);
    }

    @Test
    void getProfileReadinessSnapshot_returnsEmpty_whenClientCannotResolveStatus() {
        UUID accountId = UUID.randomUUID();
        when(userServiceClient.fetchProfileReady(accountId)).thenReturn(Optional.empty());

        Optional<Boolean> actual = userProfileReadinessService.getProfileReadinessSnapshot(accountId);

        assertThat(actual).isEmpty();
        verify(userServiceClient).fetchProfileReady(accountId);
    }

    @Test
    void getProfileReadinessSnapshot_returnsResolvedStatus_whenClientProvidesStatus() {
        UUID accountId = UUID.randomUUID();
        when(userServiceClient.fetchProfileReady(accountId)).thenReturn(Optional.of(Boolean.TRUE));

        Optional<Boolean> actual = userProfileReadinessService.getProfileReadinessSnapshot(accountId);

        assertThat(actual).contains(Boolean.TRUE);
        verify(userServiceClient).fetchProfileReady(accountId);
    }
}
