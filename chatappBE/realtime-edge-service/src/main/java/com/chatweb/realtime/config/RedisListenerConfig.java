package com.chatweb.realtime.config;

import com.chatweb.common.redis.listener.RedisEventListener;
import com.chatweb.realtime.dispatch.EdgeDeliveryHandoffPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Redis pub/sub listener configuration for realtime-edge ingress.
 * Disabled by default during testing; enable via realtime.redis.listener.enabled=true property.
 */
@Configuration
@RequiredArgsConstructor
public class RedisListenerConfig {

    private final RedisConnectionFactory redisConnectionFactory;
    private final RedisEventListener redisEventListener;
    private final EdgeDeliveryHandoffPublisher edgeDeliveryHandoffPublisher;

    @Value("${realtime.dispatch.handoff.enabled:false}")
    private boolean handoffEnabled;

    @Bean
    @ConditionalOnProperty(name = "realtime.redis.listener.enabled", havingValue = "true", matchIfMissing = false)
    public RedisMessageListenerContainer redisMessageListenerContainer() {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        // Chat and notification delivery moved to Kafka — only presence stays on Redis pub/sub.
        container.addMessageListener(redisEventListener, new PatternTopic("realtime.presence.*"));
        if (handoffEnabled) {
            container.addMessageListener(redisEventListener, new PatternTopic(edgeDeliveryHandoffPublisher.topicPattern()));
        }
        return container;
    }
}
