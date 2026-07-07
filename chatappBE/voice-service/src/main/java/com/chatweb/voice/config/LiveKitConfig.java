package com.chatweb.voice.config;

import io.livekit.server.RoomServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LiveKitProperties.class)
@RequiredArgsConstructor
@Slf4j
public class LiveKitConfig {

    private final LiveKitProperties props;

    @Bean
    public RoomServiceClient roomServiceClient() {
        if (!props.isEnabled()) {
            log.warn("[LIVEKIT] Stub mode active — LIVEKIT_ENABLED=false. Room API calls are no-ops.");
            return RoomServiceClient.create(props.getUrlInternal(), props.getApiKey(), props.getApiSecret());
        }
        log.info("[LIVEKIT] Connecting to LiveKit at {}", props.getUrlInternal());
        return RoomServiceClient.create(props.getUrlInternal(), props.getApiKey(), props.getApiSecret());
    }
}
