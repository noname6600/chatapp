package com.chatweb.common.kafka.config;

import com.chatweb.common.kafka.consumer.KafkaEventDispatcher;
import com.chatweb.common.kafka.consumer.KafkaEventHandler;
import com.chatweb.common.kafka.consumer.UnknownKafkaEventPolicy;
import com.chatweb.common.kafka.observability.KafkaEventObserver;
import com.chatweb.common.kafka.observability.MicrometerKafkaEventLogger;
import com.chatweb.common.kafka.observability.Slf4jKafkaEventLogger;
import com.chatweb.common.kafka.producer.DefaultKafkaEventPublisher;
import com.chatweb.common.kafka.producer.KafkaEventPublisher;
import com.chatweb.common.kafka.retry.KafkaRetryDlqPolicy;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import org.apache.kafka.common.TopicPartition;
import java.util.List;

@AutoConfiguration
@ConditionalOnClass(KafkaTemplate.class)
@EnableConfigurationProperties(KafkaRetryDlqPolicy.class)
public class KafkaAutoConfiguration {

    // ── Observer: prefer Micrometer when available, fall back to SLF4J ──────

    @Bean
    @ConditionalOnMissingBean(KafkaEventObserver.class)
    @ConditionalOnClass(MeterRegistry.class)
    public KafkaEventObserver micrometerKafkaEventObserver(MeterRegistry registry) {
        return new MicrometerKafkaEventLogger(registry);
    }

    @Bean
    @ConditionalOnMissingBean(KafkaEventObserver.class)
    public KafkaEventObserver slf4jKafkaEventObserver() {
        return new Slf4jKafkaEventLogger();
    }

    // ── Publisher ────────────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(KafkaEventPublisher.class)
    public KafkaEventPublisher kafkaEventPublisher(
            KafkaTemplate<String, Object> kafkaTemplate,
            KafkaEventObserver observer
    ) {
        return new DefaultKafkaEventPublisher(kafkaTemplate, observer);
    }

    // ── Dispatcher ───────────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(KafkaEventDispatcher.class)
    public KafkaEventDispatcher kafkaEventDispatcher(
            List<KafkaEventHandler<?>> handlers,
            KafkaEventObserver observer,
            @Value("${common.kafka.unknown-event-policy:FAIL}") String unknownPolicyStr
    ) {
        UnknownKafkaEventPolicy policy = UnknownKafkaEventPolicy.valueOf(unknownPolicyStr.toUpperCase());
        return new KafkaEventDispatcher(handlers, observer, policy);
    }

    // ── Central listener container factory with retry + DLQ ──────────────────
    // @ConditionalOnMissingBean so services that need custom settings can override.

    @Bean
    @ConditionalOnMissingBean(ConcurrentKafkaListenerContainerFactory.class)
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            KafkaTemplate<String, Object> kafkaTemplate,
            KafkaRetryDlqPolicy policy
    ) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (ConsumerRecord<?, ?> record, Exception ex) ->
                        new TopicPartition(policy.deadLetterTopic(record.topic()), 0)
        );

        ExponentialBackOff backOff = new ExponentialBackOff(
                policy.getInitialIntervalMs(),
                policy.getMultiplier()
        );
        backOff.setMaxInterval(policy.getMaxIntervalMs());

        CommonErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
