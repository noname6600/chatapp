package com.example.common.integration.presence;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public final class RoomOnlineUsersPayload {

    private final UUID roomId;

    private final List<PresenceUserStatePayload> users;

    @JsonCreator
    public RoomOnlineUsersPayload(
            @JsonProperty("roomId") UUID roomId,
            @JsonProperty("users") List<PresenceUserStatePayload> users
    ) {
        this.roomId = roomId;
        this.users = users;
    }
}



