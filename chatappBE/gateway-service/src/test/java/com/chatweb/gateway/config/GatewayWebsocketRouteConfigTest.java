package com.chatweb.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayWebsocketRouteConfigTest {

    @Test
    void applicationYaml_containsCanonicalWsRouteRewriteToRealtimeHandler() throws IOException {
        ClassPathResource resource = new ClassPathResource("application.yaml");
        String yaml = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(yaml).contains("- id: realtime-edge-ws");
        assertThat(yaml).contains("- Path=/ws/**");
        assertThat(yaml).contains("- RewritePath=/ws/?(?<segment>.*), /realtime");
    }
}
