package com.example.auth.scheduler;

import com.example.auth.repository.RefreshTokenRepository;
import com.example.auth.service.ITokenServiceFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@Slf4j
@RequiredArgsConstructor
public class RefreshTokenCleanupScheduler {

    private final ITokenServiceFacade tokenFacade;

    @Scheduled(cron = "0 */30 * * * *")
    public void cleanupRefreshTokens() {
        try {
            int deleted = tokenFacade.cleanup();
            log.info("[REFRESH-TOKEN-CLEANUP] Deleted {} tokens", deleted);
        } catch (Exception e) {
            log.error("[REFRESH-TOKEN-CLEANUP] Failed", e);
        }
    }
}

