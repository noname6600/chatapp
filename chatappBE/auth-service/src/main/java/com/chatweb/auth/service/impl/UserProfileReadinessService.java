package com.chatweb.auth.service.impl;

import com.chatweb.auth.client.UserServiceClient;
import com.chatweb.auth.service.IUserProfileReadinessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserProfileReadinessService implements IUserProfileReadinessService {

    private final UserServiceClient userServiceClient;

    @Override
    public Optional<Boolean> getProfileReadinessSnapshot(UUID accountId) {
        Optional<Boolean> readiness = userServiceClient.fetchProfileReady(accountId);
        if (readiness.isEmpty()) {
            log.debug("auth_profile_readiness_snapshot_unavailable accountId={}", accountId);
        }
        return readiness;
    }
}
