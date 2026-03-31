package com.example.notification.kafka;

import com.example.common.integration.friendship.FriendRequestEvent;
import com.example.common.kafka.event.FriendRequestKafkaEvent;
import com.example.notification.entity.Notification;
import com.example.notification.entity.NotificationType;
import com.example.notification.repository.NotificationRepository;
import com.example.notification.service.impl.NotificationCommandService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FriendRequestEventConsumerTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationCommandService notificationCommandService;

    @InjectMocks
    private FriendRequestEventConsumer consumer;

    @Test
    void createsFriendRequestNotificationWhenNoUnreadDuplicate() {
        UUID sender = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        FriendRequestEvent payload = FriendRequestEvent.builder()
                .senderId(sender)
                .recipientId(recipient)
                .requestId(requestId)
                .type(FriendRequestEvent.Type.SENT)
                .build();

        when(notificationRepository.findFirstByUserIdAndTypeAndReferenceIdAndIsReadFalse(
                recipient,
                NotificationType.FRIEND_REQUEST,
                sender
        )).thenReturn(Optional.empty());

        consumer.listen(FriendRequestKafkaEvent.of("friendship-service", payload));

        verify(notificationCommandService).createNotification(
                eq(recipient),
                eq(NotificationType.FRIEND_REQUEST),
                eq(sender),
                eq(null),
                eq(null),
                eq("New friend request")
        );
    }

    @Test
    void skipsIfUnreadAlreadyExists() {
        UUID sender = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        FriendRequestEvent payload = FriendRequestEvent.builder()
                .senderId(sender)
                .recipientId(recipient)
                .requestId(requestId)
                .type(FriendRequestEvent.Type.SENT)
                .build();

        when(notificationRepository.findFirstByUserIdAndTypeAndReferenceIdAndIsReadFalse(
                recipient,
                NotificationType.FRIEND_REQUEST,
                sender
        )).thenReturn(Optional.of(Notification.builder().id(UUID.randomUUID()).build()));

        consumer.listen(FriendRequestKafkaEvent.of("friendship-service", payload));

        verify(notificationCommandService, never()).createNotification(
                eq(recipient),
                eq(NotificationType.FRIEND_REQUEST),
                eq(sender),
                eq(null),
                eq(null),
                eq("New friend request")
        );
    }
}
