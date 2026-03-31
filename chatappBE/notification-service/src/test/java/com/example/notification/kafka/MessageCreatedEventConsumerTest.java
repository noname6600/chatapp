package com.example.notification.kafka;

import com.example.common.integration.chat.ChatMessagePayload;
import com.example.common.integration.enums.MessageType;
import com.example.common.kafka.event.ChatMessageSentEvent;
import com.example.notification.entity.Notification;
import com.example.notification.entity.NotificationType;
import com.example.notification.entity.RoomMuteSetting;
import com.example.notification.entity.RoomMuteSettingId;
import com.example.notification.repository.NotificationRepository;
import com.example.notification.repository.RoomMuteSettingRepository;
import com.example.notification.service.impl.NotificationCommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MessageCreatedEventConsumerTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private RoomMuteSettingRepository roomMuteSettingRepository;

    @Mock
    private NotificationCommandService notificationCommandService;

    @InjectMocks
    private MessageCreatedEventConsumer consumer;

    private UUID messageId;
    private UUID roomId;
    private UUID senderId;
    private UUID recipientId1;
    private UUID recipientId2;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        messageId = UUID.randomUUID();
        roomId = UUID.randomUUID();
        senderId = UUID.randomUUID();
        recipientId1 = UUID.randomUUID();
        recipientId2 = UUID.randomUUID();
    }

    @Test
        void MessageCreatedEventConsumer_createsMessageNotificationsExcludingSender() {
        // Arrange
        ChatMessagePayload payload = ChatMessagePayload.builder()
                .messageId(messageId)
                .roomId(roomId)
                .senderId(senderId)
                .seq(1L)
                .type(MessageType.TEXT)
                .content("Hello everyone")
                .preview("Hello everyone")
                .createdAt(Instant.now())
                .mentionedUserIds(List.of())
                .recipientUserIds(List.of(recipientId1, recipientId2))
                .build();

        ChatMessageSentEvent event = ChatMessageSentEvent.from("chat-service", payload);

        when(roomMuteSettingRepository.findByIdUserIdAndIdRoomId(anyUUID(), anyUUID()))
                .thenReturn(Optional.empty());
        when(notificationRepository.findFirstByUserIdAndTypeAndReferenceIdAndIsReadFalse(anyUUID(), any(), anyUUID()))
                .thenReturn(Optional.empty());

        // Act
        consumer.listen(event);

        // Assert
        verify(notificationCommandService, times(2))
                .createNotification(anyUUID(), eq(NotificationType.MESSAGE), eq(messageId), eq(roomId), anyString(), anyString());
        verify(notificationCommandService).createNotification(recipientId1, NotificationType.MESSAGE, messageId, roomId, senderId.toString(), "Hello everyone");
        verify(notificationCommandService).createNotification(recipientId2, NotificationType.MESSAGE, messageId, roomId, senderId.toString(), "Hello everyone");
    }

    @Test
    void MessageCreatedEventConsumer_createsMentionNotificationInsteadOfMessageForMentionedUser() {
        ChatMessagePayload payload = ChatMessagePayload.builder()
                .messageId(messageId)
                .roomId(roomId)
                .senderId(senderId)
                .seq(1L)
                .type(MessageType.TEXT)
                .content("Hello @user")
                .preview("Hello @user")
                .createdAt(Instant.now())
                .mentionedUserIds(List.of(recipientId1))
                .recipientUserIds(List.of(recipientId1, recipientId2))
                .build();

        ChatMessageSentEvent event = ChatMessageSentEvent.from("chat-service", payload);

        when(roomMuteSettingRepository.findByIdUserIdAndIdRoomId(anyUUID(), anyUUID()))
                .thenReturn(Optional.empty());
        when(notificationRepository.findFirstByUserIdAndTypeAndReferenceIdAndIsReadFalse(anyUUID(), any(), anyUUID()))
                .thenReturn(Optional.empty());

        consumer.listen(event);

        verify(notificationCommandService).createNotification(recipientId1, NotificationType.MENTION, messageId, roomId, senderId.toString(), "Hello @user");
        verify(notificationCommandService).createNotification(recipientId2, NotificationType.MESSAGE, messageId, roomId, senderId.toString(), "Hello @user");
        verify(notificationCommandService, never()).createNotification(
                eq(recipientId2),
                eq(NotificationType.MENTION),
                eq(messageId),
                eq(roomId),
                anyString(),
                anyString());
    }

    @Test
    void MessageCreatedEventConsumer_doesNotCreateMentionNotificationsForNonMentionedRecipients() {
        ChatMessagePayload payload = ChatMessagePayload.builder()
                .messageId(messageId)
                .roomId(roomId)
                .senderId(senderId)
                .seq(1L)
                .type(MessageType.TEXT)
                .content("General update")
                .preview("General update")
                .createdAt(Instant.now())
                .mentionedUserIds(List.of())
                .recipientUserIds(List.of(recipientId1, recipientId2))
                .build();

        ChatMessageSentEvent event = ChatMessageSentEvent.from("chat-service", payload);

        when(roomMuteSettingRepository.findByIdUserIdAndIdRoomId(anyUUID(), anyUUID()))
                .thenReturn(Optional.empty());
        when(notificationRepository.findFirstByUserIdAndTypeAndReferenceIdAndIsReadFalse(anyUUID(), any(), anyUUID()))
                .thenReturn(Optional.empty());

        consumer.listen(event);

        verify(notificationCommandService, never()).createNotification(
                anyUUID(),
                eq(NotificationType.MENTION),
                eq(messageId),
                eq(roomId),
                anyString(),
                anyString());
        verify(notificationCommandService, times(2)).createNotification(
                anyUUID(),
                eq(NotificationType.MESSAGE),
                eq(messageId),
                eq(roomId),
                anyString(),
                anyString());
    }

    @Test
    void listen_skipsNotificationIfRoomIsMuted() {
        // Arrange
        ChatMessagePayload payload = ChatMessagePayload.builder()
                .messageId(messageId)
                .roomId(roomId)
                .senderId(senderId)
                .seq(1L)
                .type(MessageType.TEXT)
                .content("Hello everyone")
                .preview("Hello everyone")
                .createdAt(Instant.now())
                .mentionedUserIds(List.of())
                .recipientUserIds(List.of(recipientId1, recipientId2))
                .build();

        ChatMessageSentEvent event = ChatMessageSentEvent.from("chat-service", payload);

        // Mute room for recipientId1
        when(roomMuteSettingRepository.findByIdUserIdAndIdRoomId(recipientId1, roomId))
                .thenReturn(Optional.of(new RoomMuteSetting(new RoomMuteSettingId(recipientId1, roomId), Instant.now())));
        when(roomMuteSettingRepository.findByIdUserIdAndIdRoomId(recipientId2, roomId))
                .thenReturn(Optional.empty());
        when(notificationRepository.findFirstByUserIdAndTypeAndReferenceIdAndIsReadFalse(anyUUID(), any(), anyUUID()))
                .thenReturn(Optional.empty());

        // Act
        consumer.listen(event);

        // Assert - only recipientId2 should get notification since recipientId1 has room muted
        verify(notificationCommandService, times(1))
                .createNotification(recipientId2, NotificationType.MESSAGE, messageId, roomId, senderId.toString(), "Hello everyone");
        verify(notificationCommandService, never())
                .createNotification(eq(recipientId1), any(), any(), any(), anyString(), anyString());
    }

    @Test
    void listen_skipsIfUnreadMessageNotificationAlreadyExists() {
        // Arrange
        ChatMessagePayload payload = ChatMessagePayload.builder()
                .messageId(messageId)
                .roomId(roomId)
                .senderId(senderId)
                .seq(1L)
                .type(MessageType.TEXT)
                .content("Hello everyone")
                .preview("Hello everyone")
                .createdAt(Instant.now())
                .mentionedUserIds(List.of())
                .recipientUserIds(List.of(recipientId1, recipientId2))
                .build();

        ChatMessageSentEvent event = ChatMessageSentEvent.from("chat-service", payload);

        // Mock: unread notification already exists for recipientId1
        Notification mockNotification = Notification.builder()
                .id(UUID.randomUUID())
                .userId(recipientId1)
                .type(NotificationType.MESSAGE)
                .referenceId(messageId)
                .roomId(roomId)
                .isRead(false)
                .build();

        when(roomMuteSettingRepository.findByIdUserIdAndIdRoomId(anyUUID(), anyUUID()))
                .thenReturn(Optional.empty());
        when(notificationRepository.findFirstByUserIdAndTypeAndReferenceIdAndIsReadFalse(recipientId1, NotificationType.MESSAGE, messageId))
                .thenReturn(Optional.of(mockNotification));
        when(notificationRepository.findFirstByUserIdAndTypeAndReferenceIdAndIsReadFalse(recipientId2, NotificationType.MESSAGE, messageId))
                .thenReturn(Optional.empty());

        // Act
        consumer.listen(event);

        // Assert - only recipientId2 should get notification since recipientId1 already has unread
        verify(notificationCommandService, times(1))
                .createNotification(recipientId2, NotificationType.MESSAGE, messageId, roomId, senderId.toString(), "Hello everyone");
        verify(notificationCommandService, never())
                .createNotification(eq(recipientId1), any(), any(), any(), anyString(), anyString());
    }

    @Test
    void listen_ignoresNullEvent() {
        // Act & Assert - should not throw
        consumer.listen(null);

        verify(notificationCommandService, never())
                .createNotification(any(), any(), any(), any(), anyString(), anyString());
    }

    @Test
    void listen_ignoresEventWithNullPayload() {
        // Arrange
        ChatMessageSentEvent event = new ChatMessageSentEvent("chat-service", null, null, null);

        // Act & Assert - should not throw
        consumer.listen(event);

        verify(notificationCommandService, never())
                .createNotification(any(), any(), any(), any(), anyString(), anyString());
    }

    @Test
    void listen_ignoresEventWithEmptyRecipients() {
        // Arrange
        ChatMessagePayload payload = ChatMessagePayload.builder()
                .messageId(messageId)
                .roomId(roomId)
                .senderId(senderId)
                .seq(1L)
                .type(MessageType.TEXT)
                .content("Hello")
                .createdAt(Instant.now())
                .mentionedUserIds(List.of())
                .recipientUserIds(List.of())
                .build();

        ChatMessageSentEvent event = ChatMessageSentEvent.from("chat-service", payload);

        // Act
        consumer.listen(event);

        // Assert
        verify(notificationCommandService, never())
                .createNotification(any(), any(), any(), any(), anyString(), anyString());
    }

    @Test
    void listen_handlesContinueOnNotificationCreationError() {
        // Arrange
        ChatMessagePayload payload = ChatMessagePayload.builder()
                .messageId(messageId)
                .roomId(roomId)
                .senderId(senderId)
                .seq(1L)
                .type(MessageType.TEXT)
                .content("Hello everyone")
                .preview("Hello everyone")
                .createdAt(Instant.now())
                .mentionedUserIds(List.of())
                .recipientUserIds(List.of(recipientId1, recipientId2))
                .build();

        ChatMessageSentEvent event = ChatMessageSentEvent.from("chat-service", payload);

        when(roomMuteSettingRepository.findByIdUserIdAndIdRoomId(anyUUID(), anyUUID()))
                .thenReturn(Optional.empty());
        when(notificationRepository.findFirstByUserIdAndTypeAndReferenceIdAndIsReadFalse(anyUUID(), any(), anyUUID()))
                .thenReturn(Optional.empty());

        // Mock: first call throws, second succeeds
        when(notificationCommandService.createNotification(
                eq(recipientId1), any(), any(), any(), anyString(), anyString()))
                .thenThrow(new RuntimeException("DB error"));
        when(notificationCommandService.createNotification(
                eq(recipientId2), any(), any(), any(), anyString(), anyString()))
                .thenReturn(Notification.builder().id(UUID.randomUUID()).build());

        // Act & Assert - should not throw, should continue processing
        consumer.listen(event);

        // Both should still be called despite first one throwing
        verify(notificationCommandService, times(2))
                .createNotification(anyUUID(), eq(NotificationType.MESSAGE), eq(messageId), eq(roomId), anyString(), anyString());
    }

    // Helper method for any UUID matcher
    private static UUID anyUUID() {
        return any(UUID.class);
    }
}
