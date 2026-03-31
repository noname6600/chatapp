package com.example.auth.scheduler;

import com.example.auth.jwt.IKeyManager;
import com.example.auth.jwt.impl.KeyManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class JwtKeyCleanupScheduler {

    private final IKeyManager keyManager;

    @Scheduled(cron = "0 0 * * * *")
    public void cleanupExpiredKeys() {
        try {
            keyManager.cleanupExpired();
            log.info("[JWT-KEY-CLEANUP] Completed successfully");
        } catch (Exception e) {
            log.error("[JWT-KEY-CLEANUP] Failed", e);
        }
    }
}

