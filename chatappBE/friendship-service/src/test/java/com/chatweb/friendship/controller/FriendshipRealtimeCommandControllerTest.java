package com.chatweb.friendship.controller;

import com.chatweb.common.core.exception.BusinessException;
import com.chatweb.friendship.dto.FriendshipRealtimeCommandRequest;
import com.chatweb.friendship.service.IFriendCommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FriendshipRealtimeCommandControllerTest {

    private IFriendCommandService commandService;
    private FriendshipRealtimeCommandController controller;

    @BeforeEach
    void setUp() {
        commandService = mock(IFriendCommandService.class);
        controller = new FriendshipRealtimeCommandController(commandService);
    }

    @Test
    void block_commandInvokesBlockService() {
        UUID userId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        Jwt jwt = jwtFor(userId);

        FriendshipRealtimeCommandRequest request = new FriendshipRealtimeCommandRequest();
        request.setCommand("block");
        request.setTargetUserId(targetUserId);

        controller.handleCommand(jwt, request);

        verify(commandService).block(userId, targetUserId);
    }

    @Test
    void sendRequest_aliasInvokesSendRequestService() {
        UUID userId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        Jwt jwt = jwtFor(userId);

        FriendshipRealtimeCommandRequest request = new FriendshipRealtimeCommandRequest();
        request.setCommand("send-friend-request");
        request.setTargetUserId(targetUserId);

        controller.handleCommand(jwt, request);

        verify(commandService).sendRequest(userId, targetUserId);
    }

    @Test
    void unsupportedCommand_throwsBusinessException() {
        UUID userId = UUID.randomUUID();
        Jwt jwt = jwtFor(userId);

        FriendshipRealtimeCommandRequest request = new FriendshipRealtimeCommandRequest();
        request.setCommand("unsupported");
        request.setTargetUserId(UUID.randomUUID());

        assertThrows(BusinessException.class, () -> controller.handleCommand(jwt, request));
    }

    private Jwt jwtFor(UUID userId) {
        return new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                Map.of("sub", userId.toString())
        );
    }
}
