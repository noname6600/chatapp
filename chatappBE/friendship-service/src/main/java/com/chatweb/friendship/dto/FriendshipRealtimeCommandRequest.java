package com.chatweb.friendship.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/**
 * Minimal command contract used by realtime-edge during Friendship phase migration.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FriendshipRealtimeCommandRequest {
    private String command;
    private UUID targetUserId;
    private String requestId;

    public FriendshipRealtimeCommandRequest() {
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public UUID getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(UUID targetUserId) {
        this.targetUserId = targetUserId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
