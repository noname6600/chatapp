package com.chatweb.notification.infrastructure.kafka;

import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.event.EventMetadata;
import com.chatweb.common.integration.chat.ChatMessagePayload;
import com.chatweb.common.integration.chat.ChatEventType;
import com.chatweb.common.integration.enums.MessageType;
import com.chatweb.common.kafka.serialization.EventEnvelopeKafkaDeserializer;
import com.chatweb.common.kafka.serialization.EventEnvelopeKafkaSerializer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationKafkaDeserializationWiringTest {

    @Test
    void consumerDeserializerChain_deserializesChatMessageEnvelopePayload() {
        EventEnvelope<ChatMessagePayload> envelope = new EventEnvelope<>(
                new EventMetadata(
                        UUID.randomUUID().toString(),
                        ChatEventType.MESSAGE_SENT.value(),
                        "chat-service",
                        Instant.now(),
                        UUID.randomUUID().toString()
                ),
                ChatMessagePayload.builder()
                        .messageId(UUID.randomUUID())
                        .roomId(UUID.randomUUID())
                        .senderId(UUID.randomUUID())
                        .type(MessageType.TEXT)
                        .content("hello")
                        .createdAt(Instant.now())
                        .build()
        );

        EventEnvelopeKafkaSerializer serializer = new EventEnvelopeKafkaSerializer();
        byte[] bytes = serializer.serialize(ChatEventType.MESSAGE_SENT.value(), envelope);

        ErrorHandlingDeserializer<EventEnvelope<?>> deserializer = new ErrorHandlingDeserializer<>();
        deserializer.configure(productionStyleConsumerProps(), false);

        EventEnvelope<?> deserialized = deserializer.deserialize(
                ChatEventType.MESSAGE_SENT.value(),
                new RecordHeaders(),
                bytes
        );

        assertThat(deserialized).isNotNull();
        assertThat(deserialized.metadata().getEventType()).isEqualTo(ChatEventType.MESSAGE_SENT.value());
        assertThat(deserialized.payload()).isInstanceOf(ChatMessagePayload.class);

        deserializer.close();
    }

    private Map<String, Object> productionStyleConsumerProps() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, EventEnvelopeKafkaDeserializer.class.getName());
        return props;
    }
}
