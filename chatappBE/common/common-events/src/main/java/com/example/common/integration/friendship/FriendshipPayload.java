package com.example.common.integration.friendship;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.UUID;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public final class FriendshipPayload {

    private final UUID userLow;
    private final UUID userHigh;
    private final UUID actionUserId;
    private final String status;

    @JsonCreator
    public FriendshipPayload(
            @JsonProperty("userLow") UUID userLow,
            @JsonProperty("userHigh") UUID userHigh,
            @JsonProperty("actionUserId") UUID actionUserId,
            @JsonProperty("status") String status
    ) {
        this.userLow = userLow;
        this.userHigh = userHigh;
        this.actionUserId = actionUserId;
        this.status = status;
    }
}


