package com.chatweb.common.redis.config;

import com.chatweb.common.redis.dispatcher.RedisEventDispatcher;
import com.chatweb.common.redis.listener.RedisEventListener;
import com.chatweb.common.redis.observability.RedisPubSubObserver;
import com.chatweb.common.redis.observability.Slf4jRedisPubSubLogger;
import com.chatweb.common.redis.publisher.DefaultRedisEventPublisher;
import com.chatweb.common.redis.publisher.RedisEventPublisher;
import com.chatweb.common.redis.registry.DefaultRedisEventRegistry;
import com.chatweb.common.redis.registry.RedisEventRegistry;
import com.chatweb.common.redis.serialization.JsonRedisEventSerializer;
import com.chatweb.common.redis.serialization.RedisEventSerializer;
import com.chatweb.common.redis.subscriber.RedisEventHandler;
import com.chatweb.common.event.SharedEventCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
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
    public RedisEventListener redisEventListener(
            RedisEventSerializer serializer,
            RedisEventDispatcher dispatcher,
            RedisPubSubObserver logger
    ) {
        return new RedisEventListener(serializer, dispatcher, logger);
    }
}
