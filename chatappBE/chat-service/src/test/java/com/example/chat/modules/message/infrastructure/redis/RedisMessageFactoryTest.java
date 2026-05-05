package com.example.chat.modules.message.infrastructure.redis;

import com.example.common.redis.message.RedisMessage;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class RedisMessageFactoryTest {

    @Test
    void create_withExplicitEventMetadata_preservesEventIdAndCorrelationId() {
        RedisMessageFactory factory = new RedisMessageFactory();
        ReflectionTestUtils.setField(factory, "sourceService", "chat-service");

        RedisMessage<String> message = factory.create(
                "chat.message.sent",
                "payload",
                "event-123",
                "corr-123"
        );

        assertThat(message.getMessageId()).isNotBlank();
        assertThat(message.getEventId()).isEqualTo("event-123");
        assertThat(message.getCorrelationId()).isEqualTo("corr-123");
        assertThat(message.getSourceService()).isEqualTo("chat-service");
    }

    @Test
    void create_withoutExplicitEventMetadata_defaultsCorrelationToMessageIdentity() {
        RedisMessageFactory factory = new RedisMessageFactory();
        ReflectionTestUtils.setField(factory, "sourceService", "chat-service");

        RedisMessage<String> message = factory.create("chat.message.sent", "payload");

        assertThat(message.getMessageId()).isNotBlank();
        assertThat(message.getEventId()).isEqualTo(message.getMessageId());
        assertThat(message.getCorrelationId()).isEqualTo(message.getMessageId());
    }
}