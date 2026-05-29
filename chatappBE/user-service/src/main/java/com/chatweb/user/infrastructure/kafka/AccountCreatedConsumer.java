package com.chatweb.user.infrastructure.kafka;

import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.integration.account.AccountCreatedPayload;
import com.chatweb.common.kafka.consumer.KafkaEventHandler;
import com.chatweb.common.kafka.consumer.KafkaEventDispatcher;
import com.chatweb.common.kafka.topic.KafkaTopics;
import com.chatweb.user.application.UserKafkaAccountCreatedApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AccountCreatedConsumer implements KafkaEventHandler<AccountCreatedPayload> {

    private final UserKafkaAccountCreatedApplicationService accountCreatedApplicationService;
    private final KafkaEventDispatcher dispatcher;

    @Override
    public String eventType() {
        return KafkaTopics.TOPIC_ACCOUNT_CREATED;
    }

    @Override
    public void handle(EventEnvelope<AccountCreatedPayload> event) {
        AccountCreatedPayload payload = event.payload();
        log.info("[USER] AccountCreated for {}", payload.getEmail());
        accountCreatedApplicationService.handleAccountCreated(payload);
    }

    @KafkaListener(topics = KafkaTopics.TOPIC_ACCOUNT_CREATED, groupId = "user-service")
    public void listen(EventEnvelope<?> envelope) {
        if (envelope == null || envelope.metadata() == null) return;
        dispatcher.dispatch(envelope);
    }
}
