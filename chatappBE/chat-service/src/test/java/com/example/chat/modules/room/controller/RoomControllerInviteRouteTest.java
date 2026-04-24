package com.example.chat.modules.room.controller;

import com.example.chat.modules.room.service.IPrivateRoomService;
import com.example.chat.modules.room.service.IRoomPinService;
import com.example.chat.modules.room.service.IRoomQueryService;
import com.example.chat.modules.room.service.IRoomService;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoomControllerInviteRouteTest {

    @Test
    void joinByLegacyInviteRoute_delegatesToJoinByInvite() {
        IRoomService roomService = mock(IRoomService.class);
        IRoomQueryService roomQueryService = mock(IRoomQueryService.class);
        IPrivateRoomService privateRoomService = mock(IPrivateRoomService.class);
        IRoomPinService roomPinService = mock(IRoomPinService.class);

        RoomController controller = new RoomController(roomService, roomQueryService, privateRoomService, roomPinService);

        UUID roomId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        org.springframework.security.oauth2.jwt.Jwt jwt = mock(org.springframework.security.oauth2.jwt.Jwt.class);
        when(jwt.getSubject()).thenReturn(currentUserId.toString());

        controller.joinByLegacyInviteRoute(jwt, roomId, UUID.randomUUID());

        verify(roomService).joinByInviteRoomId(currentUserId, roomId);
    }

    @Test
    void roomController_declaresLegacyInvitePostMapping() throws Exception {
        Method method = RoomController.class.getDeclaredMethod(
                "joinByLegacyInviteRoute",
                org.springframework.security.oauth2.jwt.Jwt.class,
                UUID.class,
                UUID.class
        );

        PostMapping postMapping = method.getAnnotation(PostMapping.class);
        assertThat(postMapping).isNotNull();
        assertThat(Arrays.asList(postMapping.value())).contains("/{roomId}/invite");
    }
}
