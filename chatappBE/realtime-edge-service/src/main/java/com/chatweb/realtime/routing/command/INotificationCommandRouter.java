package com.chatweb.realtime.routing.command;

/**
 * Routes notification commands from the edge to notification-service.
 *
 * Phase B only supports commands that already exist in notification-service:
 * - mark-read
 * - mark-all-read
 * - clear-room
 * - mark-read-by-room
 */
public interface INotificationCommandRouter {
    void route(String accessToken, NotificationCommandRequest commandRequest);
}
