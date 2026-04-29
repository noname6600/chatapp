package com.example.notification.websocket;

import com.example.common.websocket.dto.WsOutgoingMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WsOutgoingMessageCompatibilityTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void readsLegacyDataFieldIntoPayload() throws Exception {
        String json = "{\"type\":\"EVENT\",\"data\":{\"k\":\"v\"}}";

        WsOutgoingMessage message = objectMapper.readValue(json, WsOutgoingMessage.class);

        assertThat(message.getType()).isEqualTo("EVENT");
        assertThat(message.getPayload()).isEqualTo(java.util.Map.of("k", "v"));
    }

    @Test
    void writesCanonicalPayloadField() throws Exception {
        WsOutgoingMessage message = new WsOutgoingMessage("EVENT", java.util.Map.of("k", "v"));

        String json = objectMapper.writeValueAsString(message);

        assertThat(json).contains("\"payload\"");
        assertThat(json).doesNotContain("\"data\"");
    }
}
