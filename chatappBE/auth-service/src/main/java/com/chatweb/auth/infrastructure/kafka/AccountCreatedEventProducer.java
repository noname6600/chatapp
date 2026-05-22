package com.chatweb.auth.infrastructure.kafka;

import com.chatweb.common.integration.account.AccountCreatedPayload;
import com.chatweb.common.integration.account.AccountEventType;
import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.event.EventMetadata;
import com.chatweb.common.event.TraceContext;
import com.chatweb.common.kafka.producer.KafkaEventPublisher;
import com.chatweb.auth.entity.Account;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountCreatedEventProducer {

    private final KafkaEventPublisher kafkaEventPublisher;

    @Value("${spring.application.name}")
    private String sourceService;

    public boolean publish(Account account) {
        try {
            String eventId = UUID.randomUUID().toString();
            EventMetadata metadata = new EventMetadata(
                eventId,
                AccountEventType.ACCOUNT_CREATED.value(),
                sourceService,
                Instant.now(),
                 TraceContext.correlationIdOrEventId(eventId)
            );
            
            EventEnvelope<AccountCreatedPayload> envelope = new EventEnvelope<>(
                metadata,
                new AccountCreatedPayload(account.getId(), account.getEmail())
            );
            
            kafkaEventPublisher.publish(
                AccountEventType.ACCOUNT_CREATED.value(),
                account.getId().toString(),
                envelope
            );
            return true;
        } catch (Exception e) {
            log.warn("Failed to publish account-created event for accountId={}: {}",
                account.getId(), e.getMessage());
            return false;
        }
    }
}
