package com.example.common.integration.presence;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Payload for presence heartbeat events.
 *
 * <p>Used by {@link PresenceEventType#USER_HEARTBEAT} to track user presence
 * status with a timestamp. This was previously misplaced in the user package
 * as {@code UserPresencePayload}.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public final class PresenceHeartbeatPayload {

    private final UUID userId;
    private final Instant at;

    @JsonCreator
    public PresenceHeartbeatPayload(
            @JsonProperty("userId") UUID userId,
            @JsonProperty("at") Instant at
    ) {
        this.userId = userId;
        this.at = at;
    }
}
