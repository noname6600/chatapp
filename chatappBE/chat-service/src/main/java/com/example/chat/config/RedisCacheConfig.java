package com.example.chat.config;

import com.example.common.redis.core.TimeRedisCacheManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
@RequiredArgsConstructor
public class RedisCacheConfig {

    private final ObjectMapper objectMapper;

    @Bean
    public TimeRedisCacheManager redisCacheManager(
            RedisConnectionFactory connectionFactory,
            @Value("${spring.application.name}") String serviceName
    ) {

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new StringRedisSerializer()
                        )
                )
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer(objectMapper)
                        )
                );

        return TimeRedisCacheManager
                .timeBuilder(connectionFactory)
                .cacheDefaults(config)
                .serviceName(serviceName)
                .build();
    }
}