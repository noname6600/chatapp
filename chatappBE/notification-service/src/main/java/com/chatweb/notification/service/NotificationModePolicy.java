package com.chatweb.notification.service;

import com.chatweb.notification.entity.RoomNotificationMode;
import org.springframework.stereotype.Component;

@Component
public class NotificationModePolicy {

    public boolean shouldDeliverRoomEvent(RoomNotificationMode mode, boolean isMentioned) {
        return shouldDeliverRoomEvent(mode, isMentioned, false);
    }

    public boolean shouldDeliverRoomEvent(RoomNotificationMode mode, boolean isMentioned, boolean isPersonallyTargeted) {
        RoomNotificationMode normalizedMode = mode == null ? RoomNotificationMode.NO_RESTRICT : mode;

        return switch (normalizedMode) {
            case NO_RESTRICT -> true;
            case ONLY_MENTION -> isMentioned || isPersonallyTargeted;
            case NOTHING -> false;
        };
    }
}
