package com.example.notification.controller;

import com.example.notification.dto.NotificationListResponse;
import com.example.notification.dto.NotificationResponse;
import com.example.notification.service.impl.NotificationCommandService;
import com.example.notification.service.impl.NotificationQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationQueryService queryService;

    @Mock
    private NotificationCommandService commandService;

    @InjectMocks
    private NotificationController controller;

    @Test
    void getMyNotifications_returnsResponseEntityWithPayload() {
        UUID userId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .subject(userId.toString())
                .headers(h -> h.put("alg", "none"))
                .claims(c -> c.put("sub", userId.toString()))
                .build();

        NotificationResponse row = NotificationResponse.builder()
                .id(UUID.randomUUID())
                .type("MESSAGE")
                .referenceId(UUID.randomUUID())
                .roomId(UUID.randomUUID())
                .senderName("Alice")
                .preview("hello")
                .isRead(false)
                .createdAt(Instant.now())
                .build();

        NotificationListResponse result = NotificationListResponse.builder()
                .notifications(List.of(row))
                .unreadCount(1)
                .build();

        when(queryService.getNotificationsForUser(userId)).thenReturn(result);

        var response = controller.getMyNotifications(jwt);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().getUnreadCount()).isEqualTo(1);
    }

    @Test
    void markRead_callsCommandService() {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .subject(userId.toString())
                .headers(h -> h.put("alg", "none"))
                .claims(c -> c.put("sub", userId.toString()))
                .build();

        controller.markRead(notificationId, jwt);

        verify(commandService).markRead(notificationId, userId);
    }

    @Test
    void markAllRead_callsCommandService() {
        UUID userId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .subject(userId.toString())
                .headers(h -> h.put("alg", "none"))
                .claims(c -> c.put("sub", userId.toString()))
                .build();

        controller.markAllRead(jwt);

        verify(commandService).markAllRead(userId);
    }
}
