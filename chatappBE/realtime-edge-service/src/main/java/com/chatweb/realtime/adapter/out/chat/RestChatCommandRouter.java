package com.chatweb.realtime.adapter.out.chat;

import com.chatweb.realtime.routing.command.IChatCommandRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * HTTP command router for chat-service.
 *
 * The edge keeps websocket ownership and room subscription tracking, while
 * chat-service keeps the message and room business logic.
 */
@Slf4j
@Component
public class RestChatCommandRouter implements IChatCommandRouter {

    private final RestClient restClient;

    public RestChatCommandRouter(
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
    public void sendMessage(String accessToken, Map<String, Object> payload) {
        postJson(accessToken, "/api/v1/messages", payload, "send", null, null);
    }

    @Override
    public void editMessage(String accessToken, UUID messageId, Map<String, Object> payload) {
        Objects.requireNonNull(messageId, "messageId must not be null");
        putJson(accessToken, "/api/v1/messages/" + messageId, payload, "edit", null, messageId);
    }

    @Override
    public void deleteMessage(String accessToken, UUID messageId, Map<String, Object> payload) {
        Objects.requireNonNull(messageId, "messageId must not be null");
        deleteJson(accessToken, "/api/v1/messages/" + messageId, payload, "delete", null, messageId);
    }

    @Override
    public void toggleReaction(String accessToken, UUID messageId, String reaction) {
        Objects.requireNonNull(messageId, "messageId must not be null");
        if (reaction == null || reaction.isBlank()) {
            throw new IllegalArgumentException("reaction must not be blank");
        }

        postEmpty(accessToken, "/api/v1/messages/" + messageId + "/reactions/" + reaction, "reaction", null, messageId);
    }

    @Override
    public boolean canAccessRoom(String accessToken, UUID roomId) {
        Objects.requireNonNull(roomId, "roomId must not be null");

        if (accessToken == null || accessToken.isBlank()) {
            log.warn("[CHAT-EDGE] room access check denied due to missing access token roomId={}", roomId);
            return false;
        }

        try {
            restClient.get()
                    .uri("/api/v1/rooms/" + roomId + "/member-count")
                    .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                    .retrieve()
                    .toBodilessEntity();

            return true;
        } catch (Exception ex) {
            log.warn("[CHAT-EDGE] room access check failed roomId={}", roomId, ex);
            return false;
        }
    }

    @Override
    public void joinRoom(String accessToken, UUID roomId) {
        Objects.requireNonNull(roomId, "roomId must not be null");
        postEmpty(accessToken, "/api/v1/rooms/" + roomId + "/join", "join", roomId, null);
    }

    @Override
    public void leaveRoom(String accessToken, UUID roomId) {
        Objects.requireNonNull(roomId, "roomId must not be null");
        postEmpty(accessToken, "/api/v1/rooms/" + roomId + "/leave", "leave", roomId, null);
    }

    @Override
    public void pinMessage(String accessToken, UUID roomId, UUID messageId) {
        Objects.requireNonNull(roomId, "roomId must not be null");
        Objects.requireNonNull(messageId, "messageId must not be null");
        postEmpty(accessToken, "/api/v1/rooms/" + roomId + "/pins?messageId=" + messageId, "pin", roomId, messageId);
    }

    @Override
    public void unpinMessage(String accessToken, UUID roomId, UUID messageId) {
        Objects.requireNonNull(roomId, "roomId must not be null");
        Objects.requireNonNull(messageId, "messageId must not be null");
        deleteJson(accessToken, "/api/v1/rooms/" + roomId + "/pins/" + messageId, Map.of(), "unpin", roomId, messageId);
    }

    private void postJson(String accessToken, String path, Map<String, Object> payload, String command, UUID roomId, UUID messageId) {
        runWithLogging(accessToken, command, roomId, messageId, () -> restClient.post()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload == null ? Map.of() : payload)
                .retrieve()
                .toBodilessEntity());
    }

    private void putJson(String accessToken, String path, Map<String, Object> payload, String command, UUID roomId, UUID messageId) {
        runWithLogging(accessToken, command, roomId, messageId, () -> restClient.put()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload == null ? Map.of() : payload)
                .retrieve()
                .toBodilessEntity());
    }

    private void deleteJson(String accessToken, String path, Map<String, Object> payload, String command, UUID roomId, UUID messageId) {
        runWithLogging(accessToken, command, roomId, messageId, () -> restClient.method(HttpMethod.DELETE)
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload == null ? Map.of() : payload)
                .retrieve()
                .toBodilessEntity());
    }

    private void postEmpty(String accessToken, String path, String command, UUID roomId, UUID messageId) {
        runWithLogging(accessToken, command, roomId, messageId, () -> restClient.post()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .retrieve()
                .toBodilessEntity());
    }

    private void runWithLogging(String accessToken, String command, UUID roomId, UUID messageId, Runnable action) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("Cannot forward chat command without an access token");
        }

        try {
            action.run();
            log.debug("[CHAT-EDGE] forwarded command={} roomId={} messageId={}", command, roomId, messageId);
        } catch (Exception ex) {
            log.warn("[CHAT-EDGE] failed to forward command={} roomId={} messageId={}", command, roomId, messageId, ex);
            throw new IllegalStateException("Failed to forward chat command to chat-service", ex);
        }
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }
}