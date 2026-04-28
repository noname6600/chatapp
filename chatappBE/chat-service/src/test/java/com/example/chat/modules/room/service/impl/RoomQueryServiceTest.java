package com.example.chat.modules.room.service.impl;

import com.example.chat.modules.room.dto.PagedRoomMembersResponse;
import com.example.chat.modules.room.dto.RoomMemberResponse;
import com.example.chat.modules.room.entity.RoomBan;
import com.example.chat.modules.room.entity.RoomMember;
import com.example.chat.modules.room.enums.Role;
import com.example.chat.modules.room.repository.RoomBanRepository;
import com.example.chat.modules.room.repository.RoomMemberRepository;
import com.example.chat.modules.room.repository.RoomRepository;
import com.example.common.redis.api.ITimeRedisCacheManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomQueryServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private RoomMemberRepository roomMemberRepository;

    @Mock
    private RoomBanRepository roomBanRepository;

    @Mock
    private ITimeRedisCacheManager cacheManager;

    private RoomQueryService roomQueryService;

    private UUID roomId;

    @BeforeEach
    void setUp() {
        roomQueryService = new RoomQueryService(roomRepository, roomMemberRepository, roomBanRepository, cacheManager);
        roomId = UUID.randomUUID();
    }

    @Test
    void membersOfRoom_mapsTypedMembers_withoutCastFailure() {
        RoomMember member = RoomMember.builder()
                .roomId(roomId)
                .userId(UUID.randomUUID())
                .displayName("Alice")
                .avatarUrl("https://cdn.example/avatar.png")
                .role(Role.MEMBER)
                .joinedAt(Instant.now())
                .build();

        when(roomMemberRepository.findByRoomId(roomId)).thenReturn(List.of(member));

        assertThatNoException().isThrownBy(() -> roomQueryService.membersOfRoom(roomId));

        List<RoomMemberResponse> response = roomQueryService.membersOfRoom(roomId);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().getUserId()).isEqualTo(member.getUserId());
        assertThat(response.getFirst().getName()).isEqualTo("Alice");
    }

    @Test
    void membersOfRoom_returnsPagedMembers_withMetadata() {
        RoomMember member = RoomMember.builder()
                .roomId(roomId)
                .userId(UUID.randomUUID())
                .displayName("Bob")
                .role(Role.OWNER)
                .joinedAt(Instant.now())
                .build();

        Pageable pageable = PageRequest.of(0, 20);
        Page<RoomMember> page = new PageImpl<>(List.of(member), pageable, 50);

        when(roomMemberRepository.findByRoomId(eq(roomId), any(Pageable.class))).thenReturn(page);

        PagedRoomMembersResponse response = roomQueryService.membersOfRoom(roomId, 0, 20, null);

        assertThat(response.getMembers()).hasSize(1);
        assertThat(response.getMembers().getFirst().getName()).isEqualTo("Bob");
        assertThat(response.getPage()).isEqualTo(0);
        assertThat(response.getSize()).isEqualTo(20);
        assertThat(response.getShown()).isEqualTo(1);
        assertThat(response.getTotal()).isEqualTo(50);
        assertThat(response.getTotalPages()).isEqualTo(3);

        verify(roomMemberRepository).findByRoomId(eq(roomId), any(Pageable.class));
        verify(roomMemberRepository, never())
                .findByRoomIdAndDisplayNameContainingIgnoreCase(eq(roomId), any(String.class), any(Pageable.class));
    }

    @Test
    void membersOfRoom_usesSearchPath_whenQueryProvided() {
        Pageable pageable = PageRequest.of(1, 10);
        Page<RoomMember> page = new PageImpl<>(List.of(), pageable, 0);

        when(roomMemberRepository.findByRoomIdAndDisplayNameContainingIgnoreCase(eq(roomId), eq("ali"), any(Pageable.class)))
                .thenReturn(page);

        PagedRoomMembersResponse response = roomQueryService.membersOfRoom(roomId, 1, 10, "ali");

        assertThat(response.getMembers()).isEmpty();
        verify(roomMemberRepository)
                .findByRoomIdAndDisplayNameContainingIgnoreCase(eq(roomId), eq("ali"), any(Pageable.class));
        verify(roomMemberRepository, never()).findByRoomId(eq(roomId), any(Pageable.class));
    }

    @Test
    void bannedMembersOfRoom_returnsPagedUserIds() {
        UUID bannedUser = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);
        Page<RoomBan> page = new PageImpl<>(List.of(
                RoomBan.builder().roomId(roomId).userId(bannedUser).build()
        ), pageable, 1);

        when(roomBanRepository.findByRoomId(eq(roomId), any(Pageable.class))).thenReturn(page);

        var response = roomQueryService.bannedMembersOfRoom(roomId, 0, 20);

        assertThat(response.getUserIds()).containsExactly(bannedUser);
        assertThat(response.getTotal()).isEqualTo(1);
    }
}
