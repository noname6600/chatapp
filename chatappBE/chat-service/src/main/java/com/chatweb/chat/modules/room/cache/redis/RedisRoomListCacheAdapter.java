package com.chatweb.chat.modules.room.cache.redis;

import com.chatweb.chat.modules.message.infrastructure.cache.CacheNames;
import com.chatweb.chat.modules.room.cache.port.RoomListCachePort;
import com.chatweb.chat.modules.room.dto.RoomResponse;
import com.chatweb.common.redis.cache.api.ITimeRedisCacheManager;
import com.chatweb.common.redis.cache.exception.CreateCacheException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RedisRoomListCacheAdapter implements RoomListCachePort {

    private final ITimeRedisCacheManager cacheManager;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${spring.application.name}")
    private String serviceName;

    @Override
    public List<RoomResponse> getRooms(UUID userId) {
        String key = roomsKey(userId);

        try {
            List<?> cached = cacheManager.get(CacheNames.ROOMS, key, List.class);
            if (cached == null) {
                return null;
            }
            return cached.stream()
                    .map(RoomResponse.class::cast)
                    .collect(Collectors.toList());
        } catch (Exception ignored) {
            evictRooms(userId);
            return null;
        }
    }

    @Override
    public void putRooms(UUID userId, List<RoomResponse> rooms, Duration ttl) {
        try {
            cacheManager.put(CacheNames.ROOMS, roomsKey(userId), rooms, ttl);
        } catch (CreateCacheException ignored) {
        }
    }

    @Override
    public void evictRooms(UUID userId) {
        try {
            cacheManager.evict(CacheNames.ROOMS, roomsKey(userId));
        } catch (CreateCacheException ignored) {
        }
    }

    @Override
    public void evictRoomsBulk(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) return;
        List<String> keys = userIds.stream()
                .map(id -> serviceName + "::" + CacheNames.ROOMS + "::" + roomsKey(id))
                .collect(Collectors.toList());
        stringRedisTemplate.delete(keys);
    }

    private String roomsKey(UUID userId) {
        return "rooms:user:" + userId;
    }
}
