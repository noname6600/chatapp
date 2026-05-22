package com.chatweb.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/**
 * Minimal realtime notification command model forwarded by realtime-edge-service.
 *
 * Supported commands in Phase B:
 * - mark-read
 * - mark-all-read
 * - clear-room
 * - mark-read-by-room
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationRealtimeCommandRequest {
    private String command;
    private UUID notificationId;
    private UUID roomId;
    private String requestId;

    public NotificationRealtimeCommandRequest() {
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public UUID getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(UUID notificationId) {
        this.notificationId = notificationId;
    }

    public UUID getRoomId() {
        return roomId;
    }

    public void setRoomId(UUID roomId) {
        this.roomId = roomId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
