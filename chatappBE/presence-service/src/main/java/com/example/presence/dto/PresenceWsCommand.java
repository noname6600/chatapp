package com.example.presence.dto;

import com.example.common.integration.presence.PresenceEventType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.UUID;

@Getter
public class PresenceWsCommand {

    private final PresenceEventType type;
    private final UUID roomId;
    private final Boolean active;

    @JsonCreator
    public PresenceWsCommand(
            @JsonProperty("type") PresenceEventType type,
            @JsonProperty("roomId") UUID roomId,
            @JsonProperty("active") Boolean active
    ) {
        this.type = type;
        this.roomId = roomId;
        this.active = active;
    }
}

