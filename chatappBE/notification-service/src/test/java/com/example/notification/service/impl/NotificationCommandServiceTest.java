package com.example.notification.service.impl;

import com.example.common.core.exception.BusinessException;
import com.example.notification.entity.Notification;
import com.example.notification.entity.NotificationType;
import com.example.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationCommandServiceTest {

    @Mock
    private NotificationRepository repository;

    @Mock
    private NotificationPushService pushService;

    @InjectMocks
    private NotificationCommandService service;

    @Test
    void markRead_throwsForbiddenWhenNotificationOwnedByAnotherUser() {
        UUID notificationId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        Notification notification = Notification.builder()
                .id(notificationId)
                .userId(ownerId)
                .type(NotificationType.MESSAGE)
                .isRead(false)
                .createdAt(Instant.now())
                .build();

        when(repository.findById(notificationId)).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> service.markRead(notificationId, otherUserId))
                .isInstanceOf(BusinessException.class);

        verify(pushService, never()).pushUnreadCount(otherUserId);
    }

    @Test
    void trimToLimit_keepsNewest200() {
        UUID userId = UUID.randomUUID();

        List<Notification> oldestFirst = IntStream.range(0, 205)
                .mapToObj(i -> Notification.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .type(NotificationType.MESSAGE)
                        .isRead(i % 2 == 0)
                        .createdAt(Instant.EPOCH.plusSeconds(i))
                        .build())
                .toList();

        when(repository.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(oldestFirst);

        service.trimToLimit(userId, 200);

        ArgumentCaptor<List<Notification>> deletedCaptor = ArgumentCaptor.forClass(List.class);
        verify(repository).deleteAllInBatch(deletedCaptor.capture());

        List<Notification> deleted = deletedCaptor.getValue();
        assertThat(deleted).hasSize(5);
        assertThat(deleted).containsExactlyElementsOf(oldestFirst.subList(0, 5));
    }

    @Test
    void markAllRead_usesAtomicRepositoryUpdateAndPushesUnread() {
        UUID userId = UUID.randomUUID();
        when(repository.markAllReadByUserId(userId)).thenReturn(3);

        service.markAllRead(userId);

        verify(repository).markAllReadByUserId(userId);
        verify(pushService).pushUnreadCount(userId);
    }

    @Test
    void clearRoom_callsRepositoryAndPushesUnreadCount() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        when(repository.markReadByUserIdAndRoomId(userId, roomId)).thenReturn(2);

        service.clearRoom(userId, roomId);

        verify(repository).markReadByUserIdAndRoomId(userId, roomId);
        verify(pushService).pushUnreadCount(userId);
    }
}

