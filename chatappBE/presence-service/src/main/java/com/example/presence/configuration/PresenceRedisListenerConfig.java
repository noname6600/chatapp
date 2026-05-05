package com.example.presence.configuration;


import com.example.common.redis.dispatcher.RedisEventDispatcher;
import com.example.common.redis.listener.RedisEventListener;
import com.example.common.redis.serialization.RedisEventSerializer;
import com.example.presence.constants.PresenceRedisChannels;
import com.example.presence.redis.PresenceKeyExpiredListener;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "presence.redis.listener.enabled", havingValue = "true", matchIfMissing = true)
public class PresenceRedisListenerConfig {

    private final RedisEventSerializer serializer;
    private final RedisEventDispatcher dispatcher;
    private final RedisPubSubLogger logger;
    private final PresenceKeyExpiredListener presenceKeyExpiredListener;

    @Bean
    public MessageListener presenceRedisMessageListener() {
        return new RedisEventListener(serializer, dispatcher, logger);
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
            new PatternTopic(PresenceRedisChannels.PRESENCE_PATTERN)
        );

        container.addMessageListener(
                presenceKeyExpiredListener,
                new PatternTopic("__keyevent@*__:expired")
        );

        return container;
    }
}






