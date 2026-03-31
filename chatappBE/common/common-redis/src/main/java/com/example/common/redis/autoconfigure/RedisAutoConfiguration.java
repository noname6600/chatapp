package com.example.common.redis.autoconfigure;


import com.example.common.redis.api.IRedisPublisher;
import com.example.common.redis.api.IRedisSubscriber;
import com.example.common.redis.dispatcher.RedisMessageDispatcher;
import com.example.common.redis.listener.DefaultRedisMessageListener;
import com.example.common.redis.observability.IRedisPubSubLogger;
import com.example.common.redis.observability.RedisPubSubLogger;
import com.example.common.redis.publisher.DefaultRedisPublisher;
import com.example.common.redis.registry.IRedisMessageRegistry;
import com.example.common.redis.serialization.IRedisMessageSerializer;
import com.example.common.redis.serialization.JsonRedisMessageSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

@Configuration
@ConditionalOnClass(StringRedisTemplate.class)
public class RedisAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public IRedisMessageSerializer redisMessageSerializer(
            ObjectMapper objectMapper,
            IRedisMessageRegistry registry
    ) {
        return new JsonRedisMessageSerializer(objectMapper, registry);
    }

    @Bean
    public IRedisPublisher redisPublisher(
            StringRedisTemplate template,
            IRedisMessageSerializer serializer,
            IRedisPubSubLogger logger
    ) {
        return new DefaultRedisPublisher(template, serializer, logger);
    }

    @Bean
    public RedisMessageDispatcher dispatcher(
            List<IRedisSubscriber<?>> subscribers
    ) {
        return new RedisMessageDispatcher(subscribers);
    }

    @Bean
    public MessageListener redisListener(
            IRedisMessageSerializer serializer,
            RedisMessageDispatcher dispatcher,
            RedisPubSubLogger logger
    ) {
        return new DefaultRedisMessageListener(serializer, dispatcher, logger);
    }
}


