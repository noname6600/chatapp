package com.example.presence.constants;

import com.example.common.redis.channel.RedisChannels;

import java.util.UUID;

public final class PresenceRedisChannels {

    private PresenceRedisChannels() {}

    public static final String PRESENCE_USER = RedisChannels.PRESENCE_USER;

    public static final String PRESENCE_ROOM = RedisChannels.PRESENCE_ROOM_PREFIX;

    public static final String PRESENCE_GLOBAL = RedisChannels.PRESENCE_GLOBAL;

    public static final String PRESENCE_PATTERN = RedisChannels.PRESENCE_PATTERN;

    public static String roomChannel(UUID roomId) {
        return RedisChannels.presenceRoom(roomId);
    }
}