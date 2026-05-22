package com.chatweb.common.redis.contract;

import com.chatweb.common.event.EventPayloadRegistry;
import com.chatweb.common.redis.config.RedisAutoConfiguration;
import com.chatweb.common.redis.dispatcher.RedisEventDispatcher;
import com.chatweb.common.redis.observability.RedisPubSubObserver;
import com.chatweb.common.redis.publisher.RedisEventPublisher;
import com.chatweb.common.redis.serialization.RedisEventSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class RedisAutoConfigurationContractTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RedisAutoConfiguration.class))
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(StringRedisTemplate.class, () -> new StringRedisTemplate() {
                @Override
                public void afterPropertiesSet() {
                    // Skip RedisConnectionFactory validation for this context wiring test.
                }
            });

    @Test
    void autoConfiguration_registersCanonicalRedisBeansIncludingInboundAdapter() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(EventPayloadRegistry.class);
            assertThat(context).hasSingleBean(RedisEventSerializer.class);
            assertThat(context).hasSingleBean(RedisEventDispatcher.class);
            assertThat(context).hasSingleBean(RedisPubSubObserver.class);
            assertThat(context).hasSingleBean(RedisEventPublisher.class);
            assertThat(context).hasSingleBean(org.springframework.data.redis.connection.MessageListener.class);
        });
    }
}
