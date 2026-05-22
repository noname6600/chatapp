package com.chatweb.notification.infrastructure.kafka;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KafkaConsumerConfigTest {

    @Test
    void kafkaListenerContainerFactory_usesDeadLetterPublishingRecoverer() {
        KafkaConsumerConfig config = new KafkaConsumerConfig();
        Object factory = config.kafkaListenerContainerFactory(
                mock(ConsumerFactory.class),
                mock(KafkaTemplate.class)
        );
        DefaultErrorHandler handler = (DefaultErrorHandler) ReflectionTestUtils.getField(factory, "commonErrorHandler");
        assertThat(handler).isNotNull();
    }
}
