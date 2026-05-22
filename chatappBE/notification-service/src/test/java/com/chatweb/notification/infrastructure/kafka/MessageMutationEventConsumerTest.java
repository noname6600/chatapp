package com.chatweb.notification.infrastructure.kafka;

import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.event.EventMetadata;
import com.chatweb.common.integration.chat.MessageUpdatedPayload;
import com.chatweb.notification.application.NotificationKafkaEventApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MessageMutationEventConsumerTest {

    @Mock
    private NotificationKafkaEventApplicationService applicationService;

    @Test
    void listen_routesEditedEventToApplicationService() {
        MessageMutationEventConsumer consumer = new MessageMutationEventConsumer(applicationService, new ObjectMapper());
        UUID eventId = UUID.randomUUID();
        MessageUpdatedPayload payload = MessageUpdatedPayload.builder()
                .messageId(UUID.randomUUID())
                .roomId(UUID.randomUUID())
                .seq(12L)
                .content("updated")
                .editedAt(Instant.now())
                .build();

        consumer.listen(new EventEnvelope<>(new EventMetadata(eventId.toString(), "chat.message.edited", "chat-service", Instant.now(), eventId.toString()), payload));

        verify(applicationService).handleChatMessageEditedEvent(eventId, payload);
    }
}