package com.chatweb.auth.scheduler;

import com.chatweb.auth.jwt.IKeyManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Slf4j
@RequiredArgsConstructor
public class JwtKeyCleanupScheduler {

    private static final String LOCK_KEY = "auth:scheduler:jwt-key-cleanup";
    private static final Duration LOCK_TTL = Duration.ofMinutes(58);

    private final IKeyManager keyManager;
    private final StringRedisTemplate redisTemplate;

    @Scheduled(cron = "0 0 * * * *")
    public void cleanupExpiredKeys() {
        if (!Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(LOCK_KEY, "1", LOCK_TTL))) {
            log.debug("[JWT-KEY-CLEANUP] Skipped - another instance is handling this cycle");
            return;
        }
        try {
            keyManager.cleanupExpired();
            log.info("[JWT-KEY-CLEANUP] Completed successfully");
        } catch (Exception e) {
            log.error("[JWT-KEY-CLEANUP] Failed", e);
        }
    }
}
