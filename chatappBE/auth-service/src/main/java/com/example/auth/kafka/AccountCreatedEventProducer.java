package com.example.auth.kafka;

import com.example.common.integration.account.AccountCreatedPayload;
import com.example.common.kafka.Topics;
import com.example.common.kafka.api.KafkaEventPublisher;
import com.example.common.kafka.event.AccountCreatedEvent;
import com.example.auth.entity.Account;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountCreatedEventProducer {

    private final KafkaEventPublisher kafkaEventPublisher;

    @Value("${spring.application.name}")
    private String sourceService;

    public boolean publish(Account account) {
        try {
            kafkaEventPublisher.publish(
                Topics.ACCOUNT_CREATED,
                account.getId().toString(),
                AccountCreatedEvent.from(
                    sourceService,
                    new AccountCreatedPayload(account.getId(), account.getEmail())
                )
            );
            return true;
        } catch (Exception e) {
            log.warn("Failed to publish account-created event for accountId={}: {}",
                account.getId(), e.getMessage());
            return false;
        }
    }
}
