package com.example.chat.modules.room.service.impl;

import com.example.chat.config.InviteCodeGenerator;
import com.example.chat.modules.room.entity.Room;
import com.example.chat.modules.room.entity.RoomMember;
import com.example.chat.modules.room.enums.Role;
import com.example.chat.modules.room.enums.RoomType;
import com.example.chat.modules.room.repository.RoomMemberRepository;
import com.example.chat.modules.room.repository.RoomRepository;
import com.example.common.redis.api.ITimeRedisCacheManager;
import com.example.common.web.exception.BusinessException;
import com.example.common.web.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

	@Mock
	private RoomRepository roomRepo;
	@Mock
	private RoomMemberRepository memberRepo;
	@Mock
	private InviteCodeGenerator inviteCodeGenerator;
	@Mock
	private GroupAvatarGenerator avatarGenerator;
	@Mock
	private CloudinaryService cloudinaryService;
	@Mock
	private ITimeRedisCacheManager cacheManager;

	@InjectMocks
	private RoomService roomService;

	private UUID userId;
	private UUID roomId;

	@BeforeEach
	void setUp() {
		userId = UUID.randomUUID();
		roomId = UUID.randomUUID();
	}

	@Test
	void joinByInviteRoomId_throwsNotFound_whenRoomMissing() {
		when(roomRepo.findById(roomId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> roomService.joinByInviteRoomId(userId, roomId))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
	}

	@Test
	void joinByInviteRoomId_throwsBadRequest_whenRoomIsPrivate() {
		when(roomRepo.findById(roomId)).thenReturn(Optional.of(Room.builder()
				.id(roomId)
				.type(RoomType.PRIVATE)
				.build()));

		assertThatThrownBy(() -> roomService.joinByInviteRoomId(userId, roomId))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.BAD_REQUEST);

		verify(memberRepo, never()).save(any(RoomMember.class));
	}

	@Test
	void joinByInviteRoomId_isIdempotent_whenAlreadyMember() {
		when(roomRepo.findById(roomId)).thenReturn(Optional.of(Room.builder()
				.id(roomId)
				.type(RoomType.GROUP)
				.build()));
		when(memberRepo.existsByRoomIdAndUserId(roomId, userId)).thenReturn(true);

		roomService.joinByInviteRoomId(userId, roomId);

		verify(memberRepo, never()).save(any(RoomMember.class));
	}

	@Test
	void joinByInviteRoomId_addsMembership_whenEligibleNonMember() {
		when(roomRepo.findById(roomId)).thenReturn(Optional.of(Room.builder()
				.id(roomId)
				.type(RoomType.GROUP)
				.build()));
		when(memberRepo.existsByRoomIdAndUserId(roomId, userId)).thenReturn(false);

		roomService.joinByInviteRoomId(userId, roomId);

		verify(memberRepo).save(any(RoomMember.class));
	}
}
