package com.chatweb.notification.controller;

import com.chatweb.common.core.exception.BusinessException;
import com.chatweb.notification.dto.NotificationRealtimeCommandRequest;
import com.chatweb.notification.service.impl.NotificationCommandService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationRealtimeCommandControllerTest {

    @Mock
    private NotificationCommandService commandService;

    @InjectMocks
    private NotificationRealtimeCommandController controller;

    @Test
    void markRead_forwardsToCommandService() {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .subject(userId.toString())
                .headers(headers -> headers.put("alg", "none"))
                .build();

        NotificationRealtimeCommandRequest request = new NotificationRealtimeCommandRequest();
        request.setCommand("mark-read");
        request.setNotificationId(notificationId);

        var response = controller.handleCommand(jwt, request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(commandService).markRead(notificationId, userId);
    }

    @Test
    void markAllRead_forwardsToCommandService() {
        UUID userId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .subject(userId.toString())
                .headers(headers -> headers.put("alg", "none"))
                .build();

        NotificationRealtimeCommandRequest request = new NotificationRealtimeCommandRequest();
        request.setCommand("mark-all-read");

        var response = controller.handleCommand(jwt, request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(commandService).markAllRead(userId);
    }

    @Test
    void unsupportedCommand_isRejected() {
        UUID userId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .subject(userId.toString())
                .headers(headers -> headers.put("alg", "none"))
                .build();

        NotificationRealtimeCommandRequest request = new NotificationRealtimeCommandRequest();
        request.setCommand("unsupported");

        assertThatThrownBy(() -> controller.handleCommand(jwt, request))
                .isInstanceOf(BusinessException.class);
    }
}
