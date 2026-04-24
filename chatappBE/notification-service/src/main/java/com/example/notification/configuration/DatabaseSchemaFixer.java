package com.example.notification.configuration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class DatabaseSchemaFixer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        // Ensure action_required column exists
        jdbcTemplate.execute(
                "ALTER TABLE notifications ADD COLUMN IF NOT EXISTS action_required BOOLEAN NOT NULL DEFAULT FALSE"
        );

        // Drop old notifications_type_check constraint if it exists (it was too restrictive and didn't include REPLY, REACTION, GROUP_INVITE)
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check"
            );
            log.info("Dropped restrictive notifications_type_check constraint");
        } catch (Exception e) {
            log.debug("notifications_type_check constraint doesn't exist or couldn't be dropped: {}", e.getMessage());
        }

        log.info("Schema check complete: ensured notifications.action_required exists, removed old type check");
    }
}

