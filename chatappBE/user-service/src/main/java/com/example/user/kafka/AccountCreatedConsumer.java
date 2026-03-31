package com.example.user.kafka;

import com.example.common.kafka.event.AccountCreatedEvent;
import com.example.common.kafka.Topics;
import com.example.user.entity.UserProfile;
import com.example.user.repository.UserProfileRepository;
import com.example.user.utils.AvatarGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AccountCreatedConsumer {

    private final UserProfileRepository repository;
    private final AvatarGenerator avatarGenerator;

    @KafkaListener(topics = Topics.ACCOUNT_CREATED)
    public void listen(AccountCreatedEvent event) {

        var payload = event.getPayload();
        var accountId = payload.getAccountId();

        log.info("[USER] Received AccountCreatedEvent for {}", payload.getEmail());

        if (repository.existsById(accountId)) {
            log.info("[USER] Profile already exists → skip");
            return;
        }

        String username = generateUniqueUsername(payload.getEmail());

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

        log.info("[USER] Profile created successfully → username={}", username);
    }

    private String generateUniqueUsername(String email) {

        String base = email.split("@")[0]
                .replaceAll("[^a-zA-Z0-9._]", "")
                .toLowerCase();

        if (base.length() < 3) {
            base = base + "user";
        }

        String username = base;
        int suffix = 0;

        while (repository.existsByUsername(username)) {
            suffix++;
            username = base + suffix;
        }

        return username;
    }
}
