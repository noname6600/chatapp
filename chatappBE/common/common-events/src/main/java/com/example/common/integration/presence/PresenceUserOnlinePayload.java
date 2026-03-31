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
public final class PresenceUserOnlinePayload {

    private final UUID userId;

    private final UUID roomId;

    private final PresenceStatus status;

    @JsonCreator
    public PresenceUserOnlinePayload(
            @JsonProperty("userId") UUID userId,
            @JsonProperty("roomId") UUID roomId,
            @JsonProperty("status") PresenceStatus status
    ) {
        this.userId = userId;
        this.roomId = roomId;
        this.status = status;
    }
}
