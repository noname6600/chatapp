package com.example.chat.modules.message.infrastructure.kafka;

import com.example.chat.modules.message.infrastructure.redis.ChatRedisPublisher;
import com.example.chat.realtime.subscriber.RealtimeEventDedupeGuard;
import com.example.common.integration.chat.ChatMessagePayload;
import com.example.common.integration.chat.ReactionPayload;
import com.example.common.integration.enums.MessageType;
import com.example.common.integration.enums.ReactionAction;
import com.example.common.integration.kafka.event.ChatMessageSentEvent;
import com.example.common.integration.kafka.event.ChatReactionUpdatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaRealtimeCorrelationPropagationTest {

    @Mock
    private ChatRedisPublisher chatRedisPublisher;

    @Mock
    private RealtimeEventDedupeGuard dedupeGuard;

    @InjectMocks
    private KafkaChatMessageEventConsumer chatMessageEventConsumer;

    @InjectMocks
    private KafkaReactionEventConsumer reactionEventConsumer;

    @BeforeEach
    void setUp() {
        when(dedupeGuard.isDuplicate(org.mockito.ArgumentMatchers.any())).thenReturn(false);
    }

    @Test
    void chatMessageEvent_preservesEventIdAndCorrelationIdWhenRepublishingToRedis() {
        ChatMessagePayload payload = ChatMessagePayload.builder()
                .messageId(UUID.randomUUID())
                .roomId(UUID.randomUUID())
                .senderId(UUID.randomUUID())
                .type(MessageType.TEXT)
                .content("hello")
                .createdAt(Instant.now())
                .build();

        ChatMessageSentEvent event = ChatMessageSentEvent.from("chat-service", payload);

        chatMessageEventConsumer.onMessageSent(event);

        verify(chatRedisPublisher).publishMessageSent(
                eq(payload),
                eq(event.getEventId().toString()),
                eq(event.getCorrelationId())
        );
    }

    @Test
    void reactionEvent_preservesEventIdAndCorrelationIdWhenRepublishingToRedis() {
        ReactionPayload payload = ReactionPayload.builder()
                .messageId(UUID.randomUUID())
                .roomId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .emoji(":thumbsup:")
                .action(ReactionAction.ADD)
                .createdAt(Instant.now())
                .messageAuthorId(UUID.randomUUID())
                .build();

        ChatReactionUpdatedEvent event = ChatReactionUpdatedEvent.from("chat-service", payload);

        reactionEventConsumer.onReactionUpdated(event);

        verify(chatRedisPublisher).publishReactionUpdated(
                eq(payload),
                eq(event.getEventId().toString()),
                eq(event.getCorrelationId())
        );
    }

    @Test
    void duplicateKafkaEvent_isDroppedWithoutRedisRepublish() {
        ChatMessagePayload payload = ChatMessagePayload.builder()
                .messageId(UUID.randomUUID())
                .roomId(UUID.randomUUID())
                .senderId(UUID.randomUUID())
                .type(MessageType.TEXT)
                .content("dup")
                .createdAt(Instant.now())
                .build();

        ChatMessageSentEvent event = ChatMessageSentEvent.from("chat-service", payload);
        when(dedupeGuard.isDuplicate(event.getEventId())).thenReturn(true);

        chatMessageEventConsumer.onMessageSent(event);

        verify(chatRedisPublisher, never()).publishMessageSent(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }
}
