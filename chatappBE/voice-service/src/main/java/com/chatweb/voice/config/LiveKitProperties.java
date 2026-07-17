package com.chatweb.voice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "livekit")
public class LiveKitProperties {

    private String apiKey;
    private String apiSecret;
    private String urlInternal;
    private String urlExternal;
    private boolean enabled;
}
