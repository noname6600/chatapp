package com.chatweb.chat.realtime.infrastructure;

import com.chatweb.chat.modules.message.infrastructure.redis.ChatRedisPublisher;
import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.integration.chat.ChatEventType;
import com.chatweb.common.integration.chat.MessagePinPayload;
import com.chatweb.common.kafka.producer.KafkaEventPublisher;
import com.chatweb.common.realtime.policy.RealtimeFlowId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatRealtimeAdapterTest {

    @Mock
    private ChatRedisPublisher chatRedisPublisher;

    @Mock
    private KafkaEventPublisher kafkaEventPublisher;

    @InjectMocks
    private ChatRealtimeAdapter adapter;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(adapter, "sourceService", "chat-service");
    }

    @Test
    void publishRoomEvent_durablePinFlowPublishesToKafkaWithRoomKey() {
        UUID roomId = UUID.randomUUID();
        MessagePinPayload payload = MessagePinPayload.builder()
                .roomId(roomId)
                .messageId(UUID.randomUUID())
                .actorUserId(UUID.randomUUID())
                .pinnedAt(Instant.now())
                .build();

        adapter.publishRoomEvent(roomId, ChatEventType.MESSAGE_PINNED.value(), payload, RealtimeFlowId.CHAT_MESSAGE_PIN);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<EventEnvelope> envelopeCaptor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(kafkaEventPublisher).publish(topicCaptor.capture(), keyCaptor.capture(), envelopeCaptor.capture());
        assertThat(topicCaptor.getValue()).isEqualTo("chat.message.events");
        assertThat(keyCaptor.getValue()).isEqualTo(roomId.toString());
        assertThat(envelopeCaptor.getValue().metadata().getEventType()).isEqualTo(ChatEventType.MESSAGE_PINNED.value());
        assertThat(envelopeCaptor.getValue().payload()).isSameAs(payload);
    }
}
