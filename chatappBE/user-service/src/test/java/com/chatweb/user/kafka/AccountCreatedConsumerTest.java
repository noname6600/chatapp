package com.chatweb.user.infrastructure.kafka;

import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.event.EventMetadata;
import com.chatweb.common.integration.account.AccountCreatedPayload;
import com.chatweb.user.application.UserKafkaAccountCreatedApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AccountCreatedConsumerTest {

    private UserKafkaAccountCreatedApplicationService applicationService;
    private AccountCreatedConsumer consumer;

    @BeforeEach
    void setUp() {
        applicationService = mock(UserKafkaAccountCreatedApplicationService.class);
        consumer = new AccountCreatedConsumer(applicationService);
    }

    @Test
    void listen_delegatesToApplicationService() {
        UUID accountId = UUID.randomUUID();
        EventEnvelope<AccountCreatedPayload> envelope = buildEnvelope(accountId, "existing@example.com");

        consumer.listen(envelope);

        verify(applicationService).handleAccountCreated(envelope.payload());
    }

    private EventEnvelope<AccountCreatedPayload> buildEnvelope(UUID accountId, String email) {
        String eventId = UUID.randomUUID().toString();
        EventMetadata metadata = new EventMetadata(
            eventId,
            "account.created",
            "auth-service",
            Instant.now(),
            eventId
        );
        AccountCreatedPayload payload = new AccountCreatedPayload(accountId, email);
        return new EventEnvelope<>(metadata, payload);
    }
}
