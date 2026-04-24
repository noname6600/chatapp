package com.example.chat.modules.room.service.impl;

import com.example.chat.modules.message.infrastructure.cache.CacheNames;
import com.example.chat.modules.room.dto.LastMessagePreview;
import com.example.chat.modules.room.dto.RoomMemberResponse;
import com.example.chat.modules.room.dto.RoomResponse;
import com.example.chat.modules.room.entity.Room;
import com.example.chat.modules.room.enums.RoomType;
import com.example.chat.modules.room.repository.RoomMemberRepository;
import com.example.chat.modules.room.repository.RoomRepository;
import com.example.chat.modules.room.repository.projection.RoomRow;
import com.example.chat.modules.room.service.IRoomQueryService;
import com.example.common.redis.api.ITimeRedisCacheManager;
import com.example.common.redis.exception.CreateCacheException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class RoomQueryService implements IRoomQueryService {

    private static final Duration TTL = Duration.ofMinutes(5);

    private final RoomRepository roomRepo;
    private final RoomMemberRepository memberRepo;
    private final ITimeRedisCacheManager cacheManager;

    @Override
    public List<RoomResponse> roomsOfUser(UUID userId) {

        String cacheName = CacheNames.ROOMS;
        String key = roomsKey(userId);

        try {
            List<?> cached = cacheManager.get(cacheName, key, List.class);
            if (cached != null) {
                return cached.stream()
                        .map(RoomResponse.class::cast)
                        .collect(Collectors.toList());
            }
        } catch (Exception ignored) {
            try {
                cacheManager.evict(cacheName, key);
            } catch (Exception ignore) {}
        }

        List<RoomRow> rows = roomRepo.findRoomsOfUserAdvanced(userId);

        Map<UUID, RoomResponse> byRoomId = new LinkedHashMap<>();
        int duplicateCount = 0;

        for (RoomRow row : rows) {
            RoomResponse candidate = mapToResponse(row, userId);
            UUID roomId = candidate.getId();
            if (byRoomId.putIfAbsent(roomId, candidate) != null) {
                duplicateCount++;
            }
        }

        if (duplicateCount > 0) {
            log.warn(
                    "Suppressed duplicate room rows for userId={}, duplicateCount={}, rawRows={}, uniqueRooms={}",
                    userId,
                    duplicateCount,
                    rows.size(),
                    byRoomId.size()
            );
        }

        List<RoomResponse> result = byRoomId.values()
                .stream()
                .collect(Collectors.toList());

        try {
            cacheManager.put(cacheName, key, result, TTL);
        } catch (CreateCacheException ignored) {}

        return result;
    }

    @Override
    public List<RoomMemberResponse> membersOfRoom(UUID roomId) {
        return memberRepo.findMembersOfRoom(roomId)
                .stream()
                .map(m -> RoomMemberResponse.builder()
                        .userId(m.getUserId())
                        .name(m.getDisplayName())
                        .avatarUrl(m.getAvatarUrl())
                        .role(m.getRole())
                        .joinedAt(m.getJoinedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public Map<UUID, List<RoomMemberResponse>> membersOfRooms(List<UUID> roomIds) {
        if (roomIds == null || roomIds.isEmpty()) return Map.of();

        return memberRepo.findMembersOfRooms(roomIds)
                .stream()
                .collect(Collectors.groupingBy(
                        m -> m.getRoomId(),
                        Collectors.mapping(
                                m -> RoomMemberResponse.builder()
                                        .userId(m.getUserId())
                                        .name(m.getDisplayName())
                                        .avatarUrl(m.getAvatarUrl())
                                        .role(m.getRole())
                                        .joinedAt(m.getJoinedAt())
                                        .build(),
                                Collectors.toList()
                        )
                ));
    }

    @Override
    public long memberCount(UUID roomId) {
        return memberRepo.countByRoomId(roomId);
    }

    private RoomResponse mapToResponse(RoomRow row, UUID userId) {

        Room room = row.getRoom();

        int unreadCount = row.getUnreadCount() == null
                ? 0
                : Math.max(0, row.getUnreadCount().intValue());

        String name = room.getName();
        String avatar = room.getAvatarUrl();
        UUID otherUserId = null;

        if (room.getType() == RoomType.PRIVATE) {

            UUID u1 = row.getUser1Id();
            UUID u2 = row.getUser2Id();

            if (u1 != null && u1.equals(userId)) {
                otherUserId = u2;
                name = row.getUser2Name();
                avatar = row.getUser2Avatar();
            } else {
                otherUserId = u1;
                name = row.getUser1Name();
                avatar = row.getUser1Avatar();
            }
        }

        return RoomResponse.builder()
                .id(room.getId())
                .type(room.getType())
                .name(name)
                .avatarUrl(avatar)
                .createdBy(room.getCreatedBy())
                .createdAt(room.getCreatedAt())
                .myRole(row.getRole())
                .unreadCount(unreadCount)
                .otherUserId(otherUserId)
                .lastMessage(buildPreview(room))
                .build();
    }

    private LastMessagePreview buildPreview(Room room) {

        if (room.getLastMessageId() == null) {
            return null;
        }

        return LastMessagePreview.builder()
                .id(room.getLastMessageId())
                .senderId(room.getLastMessageSenderId())
                .content(room.getLastMessagePreview())
                .createdAt(room.getLastMessageAt())
                .build();
    }

    private String roomsKey(UUID userId) {
        return "rooms:user:" + userId;
    }
}