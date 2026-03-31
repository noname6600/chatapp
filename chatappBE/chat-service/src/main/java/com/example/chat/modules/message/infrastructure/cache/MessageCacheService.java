package com.example.chat.modules.message.infrastructure.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;


@RequiredArgsConstructor
public class MessageCacheService {

    private final RedisTemplate<String, Object> redis;

    public void evictRoomMessages(UUID roomId) {

        String key = "room:messages:" + roomId;

        redis.delete(key);
    }
}