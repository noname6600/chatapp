package com.example.chat.modules.room.service.impl;

import com.example.chat.config.InviteCodeGenerator;
import com.example.chat.modules.room.entity.Room;
import com.example.chat.modules.room.entity.RoomMember;
import com.example.chat.modules.room.enums.Role;
import com.example.chat.modules.room.enums.RoomType;
import com.example.chat.modules.room.repository.RoomBanRepository;
import com.example.chat.modules.room.repository.RoomMemberRepository;
import com.example.chat.modules.room.repository.RoomRepository;
import com.example.common.websocket.session.IRoomBroadcaster;
import com.example.chat.modules.message.application.service.ISystemMessageService;
import com.example.common.redis.api.ITimeRedisCacheManager;
import com.example.common.web.exception.BusinessException;
import com.example.common.web.exception.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
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
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

	@Mock
	private RoomRepository roomRepo;
	@Mock
	private RoomMemberRepository memberRepo;
	@Mock
	private RoomBanRepository roomBanRepository;
	@Mock
	private InviteCodeGenerator inviteCodeGenerator;
	@Mock
	private GroupAvatarGenerator avatarGenerator;
	@Mock
	private CloudinaryService cloudinaryService;
	@Mock
	private ITimeRedisCacheManager cacheManager;
	@Mock
	private IRoomBroadcaster roomBroadcaster;
	@Mock
	private ISystemMessageService systemMessageService;

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
		when(roomBanRepository.existsByRoomIdAndUserId(roomId, userId)).thenReturn(false);
		when(memberRepo.save(any(RoomMember.class))).thenAnswer(invocation -> invocation.getArgument(0));

		roomService.joinByInviteRoomId(userId, roomId);

		verify(memberRepo).save(any(RoomMember.class));
	}

	@Test
	void joinByInviteRoomId_isIdempotent_whenSaveHitsUniqueConstraintRace() {
		when(roomRepo.findById(roomId)).thenReturn(Optional.of(Room.builder()
				.id(roomId)
				.type(RoomType.GROUP)
				.build()));
		when(memberRepo.existsByRoomIdAndUserId(roomId, userId)).thenReturn(false);
		when(roomBanRepository.existsByRoomIdAndUserId(roomId, userId)).thenReturn(false);
		doThrow(new DataIntegrityViolationException("duplicate key"))
				.when(memberRepo)
				.save(any(RoomMember.class));

		roomService.joinByInviteRoomId(userId, roomId);

		verify(memberRepo).save(any(RoomMember.class));
	}

	// ---- removeMember tests ----

	@Test
	void removeMember_success_whenOwnerRemovesAnotherMember() {
		UUID ownerId = UUID.randomUUID();
		UUID targetId = UUID.randomUUID();

		RoomMember owner = RoomMember.builder().roomId(roomId).userId(ownerId).role(Role.OWNER).build();
		when(memberRepo.findByRoomIdAndUserId(roomId, ownerId)).thenReturn(Optional.of(owner));

		roomService.removeMember(roomId, ownerId, targetId);

		verify(memberRepo).deleteByRoomIdAndUserId(roomId, targetId);
		verify(roomBroadcaster).sendToRoom(any(), any());
	}

	@Test
	void removeMember_throws_whenCallerIsNotOwner() {
		UUID callerId = UUID.randomUUID();
		UUID targetId = UUID.randomUUID();

		RoomMember member = RoomMember.builder().roomId(roomId).userId(callerId).role(Role.MEMBER).build();
		when(memberRepo.findByRoomIdAndUserId(roomId, callerId)).thenReturn(Optional.of(member));

		assertThatThrownBy(() -> roomService.removeMember(roomId, callerId, targetId))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.FORBIDDEN);

		verify(memberRepo, never()).deleteByRoomIdAndUserId(any(), any());
	}

	@Test
	void removeMember_throws_whenOwnerTriesToRemoveSelf() {
		UUID ownerId = UUID.randomUUID();

		RoomMember owner = RoomMember.builder().roomId(roomId).userId(ownerId).role(Role.OWNER).build();
		when(memberRepo.findByRoomIdAndUserId(roomId, ownerId)).thenReturn(Optional.of(owner));

		assertThatThrownBy(() -> roomService.removeMember(roomId, ownerId, ownerId))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.BAD_REQUEST);

		verify(memberRepo, never()).deleteByRoomIdAndUserId(any(), any());
	}

	@Test
	void banMember_throws_whenOwnerTriesToBanSelf() {
		UUID ownerId = UUID.randomUUID();
		RoomMember owner = RoomMember.builder().roomId(roomId).userId(ownerId).role(Role.OWNER).build();
		when(memberRepo.findByRoomIdAndUserId(roomId, ownerId)).thenReturn(Optional.of(owner));

		assertThatThrownBy(() -> roomService.banMember(roomId, ownerId, ownerId))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.BAD_REQUEST);
	}

	@Test
	void transferOwnership_throws_whenTargetIsNotMember() {
		UUID ownerId = UUID.randomUUID();
		UUID newOwnerId = UUID.randomUUID();
		RoomMember owner = RoomMember.builder().roomId(roomId).userId(ownerId).role(Role.OWNER).build();
		when(memberRepo.findByRoomIdAndUserId(roomId, ownerId)).thenReturn(Optional.of(owner));
		when(memberRepo.findByRoomIdAndUserId(roomId, newOwnerId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> roomService.transferOwnership(roomId, ownerId, newOwnerId))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.BAD_REQUEST);
	}
}
