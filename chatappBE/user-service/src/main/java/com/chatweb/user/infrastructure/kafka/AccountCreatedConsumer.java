package com.chatweb.user.infrastructure.kafka;

import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.integration.account.AccountCreatedPayload;
import com.chatweb.common.kafka.topic.KafkaTopics;
import com.chatweb.user.application.UserKafkaAccountCreatedApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class AccountCreatedConsumer {

    private final UserKafkaAccountCreatedApplicationService accountCreatedApplicationService;

    @KafkaListener(topics = KafkaTopics.TOPIC_ACCOUNT_CREATED)
    public void listen(EventEnvelope<AccountCreatedPayload> envelope) {
        var payload = envelope.payload();
        UUID accountId = payload.getAccountId();

        log.info("[USER] Received AccountCreated for {}", payload.getEmail());

        accountCreatedApplicationService.handleAccountCreated(payload);
    }
}
