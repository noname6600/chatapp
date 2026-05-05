package com.example.notification.constants;

import com.example.common.redis.channel.RedisChannels;

import java.util.UUID;

public final class NotificationRedisChannels {

    public static final String NOTIFICATION_USER_PREFIX = RedisChannels.NOTIFICATION_USER_PREFIX;
    public static final String NOTIFICATION_USER_PATTERN = RedisChannels.NOTIFICATION_USER_PATTERN;

    private NotificationRedisChannels() {
    }

    public static String userChannel(UUID userId) {
        return RedisChannels.notificationUser(userId);
    }
}