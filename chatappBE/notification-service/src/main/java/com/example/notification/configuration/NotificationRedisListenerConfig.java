package com.example.notification.configuration;

import com.example.notification.constants.NotificationRedisChannels;
import com.example.notification.websocket.redis.RedisNotificationSubscriber;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.redis.listener.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationRedisListenerConfig {

    private final RedisNotificationSubscriber redisNotificationSubscriber;

    @Bean
    public RedisMessageListenerContainer notificationRedisMessageListenerContainer(
            RedisConnectionFactory factory
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);

        container.addMessageListener(
                redisNotificationSubscriber,
                new PatternTopic(NotificationRedisChannels.NOTIFICATION_USER_PATTERN)
        );

        return container;
    }
}