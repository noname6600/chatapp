package com.example.chat.modules.room.service.impl;

import com.example.chat.modules.message.infrastructure.cache.CacheNames;
import com.example.chat.modules.message.infrastructure.client.UserClient;
import com.example.chat.modules.room.dto.LastMessagePreview;
import com.example.chat.modules.room.dto.RoomResponse;
import com.example.chat.modules.message.infrastructure.client.UserBasicProfile;
import com.example.chat.modules.room.entity.PrivateRoom;
import com.example.chat.modules.room.entity.Room;
import com.example.chat.modules.room.entity.RoomMember;
import com.example.chat.modules.room.enums.Role;
import com.example.chat.modules.room.enums.RoomType;
import com.example.chat.modules.room.repository.PrivateRoomRepository;
import com.example.chat.modules.room.repository.RoomMemberRepository;
import com.example.chat.modules.room.repository.RoomRepository;
import com.example.chat.modules.room.service.IPrivateRoomService;
import com.example.common.redis.api.ITimeRedisCacheManager;
import com.example.common.redis.exception.CreateCacheException;
import com.example.common.web.exception.BusinessException;
import com.example.common.web.exception.ErrorCode;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PrivateRoomService implements IPrivateRoomService {

    private final RoomRepository roomRepo;
    private final RoomMemberRepository memberRepo;
    private final PrivateRoomRepository privateRoomRepo;
    private final ITimeRedisCacheManager cacheManager;

    private final UserClient userClient;

    @Override
    public RoomResponse getOrCreatePrivateRoom(UUID u1, UUID u2) {

        if (u1.equals(u2)) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "Cannot chat with yourself"
            );
        }

        UUID a = u1.compareTo(u2) < 0 ? u1 : u2;
        UUID b = u1.compareTo(u2) < 0 ? u2 : u1;

        return privateRoomRepo
                .findByUser1IdAndUser2Id(a, b)
                .map(pr -> buildRoom(roomRepo.getReferenceById(pr.getRoomId())))
                .orElseGet(() -> createPrivateRoom(u1, u2, a, b));
    }

    private RoomResponse createPrivateRoom(
            UUID u1,
            UUID u2,
            UUID a,
            UUID b
    ) {

        try {

            List<UserBasicProfile> users =
                    userClient.getUsersBulk(List.of(u1, u2)).getData();

            if (users.size() != 2) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND ,"Not found users");
            }

            Map<UUID, UserBasicProfile> map =
                    users.stream()
                            .collect(Collectors.toMap(
                                    UserBasicProfile::getAccountId,
                                    Function.identity()
                            ));

            UserBasicProfile user1 = map.get(u1);
            UserBasicProfile user2 = map.get(u2);

            Room room = Room.builder()
                    .type(RoomType.PRIVATE)
                    .createdBy(u1)
                    .build();

            roomRepo.save(room);

            memberRepo.saveAll(List.of(

                    member(
                            room.getId(),
                            user1.getAccountId(),
                            user1.getDisplayName(),
                            user1.getAvatarUrl()
                    ),

                    member(
                            room.getId(),
                            user2.getAccountId(),
                            user2.getDisplayName(),
                            user2.getAvatarUrl()
                    )

            ));

            privateRoomRepo.save(
                    PrivateRoom.builder()
                            .user1Id(a)
                            .user2Id(b)
                            .roomId(room.getId())
                            .build()
            );

            evictRoomsCache(u1);
            evictRoomsCache(u2);

            return buildRoom(room);

        } catch (DataIntegrityViolationException ex) {

            log.warn("Race condition when creating private room");

            PrivateRoom existing =
                    privateRoomRepo
                            .findByUser1IdAndUser2Id(a, b)
                            .orElseThrow();

            Room room =
                    roomRepo.getReferenceById(existing.getRoomId());

            return buildRoom(room);
        }
    }

    private RoomMember member(
            UUID roomId,
            UUID userId,
            String name,
            String avatar
    ) {

        return RoomMember.builder()
                .roomId(roomId)
                .userId(userId)
                .displayName(name)
                .avatarUrl(avatar)
                .role(Role.MEMBER)
                .build();
    }

    private RoomResponse buildRoom(Room room) {

        return RoomResponse.builder()
                .id(room.getId())
                .type(room.getType())
                .createdBy(room.getCreatedBy())
                .createdAt(room.getCreatedAt())

                .name(null)
                .avatarUrl(null)

                .myRole(Role.MEMBER)
                .unreadCount(0)
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

    private void evictRoomsCache(UUID userId) {

        try {
            cacheManager.evict(CacheNames.ROOMS, roomsKey(userId));
        } catch (CreateCacheException ignored) {
        }
    }

    private String roomsKey(UUID userId) {
        return "rooms:user:" + userId;
    }
}