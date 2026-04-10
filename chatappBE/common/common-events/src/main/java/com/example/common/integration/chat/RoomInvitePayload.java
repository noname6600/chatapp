package com.example.common.integration.chat;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
public final class RoomInvitePayload {

    private final UUID roomId;

    private final String roomName;

    private final String roomAvatarUrl;

    private final Integer memberCount;

    @JsonCreator
    public RoomInvitePayload(
            @JsonProperty("roomId") UUID roomId,
            @JsonProperty("roomName") String roomName,
            @JsonProperty("roomAvatarUrl") String roomAvatarUrl,
            @JsonProperty("memberCount") Integer memberCount
    ) {
        this.roomId = roomId;
        this.roomName = roomName;
        this.roomAvatarUrl = roomAvatarUrl;
        this.memberCount = memberCount;
    }
}
