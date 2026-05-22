package com.chatweb.auth.client;

import com.chatweb.common.web.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.UUID;

@Component
@Slf4j
public class UserServiceClient {

    private final RestClient restClient;
    private final String internalAuthHeader;
    private final String internalAuthToken;

    public UserServiceClient(
            @Value("${user-service.base-url:http://localhost:8082/api/v1/users}") String userServiceBaseUrl,
            @Value("${internal.auth.header:X-Internal-Service-Token}") String internalAuthHeader,
            @Value("${internal.auth.token:}") String internalAuthToken
    ) {
        this.internalAuthHeader = internalAuthHeader;
        this.internalAuthToken = internalAuthToken;
        this.restClient = RestClient.builder()
                .baseUrl(userServiceBaseUrl)
                .build();
    }

    public Optional<Boolean> fetchProfileReady(UUID accountId) {
        try {
            ResponseEntity<ApiResponse<Boolean>> response = restClient.get()
                    .uri("/internal/{accountId}/exists", accountId)
                    .header(internalAuthHeader, internalAuthToken)
                    .retrieve()
                    .toEntity(new ParameterizedTypeReference<>() {});

            ApiResponse<Boolean> payload = response.getBody();
            if (payload == null || !payload.isSuccess()) {
                return Optional.empty();
            }

            return Optional.ofNullable(payload.getData());
        } catch (Exception ex) {
            log.warn("auth_user_service_probe_failed accountId={} reason={}", accountId, ex.getMessage());
            return Optional.empty();
        }
    }
}