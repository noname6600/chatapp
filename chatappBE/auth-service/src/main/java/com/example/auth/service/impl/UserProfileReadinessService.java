package com.example.auth.service.impl;

import com.example.auth.service.IUserProfileReadinessService;
import com.example.common.web.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Service
@Slf4j
public class UserProfileReadinessService implements IUserProfileReadinessService {

    private static final int MAX_ATTEMPTS = 4;
    private static final long RETRY_DELAY_MS = 300;

    private final RestClient restClient;

    public UserProfileReadinessService(
            @Value("${user-service.base-url:http://localhost:8082/api/v1/users}") String userServiceBaseUrl
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(userServiceBaseUrl)
                .build();
    }

    @Override
    public boolean waitUntilReady(UUID accountId) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            if (isProfileReady(accountId)) {
                return true;
            }

            if (attempt < MAX_ATTEMPTS) {
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }

        return false;
    }

    private boolean isProfileReady(UUID accountId) {
        try {
            ResponseEntity<ApiResponse<Boolean>> response = restClient.get()
                    .uri("/internal/{accountId}/exists", accountId)
                    .retrieve()
                    .toEntity(new ParameterizedTypeReference<>() {});

            ApiResponse<Boolean> payload = response.getBody();
            return payload != null && payload.isSuccess() && Boolean.TRUE.equals(payload.getData());
        } catch (Exception ex) {
            log.warn("auth_profile_readiness_probe_failed accountId={} reason={}", accountId, ex.getMessage());
            return false;
        }
    }
}
