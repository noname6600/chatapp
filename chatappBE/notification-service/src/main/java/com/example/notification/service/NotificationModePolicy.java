package com.example.notification.service;

import com.example.notification.entity.RoomNotificationMode;
import org.springframework.stereotype.Component;

@Component
public class NotificationModePolicy {

    public boolean shouldDeliverRoomEvent(RoomNotificationMode mode, boolean isMentioned) {
        RoomNotificationMode normalizedMode = mode == null ? RoomNotificationMode.NO_RESTRICT : mode;

        return switch (normalizedMode) {
            case NO_RESTRICT -> true;
            case ONLY_MENTION -> isMentioned;
            case NOTHING -> false;
        };
    }
}
