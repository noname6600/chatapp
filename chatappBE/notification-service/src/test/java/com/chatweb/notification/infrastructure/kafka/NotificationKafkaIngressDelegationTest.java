package com.chatweb.notification.infrastructure.kafka;

import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.event.EventMetadata;
import com.chatweb.common.integration.chat.ChatMessagePayload;
import com.chatweb.common.integration.chat.ReactionPayload;
import com.chatweb.common.integration.friendship.FriendRequestPayload;
import com.chatweb.notification.application.NotificationKafkaEventApplicationService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificationKafkaIngressDelegationTest {

    @Test
    void messageConsumer_delegatesToApplicationService() {
        NotificationKafkaEventApplicationService applicationService = mock(NotificationKafkaEventApplicationService.class);
        MessageCreatedEventConsumer consumer = new MessageCreatedEventConsumer(applicationService);

        EventEnvelope<ChatMessagePayload> envelope = new EventEnvelope<>(
                metadata("chat.message.sent"),
                ChatMessagePayload.builder().build()
        );

        consumer.listen(envelope);

        verify(applicationService).handleChatMessageSentEvent(
                envelope.metadata().getEventId(),
                envelope.payload()
        );
    }

    @Test
    void reactionConsumer_delegatesToApplicationService() {
        NotificationKafkaEventApplicationService applicationService = mock(NotificationKafkaEventApplicationService.class);
        ReactionEventConsumer consumer = new ReactionEventConsumer(applicationService);

        EventEnvelope<ReactionPayload> envelope = new EventEnvelope<>(
                metadata("chat.reaction.updated"),
                ReactionPayload.builder().build()
        );

        consumer.listen(envelope);

        verify(applicationService).handleReactionUpdatedEvent(
                envelope.metadata().getEventId(),
                envelope.payload()
        );
    }

    @Test
    void friendRequestConsumer_delegatesToApplicationService() {
        NotificationKafkaEventApplicationService applicationService = mock(NotificationKafkaEventApplicationService.class);
        FriendRequestEventConsumer consumer = new FriendRequestEventConsumer(applicationService);

        EventEnvelope<FriendRequestPayload> envelope = new EventEnvelope<>(
                metadata("friend.request.sent"),
                FriendRequestPayload.builder().build()
        );

        consumer.listen(envelope);

        verify(applicationService).handleFriendRequestEvent(
                envelope.metadata().getEventId(),
                envelope.metadata().getEventType(),
                envelope.payload()
        );
    }

    private EventMetadata metadata(String eventType) {
        String eventId = UUID.randomUUID().toString();
        return new EventMetadata(
                eventId,
                eventType,
                "notification-service",
                Instant.now(),
                eventId
        );
    }
}
