package com.example.common.redis.config;

import com.example.common.redis.dispatcher.RedisEventDispatcher;
import com.example.common.redis.listener.RedisEventListener;
import com.example.common.redis.observability.RedisPubSubObserver;
import com.example.common.redis.observability.Slf4jRedisPubSubLogger;
import com.example.common.redis.publisher.DefaultRedisEventPublisher;
import com.example.common.redis.publisher.RedisEventPublisher;
import com.example.common.redis.registry.DefaultRedisEventRegistry;
import com.example.common.redis.registry.RedisEventRegistry;
import com.example.common.redis.serialization.JsonRedisEventSerializer;
import com.example.common.redis.serialization.RedisEventSerializer;
import com.example.common.redis.subscriber.RedisEventHandler;
import com.example.common.event.SharedEventCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

@AutoConfiguration
@ConditionalOnClass(StringRedisTemplate.class)
public class RedisAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RedisPubSubObserver redisPubSubObserver() {
        return new Slf4jRedisPubSubLogger();
    }

    @Bean
    @ConditionalOnMissingBean
    public RedisEventRegistry redisEventRegistry() {
        DefaultRedisEventRegistry registry = new DefaultRedisEventRegistry();
        SharedEventCatalog.registerAll(registry);
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public RedisEventSerializer redisEventSerializer(
            ObjectMapper objectMapper,
            RedisEventRegistry registry
    ) {
        return new JsonRedisEventSerializer(objectMapper, registry);
    }

    @Bean
    @ConditionalOnMissingBean
    public RedisEventPublisher redisEventPublisher(
            StringRedisTemplate template,
            RedisEventSerializer serializer,
            RedisPubSubObserver logger
    ) {
        return new DefaultRedisEventPublisher(template, serializer, logger);
    }

    @Bean
    @ConditionalOnMissingBean
    public RedisEventDispatcher redisEventDispatcher(
            List<RedisEventHandler<?>> handlers
    ) {
        return new RedisEventDispatcher(handlers);
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageListener redisEventListener(
            RedisEventSerializer serializer,
            RedisEventDispatcher dispatcher,
            RedisPubSubObserver logger
    ) {
        return new RedisEventListener(serializer, dispatcher, logger);
    }
}
