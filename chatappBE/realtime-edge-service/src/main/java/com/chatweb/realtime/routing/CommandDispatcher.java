package com.chatweb.realtime.routing;

import com.chatweb.realtime.adapter.out.friendship.RestFriendshipCommandRouter;
import com.chatweb.realtime.adapter.out.notification.RestNotificationCommandRouter;
import com.chatweb.realtime.connection.RealtimeSession;
import com.chatweb.realtime.protocol.RealtimeClientMessage;
import com.chatweb.realtime.routing.command.FriendshipCommandRequest;
import com.chatweb.realtime.routing.command.NotificationCommandRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Dispatches websocket commands to domain-specific routers.
 *
 * Currently wired: notification and friendship commands.
 * Other domains remain on their current service-local websocket paths until
 * their own migration phases.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CommandDispatcher {

    private final RestNotificationCommandRouter notificationCommandRouter;
    private final RestFriendshipCommandRouter friendshipCommandRouter;

    public boolean dispatch(RealtimeSession session, String accessToken, RealtimeClientMessage message) {
        if (message == null) {
            return false;
        }

        if (isNotificationCommand(message)) {
            dispatchNotification(accessToken, message);
            return true;
        }

        if (isFriendshipCommand(message)) {
            dispatchFriendship(accessToken, message);
            return true;
        }

        return false;
    }

    public void dispatchNotification(String accessToken, RealtimeClientMessage message) {
        NotificationCommandRequest request = new NotificationCommandRequest();
        request.setCommand(message.getCommand());
        request.setRequestId(message.getRequestId());

        Map<String, Object> payload = message.getPayload();
        if (payload != null) {
            Object notificationId = payload.get("notificationId");
            if (notificationId != null) {
                request.setNotificationId(java.util.UUID.fromString(notificationId.toString()));
            }

            Object roomId = payload.get("roomId");
            if (roomId != null) {
                request.setRoomId(java.util.UUID.fromString(roomId.toString()));
            }
        }

        notificationCommandRouter.route(accessToken, request);
    }

    public void dispatchFriendship(String accessToken, RealtimeClientMessage message) {
        FriendshipCommandRequest request = new FriendshipCommandRequest();
        request.setCommand(message.getCommand());
        request.setRequestId(message.getRequestId());

        Map<String, Object> payload = message.getPayload();
        if (payload != null) {
            Object targetUserId = payload.get("targetUserId");
            if (targetUserId == null) {
                targetUserId = payload.get("userId");
            }

            if (targetUserId != null) {
                request.setTargetUserId(UUID.fromString(targetUserId.toString()));
            }
        }

        friendshipCommandRouter.route(accessToken, request);
    }

    private boolean isNotificationCommand(RealtimeClientMessage message) {
        if (message.getDomain() != null && "notification".equalsIgnoreCase(message.getDomain())) {
            return true;
        }

        return "notification.command".equalsIgnoreCase(message.getType());
    }

    private boolean isFriendshipCommand(RealtimeClientMessage message) {
        if (message.getDomain() != null && "friendship".equalsIgnoreCase(message.getDomain())) {
            return true;
        }

        return "friendship.command".equalsIgnoreCase(message.getType());
    }
}
