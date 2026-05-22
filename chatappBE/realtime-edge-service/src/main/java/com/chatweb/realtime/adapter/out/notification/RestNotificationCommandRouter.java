package com.chatweb.realtime.adapter.out.notification;

import com.chatweb.realtime.routing.command.INotificationCommandRouter;
import com.chatweb.realtime.routing.command.NotificationCommandRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.util.Objects;

/**
 * HTTP command router for notification-service.
 *
 * This is the first real migration slice for Phase B:
 * - edge receives notification commands
 * - edge forwards them to notification-service over HTTP
 * - notification-service keeps owning the business logic
 *
 * Temporary limitation:
 * - Uses bearer token forwarded from websocket handshake context
 * - If the token is missing, the command is rejected instead of being faked
 */
@Slf4j
@Component
public class RestNotificationCommandRouter implements INotificationCommandRouter {

    private final RestClient restClient;

    public RestNotificationCommandRouter(
            RestClient.Builder restClientBuilder,
            @Value("${services.notification.url}") String notificationServiceUrl
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(2_000);
        requestFactory.setReadTimeout(3_000);

        this.restClient = restClientBuilder
                .baseUrl(notificationServiceUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public void route(String accessToken, NotificationCommandRequest commandRequest) {
        Objects.requireNonNull(commandRequest, "commandRequest must not be null");

        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("Cannot forward notification command without an access token");
        }

        String command = commandRequest.getCommand();
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("notification command is required");
        }

        try {
            restClient.post()
                    .uri("/api/v1/notifications/realtime/commands")
                    .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(commandRequest)
                    .retrieve()
                    .toBodilessEntity();

            log.debug("[NOTI-EDGE] forwarded notification command={} requestId={}",
                    command, commandRequest.getRequestId());
        } catch (Exception ex) {
            log.warn("[NOTI-EDGE] failed to forward notification command={} requestId={}",
                    command, commandRequest.getRequestId(), ex);
            throw new IllegalStateException("Failed to forward notification command to notification-service", ex);
        }
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }
}
