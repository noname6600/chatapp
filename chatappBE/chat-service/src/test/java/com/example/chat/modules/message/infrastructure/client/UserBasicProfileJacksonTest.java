package com.example.chat.modules.message.infrastructure.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserBasicProfileJacksonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserialize_ignoresUnknownFields() throws Exception {
        UUID accountId = UUID.randomUUID();

        String json = """
                {
                  "accountId": "%s",
                  "displayName": "Alice",
                  "avatarUrl": "https://cdn.example/avatar.png",
                  "username": "alice_123"
                }
                """.formatted(accountId);

        UserBasicProfile profile = objectMapper.readValue(json, UserBasicProfile.class);

        assertThat(profile.getAccountId()).isEqualTo(accountId);
        assertThat(profile.getDisplayName()).isEqualTo("Alice");
        assertThat(profile.getAvatarUrl()).isEqualTo("https://cdn.example/avatar.png");
    }
}
