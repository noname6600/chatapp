package com.example.chat.modules.room.service.impl;

import com.example.chat.modules.message.infrastructure.client.UserBasicProfile;
import com.example.chat.modules.message.infrastructure.client.UserClient;
import com.example.chat.modules.room.entity.PrivateRoom;
import com.example.chat.modules.room.entity.Room;
import com.example.chat.modules.room.entity.RoomMember;
import com.example.chat.modules.room.repository.PrivateRoomRepository;
import com.example.chat.modules.room.repository.RoomMemberRepository;
import com.example.chat.modules.room.repository.RoomRepository;
import com.example.common.redis.api.ITimeRedisCacheManager;
import com.example.common.core.exception.BusinessException;
import com.example.common.core.exception.CommonErrorCode;
import com.example.common.web.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrivateRoomServiceTest {

    @Mock
    private RoomRepository roomRepo;
    @Mock
    private RoomMemberRepository memberRepo;
    @Mock
    private PrivateRoomRepository privateRoomRepo;
    @Mock
    private ITimeRedisCacheManager cacheManager;
    @Mock
    private UserClient userClient;

    @InjectMocks
    private PrivateRoomService privateRoomService;

    private UUID user1;
    private UUID user2;
    private UUID roomId;

    @BeforeEach
    void setUp() {
        user1 = UUID.randomUUID();
        user2 = UUID.randomUUID();
        roomId = UUID.randomUUID();
    }

    @Test
    void getOrCreatePrivateRoom_throwsNotFound_whenRequiredProfileFieldMissing() {
        when(privateRoomRepo.findByUser1IdAndUser2Id(any(UUID.class), any(UUID.class)))
                .thenReturn(Optional.empty());

        UserBasicProfile p1 = profile(user1, "Alice", null);
        UserBasicProfile p2 = profile(user2, "Bob", "https://cdn.example/bob.png");

        when(userClient.getUsersBulk(List.of(user1, user2)))
                .thenReturn(ApiResponse.success(List.of(p1, p2)));

        assertThatThrownBy(() -> privateRoomService.getOrCreatePrivateRoom(user1, user2))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND);

        verify(roomRepo, never()).save(any(Room.class));
        verify(memberRepo, never()).saveAll(anyList());
    }

    @Test
    void getOrCreatePrivateRoom_createsRoom_whenProfilesAreValid() {
        when(privateRoomRepo.findByUser1IdAndUser2Id(any(UUID.class), any(UUID.class)))
                .thenReturn(Optional.empty());

        UserBasicProfile p1 = profile(user1, "Alice", "https://cdn.example/alice.png");
        UserBasicProfile p2 = profile(user2, "Bob", "https://cdn.example/bob.png");

        when(userClient.getUsersBulk(List.of(user1, user2)))
                .thenReturn(ApiResponse.success(List.of(p1, p2)));

        when(roomRepo.save(any(Room.class))).thenAnswer(invocation -> {
            Room room = invocation.getArgument(0);
            room.setId(roomId);
            return room;
        });

        when(memberRepo.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(privateRoomRepo.save(any(PrivateRoom.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(privateRoomService.getOrCreatePrivateRoom(user1, user2)).isNotNull();

        verify(roomRepo).save(any(Room.class));
        verify(memberRepo).saveAll(anyList());
        verify(privateRoomRepo).save(any(PrivateRoom.class));
    }

    private UserBasicProfile profile(UUID accountId, String displayName, String avatarUrl) {
        UserBasicProfile profile = new UserBasicProfile();
        profile.setAccountId(accountId);
        profile.setDisplayName(displayName);
        profile.setAvatarUrl(avatarUrl);
        return profile;
    }
}


