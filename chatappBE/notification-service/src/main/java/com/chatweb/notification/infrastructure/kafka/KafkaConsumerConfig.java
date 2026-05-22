package com.chatweb.notification.infrastructure.kafka;

import com.chatweb.common.kafka.topic.KafkaTopics;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.beans.factory.annotation.Value;

import org.apache.kafka.common.TopicPartition;



@Configuration
@EnableKafka
public class KafkaConsumerConfig {

        @Value("${notification.kafka.retry.max-attempts:5}")
        private int retryMaxAttempts = 5;

        @Value("${notification.kafka.retry.initial-interval-ms:1000}")
        private long retryInitialIntervalMs = 1000L;

        @Value("${notification.kafka.retry.max-interval-ms:30000}")
        private long retryMaxIntervalMs = 30000L;

        @Value("${notification.kafka.retry.multiplier:2.0}")
        private double retryMultiplier = 2.0;

    @Bean
    public JsonMapper objectMapper() {
        return JsonMapper.builder().findAndAddModules().build();
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object>
    kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            KafkaTemplate<String, Object> kafkaTemplate
    ) {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory);

        long initial = Math.max(100L, retryInitialIntervalMs);
        long maxInterval = Math.max(1000L, retryMaxIntervalMs);
        int maxAttempts = Math.max(1, retryMaxAttempts);
        double multiplier = Math.max(1.0, retryMultiplier);

        ExponentialBackOff backOff = new ExponentialBackOff();
        backOff.setInitialInterval(initial);
        backOff.setMaxInterval(maxInterval);
        backOff.setMultiplier(multiplier);
        backOff.setMaxElapsedTime(maxInterval * (long) maxAttempts);

        factory.setCommonErrorHandler(
                new DefaultErrorHandler(
                        new DeadLetterPublishingRecoverer(kafkaTemplate,
                                (record, ex) -> new TopicPartition(KafkaTopics.TOPIC_SYSTEM_DEAD_LETTER, record.partition())),
                        backOff
                )
        );

        return factory;
    }
}



