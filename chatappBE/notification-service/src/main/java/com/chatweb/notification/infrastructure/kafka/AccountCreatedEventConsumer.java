package com.chatweb.notification.infrastructure.kafka;

import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.integration.account.AccountCreatedPayload;
import com.chatweb.common.kafka.topic.KafkaTopics;
import com.chatweb.notification.service.impl.NotificationDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AccountCreatedEventConsumer {

    private final NotificationDomainService notificationService;

    @KafkaListener(topics = KafkaTopics.TOPIC_ACCOUNT_CREATED, groupId = NotificationKafkaConsumerGroups.DEFAULT)
    public void listen(EventEnvelope<AccountCreatedPayload> envelope) {

        var payload = envelope.payload();

        log.info("[NOTI] New account created: {}", payload.getEmail());

        notificationService.notifyWelcome(
                payload.getAccountId(),
                payload.getEmail()
        );
    }
}

