package com.example.notification.kafka;

import com.example.common.integration.chat.ChatMessagePayload;
import com.example.common.integration.chat.MessageBlockPayload;
import com.example.common.integration.chat.RoomInvitePayload;
import com.example.common.integration.enums.MessageType;
import com.example.common.kafka.event.ChatMessageSentEvent;
import com.example.notification.entity.Notification;
import com.example.notification.entity.NotificationType;
import com.example.notification.entity.RoomMuteSetting;
import com.example.notification.entity.RoomMuteSettingId;
import com.example.notification.entity.RoomNotificationMode;
import com.example.notification.repository.NotificationRepository;
import com.example.notification.repository.RoomMuteSettingRepository;
import com.example.notification.service.impl.NotificationCommandService;
import com.example.notification.service.impl.RoomMuteSettingService;
import com.example.notification.service.NotificationModePolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageCreatedEventConsumerTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private RoomMuteSettingRepository roomMuteSettingRepository;

    @Mock
    private RoomMuteSettingService roomMuteSettingService;

    @Mock
    private NotificationModePolicy notificationModePolicy;

    @Mock
    private NotificationCommandService notificationCommandService;

    @InjectMocks
    private MessageCreatedEventConsumer consumer;

    private UUID messageId;
    private UUID roomId;
    private UUID senderId;
    private UUID recipientId1;
    private UUID recipientId2;
    private Instant createdAt;

    @BeforeEach
    void setUp() {
        messageId = UUID.randomUUID();
        roomId = UUID.randomUUID();
        senderId = UUID.randomUUID();
        recipientId1 = UUID.randomUUID();
        recipientId2 = UUID.randomUUID();
        createdAt = Instant.parse("2026-04-17T10:15:30Z");
    }

    @Test
        void createsMentionInGroupRoomAlsoCreatesGenericMessageForOtherRecipients() {
        ChatMessagePayload payload = ChatMessagePayload.builder()
                .messageId(messageId)
                .roomId(roomId)
                .senderId(senderId)
                .seq(1L)
                .type(MessageType.TEXT)
                .content("Hello @one")
                .preview("Hello @one")
                .createdAt(createdAt)
                .isDirect(false)
                .mentionedUserIds(List.of(recipientId1))
                .recipientUserIds(List.of(recipientId1, recipientId2))
                .build();

        when(roomMuteSettingRepository.findByIdUserIdAndIdRoomId(recipientId1, roomId)).thenReturn(Optional.empty());
        when(roomMuteSettingRepository.findByIdUserIdAndIdRoomId(recipientId2, roomId)).thenReturn(Optional.empty());
        when(notificationModePolicy.shouldDeliverRoomEvent(RoomNotificationMode.NO_RESTRICT, true)).thenReturn(true);
        when(notificationModePolicy.shouldDeliverRoomEvent(RoomNotificationMode.NO_RESTRICT, false)).thenReturn(true);
        when(notificationRepository.findFirstByUserIdAndTypeAndReferenceIdAndIsReadFalse(any(), any(), eq(messageId)))
                .thenReturn(Optional.empty());

        consumer.listen(ChatMessageSentEvent.from("chat-service", payload));

        verify(notificationCommandService).createNotification(
                eq(recipientId1), eq(NotificationType.MENTION), eq(messageId), eq(roomId),
                eq(senderId), eq(null), eq(null), eq("Hello @one"), eq(createdAt));
        verify(notificationCommandService).createNotification(
                eq(recipientId2), eq(NotificationType.MESSAGE), eq(messageId), eq(roomId),
                eq(senderId), eq(null), eq(null), eq("Hello @one"), eq(createdAt));
    }

    @Test
        void createsMentionNotificationForMentionedRecipientInDirectRoomAndMessageForOther() {
        ChatMessagePayload payload = ChatMessagePayload.builder()
                .messageId(messageId)
                .roomId(roomId)
                .senderId(senderId)
                .seq(1L)
                .type(MessageType.TEXT)
                .content("Hello @one")
                .preview("Hello @one")
                .createdAt(createdAt)
                .isDirect(true)
                .mentionedUserIds(List.of(recipientId1))
                .recipientUserIds(List.of(recipientId1, recipientId2))
                .build();

        when(roomMuteSettingRepository.findByIdUserIdAndIdRoomId(recipientId1, roomId)).thenReturn(Optional.empty());
        when(roomMuteSettingRepository.findByIdUserIdAndIdRoomId(recipientId2, roomId)).thenReturn(Optional.empty());
        when(notificationModePolicy.shouldDeliverRoomEvent(RoomNotificationMode.NO_RESTRICT, true)).thenReturn(true);
        when(notificationModePolicy.shouldDeliverRoomEvent(RoomNotificationMode.NO_RESTRICT, false)).thenReturn(true);
        when(notificationRepository.findFirstByUserIdAndTypeAndReferenceIdAndIsReadFalse(eq(recipientId1), any(), eq(messageId)))
                .thenReturn(Optional.empty());

        consumer.listen(ChatMessageSentEvent.from("chat-service", payload));

        verify(notificationCommandService).createNotification(
                eq(recipientId1), eq(NotificationType.MENTION), eq(messageId), eq(roomId),
                eq(senderId), eq(null), eq(null), eq("Hello @one"), eq(createdAt));
        verify(notificationCommandService).createNotification(
                eq(recipientId2), eq(NotificationType.MESSAGE), eq(messageId), eq(roomId),
                eq(senderId), eq(null), eq(null), eq("Hello @one"), eq(createdAt));
    }

    @Test
        void createsGenericMessageForAllGroupRoomRecipients() {
        ChatMessagePayload payload = ChatMessagePayload.builder()
                .messageId(messageId)
                .roomId(roomId)
                .senderId(senderId)
                .seq(1L)
                .type(MessageType.TEXT)
                .content("General group update")
                .preview("General group update")
                .createdAt(createdAt)
                .isDirect(false)
                .mentionedUserIds(List.of())
                .recipientUserIds(List.of(recipientId1, recipientId2))
                .build();

        when(roomMuteSettingRepository.findByIdUserIdAndIdRoomId(any(), eq(roomId))).thenReturn(Optional.empty());
        when(notificationModePolicy.shouldDeliverRoomEvent(RoomNotificationMode.NO_RESTRICT, false)).thenReturn(true);

        consumer.listen(ChatMessageSentEvent.from("chat-service", payload));

        verify(notificationCommandService).createNotification(
                eq(recipientId1), eq(NotificationType.MESSAGE), eq(messageId), eq(roomId),
                eq(senderId), eq(null), eq(null), eq("General group update"), eq(createdAt));
        verify(notificationCommandService).createNotification(
                eq(recipientId2), eq(NotificationType.MESSAGE), eq(messageId), eq(roomId),
                eq(senderId), eq(null), eq(null), eq("General group update"), eq(createdAt));
    }

    @Test
        void createsGenericMessageNotificationInDirectRoom() {
        ChatMessagePayload payload = ChatMessagePayload.builder()
                .messageId(messageId)
                .roomId(roomId)
                .senderId(senderId)
                .seq(1L)
                .type(MessageType.TEXT)
                .content("Hey there")
                .preview("Hey there")
                .createdAt(createdAt)
                .isDirect(true)
                .mentionedUserIds(List.of())
                .recipientUserIds(List.of(recipientId1))
                .build();

        when(roomMuteSettingRepository.findByIdUserIdAndIdRoomId(recipientId1, roomId)).thenReturn(Optional.empty());
        when(notificationModePolicy.shouldDeliverRoomEvent(RoomNotificationMode.NO_RESTRICT, false)).thenReturn(true);

        consumer.listen(ChatMessageSentEvent.from("chat-service", payload));

        verify(notificationCommandService).createNotification(
                eq(recipientId1), eq(NotificationType.MESSAGE), eq(messageId), eq(roomId),
                eq(senderId), eq(null), eq(null), eq("Hey there"), eq(createdAt));
    }

    @Test
    void onlyMentionModeSuppressesNonMentionMessages() {
        ChatMessagePayload payload = ChatMessagePayload.builder()
                .messageId(messageId)
                .roomId(roomId)
                .senderId(senderId)
                .seq(1L)
                .type(MessageType.TEXT)
                .content("General update")
                .preview("General update")
                .createdAt(createdAt)
                .mentionedUserIds(List.of())
                .recipientUserIds(List.of(recipientId1))
                .build();

        RoomMuteSetting setting = RoomMuteSetting.builder()
                .id(new RoomMuteSettingId(recipientId1, roomId))
                .mode(RoomNotificationMode.ONLY_MENTION)
                .build();

        when(roomMuteSettingRepository.findByIdUserIdAndIdRoomId(recipientId1, roomId)).thenReturn(Optional.of(setting));
        when(roomMuteSettingService.resolveMode(setting)).thenReturn(RoomNotificationMode.ONLY_MENTION);
        when(notificationModePolicy.shouldDeliverRoomEvent(RoomNotificationMode.ONLY_MENTION, false)).thenReturn(false);

        consumer.listen(ChatMessageSentEvent.from("chat-service", payload));

        verify(notificationCommandService, never()).createNotification(
                any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void nothingModeSuppressesAllRoomNotifications() {
        ChatMessagePayload payload = ChatMessagePayload.builder()
                .messageId(messageId)
                .roomId(roomId)
                .senderId(senderId)
                .seq(1L)
                .type(MessageType.TEXT)
                .content("Hello @one")
                .preview("Hello @one")
                .createdAt(createdAt)
                .mentionedUserIds(List.of(recipientId1))
                .recipientUserIds(List.of(recipientId1))
                .build();

        RoomMuteSetting setting = RoomMuteSetting.builder()
                .id(new RoomMuteSettingId(recipientId1, roomId))
                .mode(RoomNotificationMode.NOTHING)
                .build();

        when(roomMuteSettingRepository.findByIdUserIdAndIdRoomId(recipientId1, roomId)).thenReturn(Optional.of(setting));
        when(roomMuteSettingService.resolveMode(setting)).thenReturn(RoomNotificationMode.NOTHING);
        when(notificationModePolicy.shouldDeliverRoomEvent(RoomNotificationMode.NOTHING, true)).thenReturn(false);

        consumer.listen(ChatMessageSentEvent.from("chat-service", payload));

        verify(notificationCommandService, never()).createNotification(
                any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void skipsDuplicateMentionNotificationForRecipientInDirectRoom() {
        // Both recipients are mentioned; recipientId1 already has an unread MENTION → skipped.
        // recipientId2 has no existing unread MENTION → notification is created.
        ChatMessagePayload payload = ChatMessagePayload.builder()
                .messageId(messageId)
                .roomId(roomId)
                .senderId(senderId)
                .seq(1L)
                .type(MessageType.TEXT)
                .content("Hello @one @two")
                .preview("Hello @one @two")
                .createdAt(createdAt)
                .isDirect(true)
                .mentionedUserIds(List.of(recipientId1, recipientId2))
                .recipientUserIds(List.of(recipientId1, recipientId2))
                .build();

        when(roomMuteSettingRepository.findByIdUserIdAndIdRoomId(any(), eq(roomId))).thenReturn(Optional.empty());
        when(notificationModePolicy.shouldDeliverRoomEvent(RoomNotificationMode.NO_RESTRICT, true)).thenReturn(true);
        when(notificationRepository.findFirstByUserIdAndTypeAndReferenceIdAndIsReadFalse(recipientId1, NotificationType.MENTION, messageId))
                .thenReturn(Optional.of(Notification.builder().id(UUID.randomUUID()).build()));
        when(notificationRepository.findFirstByUserIdAndTypeAndReferenceIdAndIsReadFalse(recipientId2, NotificationType.MENTION, messageId))
                .thenReturn(Optional.empty());

        consumer.listen(ChatMessageSentEvent.from("chat-service", payload));

        verify(notificationCommandService, never()).createNotification(
                eq(recipientId1), any(), any(), any(), any(), any(), any(), any(), any());
        verify(notificationCommandService, times(1)).createNotification(
                eq(recipientId2), eq(NotificationType.MENTION), eq(messageId), eq(roomId),
                eq(senderId), eq(null), eq(null), eq("Hello @one @two"), eq(createdAt));
    }

    @Test
    void createsReplyNotificationForReplyToAuthorInGroupRoom() {
        UUID replyToMessageId = UUID.randomUUID();

        ChatMessagePayload payload = ChatMessagePayload.builder()
                .messageId(messageId)
                .roomId(roomId)
                .senderId(senderId)
                .senderDisplayName("Bob")
                .seq(2L)
                .type(MessageType.TEXT)
                .content("Replying")
                .preview("Replying")
                .replyToMessageId(replyToMessageId)
                .replyToAuthorId(recipientId1)
                .createdAt(createdAt)
                .isDirect(false)
                .mentionedUserIds(List.of())
                .recipientUserIds(List.of(recipientId1, recipientId2))
                .build();

        when(roomMuteSettingRepository.findByIdUserIdAndIdRoomId(any(), eq(roomId))).thenReturn(Optional.empty());
        when(notificationModePolicy.shouldDeliverRoomEvent(RoomNotificationMode.NO_RESTRICT, false)).thenReturn(true);
        when(notificationRepository.findFirstByUserIdAndTypeAndReferenceIdAndIsReadFalse(any(), any(), eq(messageId)))
                .thenReturn(Optional.empty());

        consumer.listen(ChatMessageSentEvent.from("chat-service", payload));

        // Reply target gets REPLY notification regardless of room type
        verify(notificationCommandService).createNotification(
                eq(recipientId1), eq(NotificationType.REPLY), eq(messageId), eq(roomId),
                eq(senderId), eq("Bob"), eq("Bob"), eq("Replying"), eq(createdAt));

        verify(notificationCommandService).createNotification(
                eq(recipientId2), eq(NotificationType.MESSAGE), eq(messageId), eq(roomId),
                eq(senderId), eq("Bob"), eq("Bob"), eq("Replying"), eq(createdAt));
    }

    @Test
    void inviteCardCreatesGroupInviteNotificationForUnmentionedRecipients() {
        MessageBlockPayload inviteBlock = MessageBlockPayload.builder()
                .type("ROOM_INVITE")
                .roomInvite(RoomInvitePayload.builder()
                        .roomId(roomId)
                        .roomName("Backend")
                        .memberCount(3)
                        .build())
                .build();

        ChatMessagePayload payload = ChatMessagePayload.builder()
                .messageId(messageId)
                .roomId(roomId)
                .senderId(senderId)
                .senderDisplayName("Alice")
                .seq(3L)
                .type(MessageType.TEXT)
                .content("Invite")
                .preview("Invite")
                .createdAt(createdAt)
                .blocks(List.of(inviteBlock))
                .mentionedUserIds(List.of())
                .recipientUserIds(List.of(recipientId1))
                .build();

        when(roomMuteSettingRepository.findByIdUserIdAndIdRoomId(recipientId1, roomId)).thenReturn(Optional.empty());
        when(notificationModePolicy.shouldDeliverRoomEvent(RoomNotificationMode.NO_RESTRICT, false)).thenReturn(true);
        when(notificationRepository.findFirstByUserIdAndTypeAndReferenceIdAndIsReadFalse(recipientId1, NotificationType.GROUP_INVITE, messageId))
                .thenReturn(Optional.empty());

        consumer.listen(ChatMessageSentEvent.from("chat-service", payload));

        verify(notificationCommandService).createNotification(
                eq(recipientId1), eq(NotificationType.GROUP_INVITE), eq(messageId), eq(roomId),
                eq(senderId), eq("Alice"), eq("Alice"), eq("Invite"), eq(createdAt));
    }
}
