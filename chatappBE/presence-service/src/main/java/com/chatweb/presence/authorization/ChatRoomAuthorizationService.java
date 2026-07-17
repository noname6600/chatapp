package com.chatweb.presence.authorization;

import com.chatweb.common.core.exception.BusinessException;
import com.chatweb.common.core.exception.CommonErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
@Slf4j
public class ChatRoomAuthorizationService implements RoomAuthorizationService {

    private final RestClient restClient;

    public ChatRoomAuthorizationService(
            RestClient.Builder restClientBuilder,
            @Value("${services.chat.url:http://localhost:8083}") String chatServiceUrl,
            @Value("${services.chat.http.connect-timeout-ms:1000}") int connectTimeoutMs,
            @Value("${services.chat.http.read-timeout-ms:2000}") int readTimeoutMs
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);

        this.restClient = restClientBuilder
                .baseUrl(chatServiceUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public void ensureRoomMember(UUID roomId, String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "Missing access token");
        }

        try {
            restClient.get()
                    .uri("/api/v1/rooms/{roomId}/member-count", roomId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.warn("[PRESENCE-AUTH] room membership authorization failed roomId={}", roomId, ex);
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "Not authorized for room");
        }
    }
}