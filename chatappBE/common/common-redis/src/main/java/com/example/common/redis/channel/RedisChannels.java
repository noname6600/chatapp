package com.example.common.redis.channel;

import java.util.UUID;

/**
 * Canonical Redis channel constants owned by common-redis.
 *
 * This class is the canonical location for Redis channel routing constants.
 */
public final class RedisChannels {

    private RedisChannels() {
    }

    public static final String CHAT_ROOM_PREFIX = "realtime.chat.room.";
    public static final String CHAT_ROOM_PATTERN = "realtime.chat.room.*";

    public static final String NOTIFICATION_USER_PREFIX = "realtime.notification.user.";
    public static final String NOTIFICATION_USER_PATTERN = "realtime.notification.user.*";

    public static final String PRESENCE_USER = "realtime.presence.user";
    public static final String PRESENCE_ROOM_PREFIX = "realtime.presence.room.";
    public static final String PRESENCE_GLOBAL = "realtime.presence.global";
    public static final String PRESENCE_PATTERN = "realtime.presence.*";

    public static String chatRoom(UUID roomId) {
        return CHAT_ROOM_PREFIX + roomId;
    }

    public static String notificationUser(UUID userId) {
        return NOTIFICATION_USER_PREFIX + userId;
    }

    public static String presenceRoom(UUID roomId) {
        return PRESENCE_ROOM_PREFIX + roomId;
    }
}
