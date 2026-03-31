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
public final class PresenceRoomLeavePayload {

    private final UUID userId;

    private final UUID roomId;

    @JsonCreator
    public PresenceRoomLeavePayload(
            @JsonProperty("userId") UUID userId,
            @JsonProperty("roomId") UUID roomId
    ) {
        this.userId = userId;
        this.roomId = roomId;
    }
}
