package com.chatweb.chat.modules.room.cache.port;

import com.chatweb.chat.modules.room.dto.RoomResponse;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface RoomListCachePort {

    List<RoomResponse> getRooms(UUID userId);

    void putRooms(UUID userId, List<RoomResponse> rooms, Duration ttl);

    void evictRooms(UUID userId);

    void evictRoomsBulk(Collection<UUID> userIds);
}
