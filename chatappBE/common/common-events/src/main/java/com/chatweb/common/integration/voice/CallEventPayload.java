package com.chatweb.common.integration.voice;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public final class CallEventPayload {

    private final String eventType;
    private final UUID callId;

    private final UUID callerId;
    private final String callerName;
    private final String callerAvatarUrl;

    private final UUID calleeId;
    private final String calleeName;
    private final String calleeAvatarUrl;

    /** LiveKit token for the caller — populated only on CALL_ACCEPTED. */
    private final String callerLkToken;

    /** LiveKit token for the callee — populated only on CALL_ACCEPTED. */
    private final String calleeLkToken;

    /** LiveKit room name for this call (format: call-{callId}). */
    private final String lkRoomName;

    /** External LiveKit URL clients use to connect (wss://...). */
    private final String lkExternalUrl;

    /** Populated on CALL_ENDED only. */
    private final Integer durationSeconds;

    private final long timestamp;

    @JsonCreator
    public CallEventPayload(
            @JsonProperty("eventType") String eventType,
            @JsonProperty("callId") UUID callId,
            @JsonProperty("callerId") UUID callerId,
            @JsonProperty("callerName") String callerName,
            @JsonProperty("callerAvatarUrl") String callerAvatarUrl,
            @JsonProperty("calleeId") UUID calleeId,
            @JsonProperty("calleeName") String calleeName,
            @JsonProperty("calleeAvatarUrl") String calleeAvatarUrl,
            @JsonProperty("callerLkToken") String callerLkToken,
            @JsonProperty("calleeLkToken") String calleeLkToken,
            @JsonProperty("lkRoomName") String lkRoomName,
            @JsonProperty("lkExternalUrl") String lkExternalUrl,
            @JsonProperty("durationSeconds") Integer durationSeconds,
            @JsonProperty("timestamp") long timestamp
    ) {
        this.eventType = eventType;
        this.callId = callId;
        this.callerId = callerId;
        this.callerName = callerName;
        this.callerAvatarUrl = callerAvatarUrl;
        this.calleeId = calleeId;
        this.calleeName = calleeName;
        this.calleeAvatarUrl = calleeAvatarUrl;
        this.callerLkToken = callerLkToken;
        this.calleeLkToken = calleeLkToken;
        this.lkRoomName = lkRoomName;
        this.lkExternalUrl = lkExternalUrl;
        this.durationSeconds = durationSeconds;
        this.timestamp = timestamp;
    }
}
