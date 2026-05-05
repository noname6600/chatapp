package com.example.common.integration.presence;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public final class GlobalOnlineUsersPayload {

    private final List<PresenceUserStatePayload> users;

    @JsonCreator
    public GlobalOnlineUsersPayload(
            @JsonProperty("users") List<PresenceUserStatePayload> users
    ) {
        this.users = users;
    }
}
