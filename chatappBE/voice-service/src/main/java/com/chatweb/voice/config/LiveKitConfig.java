package com.chatweb.voice.config;

import io.livekit.server.RoomServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class LiveKitConfig {

    private final LiveKitProperties props;

    @Bean
    public RoomServiceClient roomServiceClient() {
        // RoomServiceClient uses Retrofit (HTTP REST) — convert ws/wss to http/https
        String httpUrl = toHttpUrl(props.getUrlInternal());
        if (!props.isEnabled()) {
            log.warn("[LIVEKIT] Stub mode active — LIVEKIT_ENABLED=false. Room API calls are no-ops.");
        } else {
            log.info("[LIVEKIT] Connecting to LiveKit REST API at {}", httpUrl);
        }
        return RoomServiceClient.create(httpUrl, props.getApiKey(), props.getApiSecret());
    }

    private static String toHttpUrl(String url) {
        if (url.startsWith("ws://"))  return "http://"  + url.substring(5);
        if (url.startsWith("wss://")) return "https://" + url.substring(6);
        return url;
    }
}
