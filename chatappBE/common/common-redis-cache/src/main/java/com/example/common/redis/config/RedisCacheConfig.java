package com.example.common.redis.config;

import com.example.common.redis.core.TimeRedisCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

//@Configuration
//public class RedisCacheConfig {
//
//    @Bean
//    public TimeRedisCacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
//        return TimeRedisCacheManager
//                .timeBuilder(connectionFactory)
//                .serviceName("redis-test")
//                .build();
//    }
//}
