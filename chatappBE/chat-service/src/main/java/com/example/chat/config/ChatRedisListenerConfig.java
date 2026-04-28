package com.example.chat.config;

import com.example.chat.constants.ChatRedisChannels;
import com.example.common.redis.dispatcher.RedisMessageDispatcher;
import com.example.common.redis.listener.DefaultRedisMessageListener;
import com.example.common.redis.observability.IRedisPubSubLogger;
import com.example.common.redis.serialization.IRedisMessageSerializer;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
@RequiredArgsConstructor
public class ChatRedisListenerConfig {

    private final IRedisMessageSerializer serializer;
    private final RedisMessageDispatcher dispatcher;
    private final IRedisPubSubLogger logger;

    @Bean
    @ConditionalOnProperty(
            value = "chat.redis.listener.enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory factory
    ) {

        RedisMessageListenerContainer container =
                new RedisMessageListenerContainer();

        container.setConnectionFactory(factory);

        container.addMessageListener(
                new DefaultRedisMessageListener(serializer, dispatcher, logger),
                new PatternTopic(ChatRedisChannels.CHAT_ROOM_PATTERN)
        );

        return container;
    }
}



