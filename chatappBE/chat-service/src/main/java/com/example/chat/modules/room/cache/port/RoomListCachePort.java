package com.example.chat.modules.room.cache.port;

import com.example.chat.modules.room.dto.RoomResponse;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

public interface RoomListCachePort {

    List<RoomResponse> getRooms(UUID userId);

    void putRooms(UUID userId, List<RoomResponse> rooms, Duration ttl);

    void evictRooms(UUID userId);
}
