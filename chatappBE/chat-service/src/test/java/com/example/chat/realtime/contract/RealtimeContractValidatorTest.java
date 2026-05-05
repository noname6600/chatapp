package com.example.chat.realtime.contract;

import com.example.common.integration.contract.RealtimeContractValidator;
import com.example.common.kafka.api.KafkaEvent;
import com.example.common.redis.api.IRedisMessage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RealtimeContractValidatorTest {

    @Test
    void validateEventName_rejectsNonConformingNames() {
        assertThatThrownBy(() -> RealtimeContractValidator.validateEventNameOrThrow("FRIEND_STATUS_CHANGED"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatNoException().isThrownBy(() -> RealtimeContractValidator.validateEventNameOrThrow("chat.message.sent"));
        assertThatNoException().isThrownBy(() -> RealtimeContractValidator.validateEventNameOrThrow("system.dead-letter"));
    }

    @Test
    void validateKafkaEvent_rejectsMissingIdentity() {
        KafkaEvent<Object> invalid = new KafkaEvent<>() {
            @Override public UUID getEventId() { return null; }
            @Override public String getEventType() { return "chat.message.sent"; }
            @Override public String getSourceService() { return "chat-service"; }
            @Override public Instant getCreatedAt() { return Instant.now(); }
            @Override public Object getPayload() { return null; }
        };

        assertThatThrownBy(() -> RealtimeContractValidator.validateIdentityOrThrow(
                invalid.getEventId() == null ? null : invalid.getEventId().toString(),
                invalid.getCorrelationId(),
                invalid.getSourceService(),
                invalid.getCreatedAt()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventId");
    }

    @Test
    void validateRedisMessage_rejectsMissingIdentity() {
        IRedisMessage invalid = new IRedisMessage() {
            @Override public String getMessageId() { return ""; }
            @Override public String getEventType() { return "presence.user.online"; }
            @Override public String getSourceService() { return "presence-service"; }
            @Override public Instant getCreatedAt() { return Instant.now(); }
        };

        assertThatThrownBy(() -> RealtimeContractValidator.validateIdentityOrThrow(
            invalid.getEventId(),
            invalid.getCorrelationId(),
            invalid.getSourceService(),
            invalid.getCreatedAt()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventId");
    }

    @Test
    void identityAccessors_areStandardizedAcrossKafkaAndRedis() {
        UUID eventId = UUID.randomUUID();
        KafkaEvent<Object> kafka = new KafkaEvent<>() {
            @Override public UUID getEventId() { return eventId; }
            @Override public String getEventType() { return "chat.message.sent"; }
            @Override public String getSourceService() { return "chat-service"; }
            @Override public Instant getCreatedAt() { return Instant.now(); }
            @Override public Object getPayload() { return null; }
        };

        IRedisMessage redis = new IRedisMessage() {
            @Override public String getMessageId() { return "m-1"; }
            @Override public String getEventType() { return "presence.user.online"; }
            @Override public String getSourceService() { return "presence-service"; }
            @Override public Instant getCreatedAt() { return Instant.now(); }
        };

        assertThat(kafka.getCorrelationId()).isEqualTo(eventId.toString());
        assertThat(redis.getEventId()).isEqualTo("m-1");
        assertThat(redis.getCorrelationId()).isEqualTo("m-1");
    }
}
