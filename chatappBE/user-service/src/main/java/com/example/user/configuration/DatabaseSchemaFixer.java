package com.example.user.configuration;

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
        Integer duplicateAccountLinks = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM (" +
                        "SELECT account_id FROM user_profiles GROUP BY account_id HAVING COUNT(*) > 1" +
                        ") duplicates",
                Integer.class
        );

        if (duplicateAccountLinks != null && duplicateAccountLinks > 0) {
            throw new IllegalStateException(
                    "Detected duplicate account_id rows in user_profiles. " +
                            "Please deduplicate before enabling uniqueness constraints."
            );
        }

        jdbcTemplate.execute(
                "CREATE UNIQUE INDEX IF NOT EXISTS uk_user_profiles_account_id ON user_profiles(account_id)"
        );

        log.info("Schema check complete: ensured unique user_profiles.account_id linkage");
    }
}
