package com.example.notification.kafka;

import com.example.common.kafka.topic.KafkaTopics;
import com.example.common.integration.kafka.event.AccountCreatedEvent;
import com.example.notification.service.impl.NotificationDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AccountCreatedEventConsumer {

    private final NotificationDomainService notificationService;

    @KafkaListener(topics = KafkaTopics.ACCOUNT_CREATED)
    public void listen(AccountCreatedEvent event) {

        var payload = event.getPayload();

        log.info("[NOTI] New account created: {}", payload.getEmail());

        notificationService.notifyWelcome(
                payload.getAccountId(),
                payload.getEmail()
        );
    }
}

