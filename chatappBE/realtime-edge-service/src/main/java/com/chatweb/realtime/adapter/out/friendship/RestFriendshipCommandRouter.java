package com.chatweb.realtime.adapter.out.friendship;

import com.chatweb.realtime.routing.command.FriendshipCommandRequest;
import com.chatweb.realtime.routing.command.IFriendshipCommandRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Objects;

/**
 * HTTP command router for friendship-service.
 */
@Slf4j
@Component
public class RestFriendshipCommandRouter implements IFriendshipCommandRouter {

    private final RestClient restClient;

    public RestFriendshipCommandRouter(
            RestClient.Builder restClientBuilder,
            @Value("${services.friendship.url:http://localhost:8085}") String friendshipServiceUrl,
            @Value("${services.friendship.http.connect-timeout-ms:1000}") int connectTimeoutMs,
            @Value("${services.friendship.http.read-timeout-ms:2000}") int readTimeoutMs
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);

        this.restClient = restClientBuilder
                .baseUrl(friendshipServiceUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public void route(String accessToken, FriendshipCommandRequest commandRequest) {
        Objects.requireNonNull(commandRequest, "commandRequest must not be null");

        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("Cannot forward friendship command without an access token");
        }

        String command = commandRequest.getCommand();
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("friendship command is required");
        }

        try {
            restClient.post()
                    .uri("/api/v1/friends/realtime/commands")
                    .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(commandRequest)
                    .retrieve()
                    .toBodilessEntity();

            log.debug("[FRIEND-EDGE] forwarded friendship command={} targetUserId={} requestId={}",
                    command, commandRequest.getTargetUserId(), commandRequest.getRequestId());
        } catch (Exception ex) {
            log.warn("[FRIEND-EDGE] failed to forward friendship command={} targetUserId={} requestId={}",
                    command, commandRequest.getTargetUserId(), commandRequest.getRequestId(), ex);
            throw new IllegalStateException("Failed to forward friendship command to friendship-service", ex);
        }
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }
}
