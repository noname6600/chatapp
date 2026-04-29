package com.example.user.kafka;

import com.example.common.integration.kafka.KafkaTopics;
import com.example.common.integration.kafka.event.AccountCreatedEvent;
import com.example.user.entity.UserProfile;
import com.example.user.repository.UserProfileRepository;
import com.example.user.utils.AvatarGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class AccountCreatedConsumer {

    private static final int MAX_USERNAME_ATTEMPTS = 50;

    private final UserProfileRepository repository;
    private final AvatarGenerator avatarGenerator;

    @KafkaListener(topics = KafkaTopics.ACCOUNT_CREATED)
    public void listen(AccountCreatedEvent event) {
        var payload = event.getPayload();
        UUID accountId = payload.getAccountId();

        log.info("[USER] Received AccountCreatedEvent for {}", payload.getEmail());

        if (repository.existsById(accountId)) {
            log.info("[USER] Profile already exists -> skip");
            return;
        }

        createProfileIdempotently(accountId, payload.getEmail());
    }

    private void createProfileIdempotently(UUID accountId, String email) {
        String base = normalizeBaseUsername(email);

        for (int suffix = 0; suffix < MAX_USERNAME_ATTEMPTS; suffix++) {
            String username = withSuffix(base, suffix);

            if (repository.existsByUsername(username)) {
                continue;
            }

            try {
                String avatarUrl = avatarGenerator.generate(accountId, username);

                repository.save(
                        UserProfile.builder()
                                .accountId(accountId)
                                .username(username)
                                .displayName(username)
                                .avatarUrl(avatarUrl)
                                .avatarPublicId("generated:" + accountId)
                                .aboutMe("")
                                .backgroundColor("#ffffff")
                                .build()
                );

                log.info("[USER] Profile created successfully -> username={}", username);
                return;
            } catch (DataIntegrityViolationException ex) {
                if (repository.existsById(accountId)) {
                    log.info("[USER] Profile already created by concurrent consumer -> accountId={}", accountId);
                    return;
                }

                log.debug("[USER] Username candidate conflict -> username={}", username);
            }
        }

        throw new IllegalStateException("Unable to allocate a unique username for account " + accountId);
    }

    private String normalizeBaseUsername(String email) {
        String base = email.split("@")[0]
                .replaceAll("[^a-zA-Z0-9._]", "")
                .toLowerCase();

        if (base.length() < 3) {
            base = base + "user";
        }

        return base;
    }

    private String withSuffix(String base, int suffix) {
        if (suffix == 0) {
            return base;
        }
        return base + suffix;
    }
}
