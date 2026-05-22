package com.chatweb.chat.modules.room.controller;

import com.chatweb.chat.modules.room.service.IPrivateRoomService;
import com.chatweb.chat.modules.room.service.IRoomPinService;
import com.chatweb.chat.modules.room.service.IRoomQueryService;
import com.chatweb.chat.modules.room.service.IRoomService;
import com.chatweb.chat.modules.room.service.RoomMembershipGuard;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoomControllerAuthorizationTest {

    @Test
    void getRoomCode_checksMembershipBeforeReturningCode() {
        IRoomService roomService = mock(IRoomService.class);
        IRoomQueryService roomQueryService = mock(IRoomQueryService.class);
        IPrivateRoomService privateRoomService = mock(IPrivateRoomService.class);
        IRoomPinService roomPinService = mock(IRoomPinService.class);
        RoomMembershipGuard roomMembershipGuard = mock(RoomMembershipGuard.class);

        RoomController controller = new RoomController(
                roomService,
                roomQueryService,
                privateRoomService,
                roomPinService,
                roomMembershipGuard
        );

        UUID roomId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(userId.toString());
        when(roomService.getRoomCode(roomId)).thenReturn("room-code");

        controller.getRoomCode(jwt, roomId);

        verify(roomMembershipGuard).ensureRoomMember(roomId, userId);
        verify(roomService).getRoomCode(roomId);
    }

    @Test
    void getMemberCount_checksMembershipBeforeReturningCount() {
        IRoomService roomService = mock(IRoomService.class);
        IRoomQueryService roomQueryService = mock(IRoomQueryService.class);
        IPrivateRoomService privateRoomService = mock(IPrivateRoomService.class);
        IRoomPinService roomPinService = mock(IRoomPinService.class);
        RoomMembershipGuard roomMembershipGuard = mock(RoomMembershipGuard.class);

        RoomController controller = new RoomController(
                roomService,
                roomQueryService,
                privateRoomService,
                roomPinService,
                roomMembershipGuard
        );

        UUID roomId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(userId.toString());

        controller.getMemberCount(jwt, roomId);

        verify(roomMembershipGuard).ensureRoomMember(roomId, userId);
        verify(roomQueryService).memberCount(roomId);
    }
}
