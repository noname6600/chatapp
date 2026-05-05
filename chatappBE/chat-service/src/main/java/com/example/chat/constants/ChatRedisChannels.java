package com.example.chat.constants;

import com.example.common.redis.channel.RedisChannels;

import java.util.UUID;

public final class ChatRedisChannels {

    public static final String CHAT_ROOM = RedisChannels.CHAT_ROOM_PREFIX;
    public static final String CHAT_ROOM_PATTERN = RedisChannels.CHAT_ROOM_PATTERN;

    private ChatRedisChannels() {
    }

    public static String roomChannel(UUID roomId) {
        return RedisChannels.chatRoom(roomId);
    }

}