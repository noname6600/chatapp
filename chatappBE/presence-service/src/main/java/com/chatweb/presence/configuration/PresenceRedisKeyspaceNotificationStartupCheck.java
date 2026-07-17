package com.chatweb.presence.configuration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Properties;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "presence.redis.keyspace-notification.check.enabled", havingValue = "true", matchIfMissing = true)
public class PresenceRedisKeyspaceNotificationStartupCheck implements ApplicationRunner {

    private static final String CONFIG_KEY = "notify-keyspace-events";
    private static final String REQUIRED_HINT = "Expected Redis notify-keyspace-events to include keyevent expired flags: Ex (or E + A).";

    private final StringRedisTemplate redisTemplate;

    @Value("${presence.redis.listener.enabled:true}")
    private boolean redisListenerEnabled;

    @Value("${presence.redis.keyspace-notification.check.fail-on-missing:false}")
    private boolean failOnMissing;

    @Override
    public void run(ApplicationArguments args) {
        if (!redisListenerEnabled) {
            log.info("[PRESENCE] Redis listener disabled; skipping keyspace notification startup check");
            return;
        }

        Optional<String> setting = readNotifyKeyspaceEvents();
        if (setting.isEmpty()) {
            String message = "[PRESENCE] Could not verify Redis notify-keyspace-events setting. " +
                    "TTL expiration OFFLINE handling depends on keyevent expired notifications. " + REQUIRED_HINT;
            handleFailure(message);
            return;
        }

        String flags = setting.get();
        if (!hasRequiredExpiredKeyeventFlags(flags)) {
            String message = "[PRESENCE] Redis notify-keyspace-events='" + flags + "' is missing required keyevent expired flags. " + REQUIRED_HINT;
            handleFailure(message);
            return;
        }

        log.info("[PRESENCE] Redis keyspace notification check passed: notify-keyspace-events='{}'", flags);
    }

    static boolean hasRequiredExpiredKeyeventFlags(String flags) {
        if (flags == null || flags.isBlank()) {
            return false;
        }

        boolean hasKeyeventChannel = flags.indexOf('E') >= 0;
        boolean hasExpiredEvent = flags.indexOf('x') >= 0 || flags.indexOf('A') >= 0;
        return hasKeyeventChannel && hasExpiredEvent;
    }

    private Optional<String> readNotifyKeyspaceEvents() {
        try {
            Properties properties = redisTemplate.execute((RedisCallback<Properties>) connection ->
                    connection.serverCommands().getConfig(CONFIG_KEY)
            );
            if (properties == null) {
                return Optional.empty();
            }

            String value = properties.getProperty(CONFIG_KEY);
            if (value != null) {
                return Optional.of(value);
            }

            return properties.values().stream().findFirst().map(Object::toString);
        } catch (DataAccessException | UnsupportedOperationException ex) {
            log.warn("[PRESENCE] Redis keyspace notification check could not read CONFIG GET {}: {}", CONFIG_KEY, ex.getMessage());
            return Optional.empty();
        }
    }

    private void handleFailure(String message) {
        if (failOnMissing) {
            throw new IllegalStateException(message);
        }
        log.warn(message);
    }
}
