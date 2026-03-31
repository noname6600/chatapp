package com.example.common.integration.presence;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public final class PresenceUserStatePayload {

    private final UUID userId;

    private final PresenceStatus status;

    @JsonCreator
    public PresenceUserStatePayload(
            @JsonProperty("userId") UUID userId,
            @JsonProperty("status") PresenceStatus status
    ) {
        this.userId = userId;
        this.status = status;
    }
}