package com.chatweb.realtime.config;

import com.chatweb.realtime.adapter.in.redis.RedisEventListener;
import com.chatweb.realtime.dispatch.EdgeDeliveryHandoffListener;
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
    private final EdgeDeliveryHandoffListener edgeDeliveryHandoffListener;
    private final EdgeDeliveryHandoffPublisher edgeDeliveryHandoffPublisher;

    @Value("${realtime.dispatch.handoff.enabled:false}")
    private boolean handoffEnabled;

    @Bean
    @ConditionalOnProperty(name = "realtime.redis.listener.enabled", havingValue = "true", matchIfMissing = false)
    public RedisMessageListenerContainer redisMessageListenerContainer() {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.addMessageListener(redisEventListener, new PatternTopic("realtime.chat.room.*"));
        container.addMessageListener(redisEventListener, new PatternTopic("realtime.notification.user.*"));
        container.addMessageListener(redisEventListener, new PatternTopic("realtime.presence.*"));
        if (handoffEnabled) {
            container.addMessageListener(edgeDeliveryHandoffListener, new PatternTopic(edgeDeliveryHandoffPublisher.topicPattern()));
        }
        return container;
    }
}
