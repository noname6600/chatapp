package com.example.notification.kafka;

import com.example.common.integration.friendship.FriendRequestEvent;
import com.example.common.integration.kafka.event.FriendRequestKafkaEvent;
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
import java.time.Instant;
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

        @Mock
        private NotificationEventDedupeGuard dedupeGuard;

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
                .senderDisplayName("Sender Name")
                .type(FriendRequestEvent.Type.SENT)
                .build();

        when(notificationRepository.findFirstByUserIdAndTypeAndReferenceIdAndIsReadFalse(
                recipient,
                NotificationType.FRIEND_REQUEST,
                sender
        )).thenReturn(Optional.empty());
        when(dedupeGuard.isDuplicate(org.mockito.ArgumentMatchers.any())).thenReturn(false);

        consumer.listen(FriendRequestKafkaEvent.of("friendship-service", payload));

        verify(notificationCommandService).createNotification(
                eq(recipient),
                eq(NotificationType.FRIEND_REQUEST),
                eq(sender),
                eq(null),
                eq(sender),
                eq("Sender Name"),
                eq(null),
                eq("New friend request"),
                eq(null),
                eq(true)
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
                .senderDisplayName("Sender Name")
                .type(FriendRequestEvent.Type.SENT)
                .build();

        when(notificationRepository.findFirstByUserIdAndTypeAndReferenceIdAndIsReadFalse(
                recipient,
                NotificationType.FRIEND_REQUEST,
                sender
        )).thenReturn(Optional.of(Notification.builder().id(UUID.randomUUID()).build()));
        when(dedupeGuard.isDuplicate(org.mockito.ArgumentMatchers.any())).thenReturn(false);

        consumer.listen(FriendRequestKafkaEvent.of("friendship-service", payload));

        verify(notificationCommandService, never()).createNotification(
                eq(recipient),
                eq(NotificationType.FRIEND_REQUEST),
                eq(sender),
                eq(null),
                eq(sender),
                eq("Sender Name"),
                eq(null),
                eq("New friend request"),
                eq(null),
                eq(true)
        );
    }

    @Test
    void accepted_resolvesStickyRequestForAccepter_andCreatesAcceptedNotificationForOtherSide() {
        UUID accepterUserId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-04-17T10:00:00Z");

        FriendRequestEvent payload = FriendRequestEvent.builder()
                .senderId(accepterUserId)
                .recipientId(otherUserId)
                .requestId(requestId)
                .senderDisplayName("Accepter Name")
                .type(FriendRequestEvent.Type.ACCEPTED)
                .createdAt(createdAt)
                .build();

        Notification sticky = Notification.builder().id(UUID.randomUUID()).build();
        when(notificationRepository.findFirstByUserIdAndTypeAndReferenceIdAndIsReadFalse(
                accepterUserId,
                NotificationType.FRIEND_REQUEST,
                otherUserId
        )).thenReturn(Optional.of(sticky));
        when(dedupeGuard.isDuplicate(org.mockito.ArgumentMatchers.any())).thenReturn(false);

        consumer.listen(FriendRequestKafkaEvent.of("friendship-service", payload));

        verify(notificationCommandService).resolveActionRequired(sticky.getId(), accepterUserId);
        verify(notificationCommandService).createNotification(
                eq(otherUserId),
                eq(NotificationType.FRIEND_REQUEST_ACCEPTED),
                eq(accepterUserId),
                eq(null),
                eq(accepterUserId),
                eq("Accepter Name"),
                eq(null),
                eq("Friend request accepted"),
                eq(createdAt)
        );
    }

    @Test
    void declined_resolvesStickyRequestForOriginalRecipient() {
        UUID sender = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        FriendRequestEvent payload = FriendRequestEvent.builder()
                .senderId(sender)
                .recipientId(recipient)
                .requestId(requestId)
                .senderDisplayName("Sender Name")
                .type(FriendRequestEvent.Type.DECLINED)
                .build();

        Notification sticky = Notification.builder().id(UUID.randomUUID()).build();
        when(notificationRepository.findFirstByUserIdAndTypeAndReferenceIdAndIsReadFalse(
                recipient,
                NotificationType.FRIEND_REQUEST,
                sender
        )).thenReturn(Optional.of(sticky));
        when(dedupeGuard.isDuplicate(org.mockito.ArgumentMatchers.any())).thenReturn(false);

        consumer.listen(FriendRequestKafkaEvent.of("friendship-service", payload));

        verify(notificationCommandService).resolveActionRequired(sticky.getId(), recipient);
    }
}
