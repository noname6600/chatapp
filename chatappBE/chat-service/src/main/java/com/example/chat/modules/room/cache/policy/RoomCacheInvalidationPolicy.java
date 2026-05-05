package com.example.chat.modules.room.cache.policy;

import com.example.chat.modules.room.cache.port.RoomListCachePort;
import com.example.chat.modules.room.repository.RoomMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RoomCacheInvalidationPolicy {

    private final RoomListCachePort roomListCachePort;
    private final RoomMemberRepository roomMemberRepository;

    public void evictRoomsForUser(UUID userId) {
        roomListCachePort.evictRooms(userId);
    }

    public void evictRoomsForUsers(Collection<UUID> userIds) {
        for (UUID userId : userIds) {
            roomListCachePort.evictRooms(userId);
        }
    }

    public void evictRoomsForRoomMembers(UUID roomId) {
        evictRoomsForUsers(roomMemberRepository.findUserIdsByRoomId(roomId));
    }

    public void evictAfterProfileMutation(UUID userId) {
        evictRoomsForUser(userId);
    }
}
