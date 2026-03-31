package com.example.common.redis.core;

import com.example.common.redis.api.ITimeRedisCacheManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.cache.*;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;


@Slf4j
public class TimeRedisCacheManager extends RedisCacheManager implements ITimeRedisCacheManager {

    private final RedisCacheWriter cacheWriter;
    private final RedisCacheConfiguration defaultCacheConfig;
    private final String serviceName;
    private final Queue<String> cacheNameFail = new ConcurrentLinkedQueue<>();
    private boolean cacheAvailable = true;

    public TimeRedisCacheManager(RedisCacheWriter cacheWriter, RedisCacheConfiguration defaultCacheConfiguration, String serviceName) {
        super(cacheWriter, defaultCacheConfiguration);
        this.cacheWriter = cacheWriter;
        this.defaultCacheConfig = defaultCacheConfiguration;
        this.serviceName = serviceName;
    }

    public TimeRedisCacheManager(
            RedisCacheWriter cacheWriter,
            RedisCacheConfiguration defaultCacheConfiguration,
            boolean allowInFlightCacheCreation,
            String serviceName
    ) {

        super(cacheWriter, defaultCacheConfiguration, allowInFlightCacheCreation);
        this.cacheWriter = cacheWriter;
        this.defaultCacheConfig = defaultCacheConfiguration;
        this.serviceName = serviceName;
    }

    public static TimeRedisCacheManagerBuilder timeBuilder(RedisConnectionFactory connectionFactory) {

        Assert.notNull(connectionFactory, "ConnectionFactory must not be null!");

        return TimeRedisCacheManagerBuilder.fromConnectionFactory(connectionFactory);
    }

    @Override
    @NonNull
    protected RedisCache createRedisCache(@NonNull String name, @Nullable RedisCacheConfiguration cacheConfig) {
        return new TimeRedisCache(name, cacheWriter, cacheConfig != null ? cacheConfig : defaultCacheConfig, serviceName);
    }

    @Override
    public void cacheUnAvailable(String cacheName) {
        log.debug("Redis connection fail with cache name {}", cacheName);
        if (!cacheNameFail.contains(cacheName)) {
            cacheNameFail.add(cacheName);
            log.warn("Redis connection fail with cache name {}", cacheName);
        }
        this.cacheAvailable = false;
    }

    @Override
    public boolean isCacheUnAvailable() {
        return !cacheAvailable;
    }

    @Override
    public void clearCacheFail() {
        while (!this.cacheNameFail.isEmpty()) {
            String cacheName = cacheNameFail.poll();
            Cache cache = getCache(cacheName);
            if (cache != null) {
                try {
                    cache.clear();
                } catch (RedisConnectionFailureException e) {
                    cacheNameFail.add(cacheName);
                    this.cacheAvailable = false;
                    return;
                }
            }
        }
        cacheAvailable = true;
    }

    public static class TimeRedisCacheManagerBuilder {

        boolean allowInFlightCacheCreation = true;
        private @Nullable
        RedisCacheWriter cacheWriter;
        private CacheStatisticsCollector statisticsCollector = CacheStatisticsCollector.none();
        private RedisCacheConfiguration defaultCacheConfiguration = RedisCacheConfiguration.defaultCacheConfig();
        private boolean enableTransactions;
        private String serviceName;

        private TimeRedisCacheManagerBuilder() {
        }

        private TimeRedisCacheManagerBuilder(@NonNull RedisCacheWriter cacheWriter) {
            this.cacheWriter = cacheWriter;
        }

        public static TimeRedisCacheManagerBuilder fromConnectionFactory(RedisConnectionFactory connectionFactory) {
            Assert.notNull(connectionFactory, "ConnectionFactory must not be null!");
            return new TimeRedisCacheManagerBuilder(RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory));
        }

        public static TimeRedisCacheManagerBuilder fromCacheWriter(RedisCacheWriter cacheWriter) {
            Assert.notNull(cacheWriter, "CacheWriter must not be null!");
            return new TimeRedisCacheManagerBuilder(cacheWriter);
        }

        public TimeRedisCacheManagerBuilder cacheDefaults(RedisCacheConfiguration defaultCacheConfiguration) {
            Assert.notNull(defaultCacheConfiguration, "DefaultCacheConfiguration must not be null!");
            this.defaultCacheConfiguration = defaultCacheConfiguration;
            return this;
        }

        public TimeRedisCacheManagerBuilder cacheWriter(RedisCacheWriter cacheWriter) {
            Assert.notNull(cacheWriter, "CacheWriter must not be null!");
            this.cacheWriter = cacheWriter;
            return this;
        }

        public TimeRedisCacheManagerBuilder transactionAware() {
            this.enableTransactions = true;
            return this;
        }

        public TimeRedisCacheManagerBuilder disableCreateOnMissingCache() {
            this.allowInFlightCacheCreation = false;
            return this;
        }

        public TimeRedisCacheManagerBuilder enableStatistics() {
            this.statisticsCollector = CacheStatisticsCollector.create();
            return this;
        }

        public TimeRedisCacheManagerBuilder serviceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }


        public TimeRedisCacheManager build() {

            Assert.state(cacheWriter != null,
                    "CacheWriter must not be null! You can provide one via 'RedisCacheManagerBuilder#cacheWriter(RedisCacheWriter)'.");

            RedisCacheWriter theCacheWriter = cacheWriter;

            if (!statisticsCollector.equals(CacheStatisticsCollector.none())) {
                theCacheWriter = cacheWriter.withStatisticsCollector(statisticsCollector);
            }

            TimeRedisCacheManager cm = new TimeRedisCacheManager(
                    theCacheWriter,
                    defaultCacheConfiguration,
                    allowInFlightCacheCreation,
                    serviceName
            );

            cm.setTransactionAware(enableTransactions);

            return cm;
        }
    }
}
