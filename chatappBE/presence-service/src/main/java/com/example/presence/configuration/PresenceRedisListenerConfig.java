package com.example.presence.configuration;


import com.example.common.redis.listener.DefaultRedisMessageListener;
import com.example.common.redis.dispatcher.RedisMessageDispatcher;
import com.example.common.redis.observability.RedisPubSubLogger;
import com.example.common.redis.serialization.IRedisMessageSerializer;
import com.example.presence.redis.PresenceKeyExpiredListener;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
@RequiredArgsConstructor
public class PresenceRedisListenerConfig {

    private final IRedisMessageSerializer serializer;
    private final RedisMessageDispatcher dispatcher;
    private final RedisPubSubLogger logger;
    private final PresenceKeyExpiredListener presenceKeyExpiredListener;

    @Bean
    public MessageListener presenceRedisMessageListener() {
        return new DefaultRedisMessageListener(serializer, dispatcher, logger);
    }

    @Bean
    public RedisMessageListenerContainer redisContainer(
            RedisConnectionFactory factory,
            MessageListener presenceRedisMessageListener
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);

        container.addMessageListener(
                presenceRedisMessageListener,
                new PatternTopic("ws.presence.*")
        );

        container.addMessageListener(
                presenceKeyExpiredListener,
                new PatternTopic("__keyevent@*__:expired")
        );

        return container;
    }
}






