package com.example.chat.realtime.contract;

import com.example.common.event.EventEnvelope;
import com.example.common.kafka.api.IKafkaEvent;
import com.example.common.kafka.api.IKafkaEventPublisher;
import com.example.common.kafka.api.KafkaEvent;
import com.example.common.kafka.api.KafkaEventPublisher;
import com.example.common.kafka.flow.KafkaEventRoutingContext;
import com.example.common.redis.api.IRedisMessage;
import com.example.common.redis.flow.RedisEventRoutingContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class RealtimeMessagingAlignmentTest {

    @Test
    void routingContexts_mapConnectionToEventMetadata_consistently() {
        UUID eventId = UUID.randomUUID();
        IKafkaEvent<Object> kafkaEvent = new KafkaEvent<>() {
            @Override public UUID getEventId() { return eventId; }
            @Override public String getEventType() { return "chat.message.sent"; }
            @Override public String getSourceService() { return "chat-service"; }
            @Override public Instant getCreatedAt() { return Instant.now(); }
            @Override public Object getPayload() { return null; }
        };

        IRedisMessage redisMessage = new IRedisMessage() {
            @Override public String getMessageId() { return "m-100"; }
            @Override public String getEventType() { return "presence.user.online"; }
            @Override public String getSourceService() { return "presence-service"; }
            @Override public Instant getCreatedAt() { return Instant.now(); }
        };

        KafkaEventRoutingContext kafkaContext = KafkaEventRoutingContext.of("chat.topic", "room-1", kafkaEvent);
        RedisEventRoutingContext redisContext = RedisEventRoutingContext.of("presence.channel", redisMessage);

        assertThat(kafkaContext.topic()).isEqualTo("chat.topic");
        assertThat(kafkaContext.key()).isEqualTo("room-1");
        assertThat(kafkaContext.eventType()).isEqualTo("chat.message.sent");
        assertThat(kafkaContext.correlationId()).isEqualTo(eventId.toString());

        assertThat(redisContext.channel()).isEqualTo("presence.channel");
        assertThat(redisContext.eventType()).isEqualTo("presence.user.online");
        assertThat(redisContext.correlationId()).isEqualTo("m-100");
    }

    @Test
    void legacyKafkaAliases_remainCompatibleWithNewIPrefixedContracts() {
        IKafkaEvent<Object> newContractEvent = new KafkaEvent<>() {
            @Override public UUID getEventId() { return UUID.randomUUID(); }
            @Override public String getEventType() { return "system.dead-letter"; }
            @Override public String getSourceService() { return "chat-service"; }
            @Override public Instant getCreatedAt() { return Instant.now(); }
            @Override public Object getPayload() { return null; }
        };

        AtomicBoolean called = new AtomicBoolean(false);

        KafkaEventPublisher legacyPublisher = new KafkaEventPublisher() {
            @Override
            public void publish(String topic, String key, IKafkaEvent<?> event) {
                called.set(true);
                assertThat(topic).isEqualTo("test.topic");
                assertThat(key).isEqualTo("test.key");
                assertThat(event.getEventType()).isEqualTo("system.dead-letter");
            }

            @Override
            public void publish(String topic, String key, EventEnvelope<?> envelope) {}
        };

        IKafkaEventPublisher newContractPublisher = legacyPublisher;
        newContractPublisher.publish("test.topic", "test.key", newContractEvent);

        assertThat(called).isTrue();
    }
}
