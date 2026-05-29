package com.chatweb.auth.scheduler;

import com.chatweb.auth.service.ITokenServiceFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Slf4j
@RequiredArgsConstructor
public class RefreshTokenCleanupScheduler {

    private static final String LOCK_KEY = "auth:scheduler:refresh-token-cleanup";
    private static final Duration LOCK_TTL = Duration.ofMinutes(28);

    private final ITokenServiceFacade tokenFacade;
    private final StringRedisTemplate redisTemplate;

    @Scheduled(cron = "0 */30 * * * *")
    public void cleanupRefreshTokens() {
        if (!Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(LOCK_KEY, "1", LOCK_TTL))) {
            log.debug("[REFRESH-TOKEN-CLEANUP] Skipped - another instance is handling this cycle");
            return;
        }
        try {
            int deleted = tokenFacade.cleanup();
            log.info("[REFRESH-TOKEN-CLEANUP] Deleted {} tokens", deleted);
        } catch (Exception e) {
            log.error("[REFRESH-TOKEN-CLEANUP] Failed", e);
        }
    }
}
