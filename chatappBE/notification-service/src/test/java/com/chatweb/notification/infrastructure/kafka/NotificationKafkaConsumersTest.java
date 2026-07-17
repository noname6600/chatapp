package com.chatweb.notification.infrastructure.kafka;

import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.event.EventMetadata;
import com.chatweb.common.integration.chat.ChatMessagePayload;
import com.chatweb.common.integration.chat.ReactionPayload;
import com.chatweb.common.integration.friendship.FriendRequestPayload;
import com.chatweb.notification.application.NotificationKafkaEventApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationKafkaConsumersTest {

    @Mock
    private NotificationKafkaEventApplicationService applicationService;

    private MessageCreatedEventConsumer messageConsumer;
    private ReactionEventConsumer reactionConsumer;
    private FriendRequestEventConsumer friendRequestConsumer;

    @BeforeEach
    void setUp() {
        messageConsumer = new MessageCreatedEventConsumer(applicationService);
        reactionConsumer = new ReactionEventConsumer(applicationService);
        friendRequestConsumer = new FriendRequestEventConsumer(applicationService);
    }

    // --- MessageCreatedEventConsumer ---

    @Test
    void messageConsumer_delegatesToApplicationService() {
        UUID eventId = UUID.randomUUID();
        ChatMessagePayload payload = buildChatMessagePayload();
        EventEnvelope<ChatMessagePayload> envelope = buildEnvelope(eventId.toString(), "chat.message.sent", payload);

        messageConsumer.listen(envelope);

        ArgumentCaptor<String> idCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ChatMessagePayload> payloadCaptor = ArgumentCaptor.forClass(ChatMessagePayload.class);
        verify(applicationService).handleChatMessageSentEvent(idCaptor.capture(), payloadCaptor.capture());
        assertThat(idCaptor.getValue()).isEqualTo(eventId.toString());
        assertThat(payloadCaptor.getValue()).isSameAs(payload);
    }

    @Test
    void messageConsumer_nullEnvelope_doesNotDelegate() {
        messageConsumer.listen(null);
        verifyNoInteractions(applicationService);
    }

    @Test
    void messageConsumer_nullPayload_doesNotDelegate() {
        EventEnvelope<ChatMessagePayload> envelope = buildEnvelope(UUID.randomUUID().toString(), "chat.message.sent", null);
        messageConsumer.listen(envelope);
        verifyNoInteractions(applicationService);
    }

    // --- ReactionEventConsumer ---

    @Test
    void reactionConsumer_delegatesToApplicationService() {
        UUID eventId = UUID.randomUUID();
        ReactionPayload payload = ReactionPayload.builder()
                .messageId(UUID.randomUUID())
                .roomId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .emoji(":thumbsup:")
                .build();
        EventEnvelope<ReactionPayload> envelope = buildEnvelope(eventId.toString(), "chat.reaction.updated", payload);

        reactionConsumer.listen(envelope);

        ArgumentCaptor<String> idCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ReactionPayload> payloadCaptor = ArgumentCaptor.forClass(ReactionPayload.class);
        verify(applicationService).handleReactionUpdatedEvent(idCaptor.capture(), payloadCaptor.capture());
        assertThat(idCaptor.getValue()).isEqualTo(eventId.toString());
        assertThat(payloadCaptor.getValue()).isSameAs(payload);
    }

    @Test
    void reactionConsumer_nullEnvelope_doesNotDelegate() {
        reactionConsumer.listen(null);
        verifyNoInteractions(applicationService);
    }

    // --- FriendRequestEventConsumer ---

    @Test
    void friendRequestConsumer_delegatesToApplicationService() {
        UUID eventId = UUID.randomUUID();
        FriendRequestPayload payload = FriendRequestPayload.builder()
                .senderId(UUID.randomUUID())
                .recipientId(UUID.randomUUID())
                .requestId(UUID.randomUUID())
                .build();
        EventEnvelope<FriendRequestPayload> envelope = buildEnvelope(
                eventId.toString(), "friend.request.sent", payload);

        friendRequestConsumer.listen(envelope);

        ArgumentCaptor<String> idCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> typeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<FriendRequestPayload> payloadCaptor = ArgumentCaptor.forClass(FriendRequestPayload.class);
        verify(applicationService).handleFriendRequestEvent(idCaptor.capture(), typeCaptor.capture(), payloadCaptor.capture());
        assertThat(idCaptor.getValue()).isEqualTo(eventId.toString());
        assertThat(typeCaptor.getValue()).isEqualTo("friend.request.sent");
        assertThat(payloadCaptor.getValue()).isSameAs(payload);
    }

    @Test
    void friendRequestConsumer_nullEnvelope_doesNotDelegate() {
        friendRequestConsumer.listen(null);
        verifyNoInteractions(applicationService);
    }

    // --- helpers ---

    private <T> EventEnvelope<T> buildEnvelope(String eventId, String eventType, T payload) {
        return new EventEnvelope<>(
                new EventMetadata(eventId, eventType, "test-service", Instant.now(), eventId),
                payload
        );
    }

    private ChatMessagePayload buildChatMessagePayload() {
        return ChatMessagePayload.builder()
                .messageId(UUID.randomUUID())
                .roomId(UUID.randomUUID())
                .senderId(UUID.randomUUID())
                .content("Hello")
                .build();
    }
}
