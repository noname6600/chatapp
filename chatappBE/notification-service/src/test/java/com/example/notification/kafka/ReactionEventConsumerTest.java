package com.example.notification.kafka;

import com.example.common.integration.chat.ReactionPayload;
import com.example.common.integration.enums.ReactionAction;
import com.example.common.integration.kafka.event.ChatReactionUpdatedEvent;
import com.example.notification.entity.Notification;
import com.example.notification.entity.NotificationType;
import com.example.notification.repository.NotificationRepository;
import com.example.notification.service.impl.NotificationCommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReactionEventConsumerTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationCommandService notificationCommandService;

    @InjectMocks
    private ReactionEventConsumer consumer;

    private UUID messageId;
    private UUID roomId;
    private UUID reactorId;
    private UUID messageAuthorId;
    private Instant createdAt;

    @BeforeEach
    void setUp() {
        messageId = UUID.randomUUID();
        roomId = UUID.randomUUID();
        reactorId = UUID.randomUUID();
        messageAuthorId = UUID.randomUUID();
        createdAt = Instant.parse("2026-04-17T10:15:30Z");
    }

    @Test
    void createsReactionNotificationForMessageAuthor() {
        ReactionPayload payload = ReactionPayload.builder()
                .messageId(messageId)
                .roomId(roomId)
                .userId(reactorId)
                .emoji("👍")
                .action(ReactionAction.ADD)
                .createdAt(createdAt)
                .messageAuthorId(messageAuthorId)
                .actorDisplayName("Alice")
                .build();

        when(notificationRepository.findFirstByUserIdAndTypeAndReferenceIdAndIsReadFalse(
                messageAuthorId, NotificationType.REACTION, messageId))
                .thenReturn(Optional.empty());

        consumer.listen(ChatReactionUpdatedEvent.from("chat-service", payload));

        verify(notificationCommandService).createNotification(
                eq(messageAuthorId),
                eq(NotificationType.REACTION),
                eq(messageId),
                eq(roomId),
                eq(reactorId),
                eq("Alice"),
                eq(null),
                eq("reacted 👍 to your message"),
                eq(createdAt)
        );
    }

    @Test
    void suppressesSelfReaction() {
        ReactionPayload payload = ReactionPayload.builder()
                .messageId(messageId)
                .roomId(roomId)
                .userId(messageAuthorId)  // reactor == author
                .emoji("👍")
                .action(ReactionAction.ADD)
                .createdAt(createdAt)
                .messageAuthorId(messageAuthorId)
                .actorDisplayName("Alice")
                .build();

        consumer.listen(ChatReactionUpdatedEvent.from("chat-service", payload));

        verify(notificationCommandService, never()).createNotification(
                any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void suppressesReactionRemoval() {
        ReactionPayload payload = ReactionPayload.builder()
                .messageId(messageId)
                .roomId(roomId)
                .userId(reactorId)
                .emoji("👍")
                .action(ReactionAction.REMOVE)
                .createdAt(createdAt)
                .messageAuthorId(messageAuthorId)
                .actorDisplayName("Alice")
                .build();

        consumer.listen(ChatReactionUpdatedEvent.from("chat-service", payload));

        verify(notificationCommandService, never()).createNotification(
                any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void suppressesDuplicateUnreadReactionNotification() {
        ReactionPayload payload = ReactionPayload.builder()
                .messageId(messageId)
                .roomId(roomId)
                .userId(reactorId)
                .emoji("❤️")
                .action(ReactionAction.ADD)
                .createdAt(createdAt)
                .messageAuthorId(messageAuthorId)
                .actorDisplayName("Bob")
                .build();

        when(notificationRepository.findFirstByUserIdAndTypeAndReferenceIdAndIsReadFalse(
                messageAuthorId, NotificationType.REACTION, messageId))
                .thenReturn(Optional.of(Notification.builder().id(UUID.randomUUID()).build()));

        consumer.listen(ChatReactionUpdatedEvent.from("chat-service", payload));

        verify(notificationCommandService, never()).createNotification(
                any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void suppressesEventWhenMessageAuthorIdMissing() {
        ReactionPayload payload = ReactionPayload.builder()
                .messageId(messageId)
                .roomId(roomId)
                .userId(reactorId)
                .emoji("👍")
                .action(ReactionAction.ADD)
                .createdAt(createdAt)
                .messageAuthorId(null)
                .actorDisplayName(null)
                .build();

        consumer.listen(ChatReactionUpdatedEvent.from("chat-service", payload));

        verify(notificationCommandService, never()).createNotification(
                any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void createsNotificationWithNullActorDisplayNameWhenMissing() {
        ReactionPayload payload = ReactionPayload.builder()
                .messageId(messageId)
                .roomId(roomId)
                .userId(reactorId)
                .emoji("🔥")
                .action(ReactionAction.ADD)
                .createdAt(createdAt)
                .messageAuthorId(messageAuthorId)
                .actorDisplayName(null)
                .build();

        when(notificationRepository.findFirstByUserIdAndTypeAndReferenceIdAndIsReadFalse(
                messageAuthorId, NotificationType.REACTION, messageId))
                .thenReturn(Optional.empty());

        consumer.listen(ChatReactionUpdatedEvent.from("chat-service", payload));

        verify(notificationCommandService).createNotification(
                eq(messageAuthorId),
                eq(NotificationType.REACTION),
                eq(messageId),
                eq(roomId),
                eq(reactorId),
                eq(null),
                eq(null),
                eq("reacted 🔥 to your message"),
                eq(createdAt)
        );
    }
}
