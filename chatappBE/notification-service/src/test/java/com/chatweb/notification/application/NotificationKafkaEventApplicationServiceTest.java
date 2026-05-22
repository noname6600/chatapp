package com.chatweb.notification.application;

import com.chatweb.common.integration.account.AccountCreatedPayload;
import com.chatweb.common.integration.chat.ChatMessagePayload;
import com.chatweb.common.integration.chat.MessageUpdatedPayload;
import com.chatweb.common.integration.friendship.FriendRequestPayload;
import com.chatweb.common.integration.friendship.FriendshipEventType;
import com.chatweb.notification.infrastructure.kafka.NotificationEventDedupeGuard;
import com.chatweb.notification.service.impl.NotificationDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationKafkaEventApplicationServiceTest {

    @Mock
    private NotificationDomainService notificationDomainService;

    @Mock
    private NotificationMessageEventApplicationService notificationMessageEventApplicationService;

    @Mock
    private NotificationReactionEventApplicationService notificationReactionEventApplicationService;

    @Mock
    private NotificationFriendRequestEventApplicationService notificationFriendRequestEventApplicationService;

    @Mock
    private NotificationEventDedupeGuard dedupeGuard;

    private NotificationKafkaEventApplicationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationKafkaEventApplicationService(
                notificationDomainService,
                notificationMessageEventApplicationService,
                notificationReactionEventApplicationService,
            notificationFriendRequestEventApplicationService,
            dedupeGuard
        );
    }

    @Test
    void handleChatMessageSentEvent_dedupesByEventId() {
        UUID eventId = UUID.randomUUID();
        ChatMessagePayload payload = ChatMessagePayload.builder()
                .messageId(UUID.randomUUID())
                .roomId(UUID.randomUUID())
                .senderId(UUID.randomUUID())
                .content("hello")
                .createdAt(Instant.now())
                .build();

            org.mockito.Mockito.when(dedupeGuard.isDuplicate(eventId.toString()))
                .thenReturn(false, true);

        service.handleChatMessageSentEvent(eventId, payload);
        service.handleChatMessageSentEvent(eventId, payload);

        verify(notificationMessageEventApplicationService, times(1)).handle(payload);
    }

    @Test
    void handleFriendRequestEvent_delegatesWithType_andDedupes() {
        UUID eventId = UUID.randomUUID();
        FriendRequestPayload payload = FriendRequestPayload.builder()
                .senderId(UUID.randomUUID())
                .recipientId(UUID.randomUUID())
                .requestId(UUID.randomUUID())
                .createdAt(Instant.now())
                .build();

            org.mockito.Mockito.when(dedupeGuard.isDuplicate(eventId.toString()))
                .thenReturn(false, true);

        service.handleFriendRequestEvent(eventId, FriendshipEventType.FRIEND_REQUEST_SENT.value(), payload);
        service.handleFriendRequestEvent(eventId, FriendshipEventType.FRIEND_REQUEST_SENT.value(), payload);

        verify(notificationFriendRequestEventApplicationService, times(1))
                .handle(FriendshipEventType.FRIEND_REQUEST_SENT.value(), payload);
    }

    @Test
    void handleFriendRequestEvent_skipsWhenEventTypeBlank() {
        FriendRequestPayload payload = FriendRequestPayload.builder()
                .senderId(UUID.randomUUID())
                .recipientId(UUID.randomUUID())
                .requestId(UUID.randomUUID())
                .build();

        service.handleFriendRequestEvent(UUID.randomUUID(), " ", payload);

        verify(notificationFriendRequestEventApplicationService, never()).handle(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void handleAccountCreatedEvent_delegatesWelcomeNotification() {
        AccountCreatedPayload payload = new AccountCreatedPayload(UUID.randomUUID(), "new@example.com");

        service.handleAccountCreatedEvent(payload);

        verify(notificationDomainService).notifyWelcome(payload.getAccountId(), payload.getEmail());
    }

    @Test
    void handleChatMessageEditedEvent_dedupesByEventId() {
        UUID eventId = UUID.randomUUID();
        MessageUpdatedPayload payload = MessageUpdatedPayload.builder()
                .messageId(UUID.randomUUID())
                .roomId(UUID.randomUUID())
                .seq(9L)
                .content("edited")
                .editedAt(Instant.now())
                .build();

        org.mockito.Mockito.when(dedupeGuard.isDuplicate(eventId.toString()))
                .thenReturn(false, true);

        service.handleChatMessageEditedEvent(eventId, payload);
        service.handleChatMessageEditedEvent(eventId, payload);

        verify(dedupeGuard, times(2)).isDuplicate(eventId.toString());
    }
}
