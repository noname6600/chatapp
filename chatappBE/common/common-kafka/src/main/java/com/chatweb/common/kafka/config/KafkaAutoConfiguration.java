package com.chatweb.common.kafka.config;

import com.chatweb.common.kafka.consumer.KafkaEventDispatcher;
import com.chatweb.common.kafka.consumer.KafkaEventHandler;
import com.chatweb.common.kafka.observability.KafkaEventObserver;
import com.chatweb.common.kafka.observability.Slf4jKafkaEventLogger;
import com.chatweb.common.kafka.producer.DefaultKafkaEventPublisher;
import com.chatweb.common.kafka.producer.KafkaEventPublisher;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;

@AutoConfiguration
@ConditionalOnClass(KafkaTemplate.class)
public class KafkaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public KafkaEventObserver kafkaEventObserver() {
        return new Slf4jKafkaEventLogger();
    }

    @Bean
    @ConditionalOnMissingBean(KafkaEventPublisher.class)
    public KafkaEventPublisher kafkaEventPublisher(
            KafkaTemplate<String, Object> kafkaTemplate,
            KafkaEventObserver logger
    ) {
        return new DefaultKafkaEventPublisher(kafkaTemplate, logger);
    }

    @Bean
    @ConditionalOnMissingBean
    public KafkaEventDispatcher kafkaEventDispatcher(
            List<KafkaEventHandler<?>> handlers,
            KafkaEventObserver observer
    ) {
        return new KafkaEventDispatcher(handlers, observer);
    }
}
