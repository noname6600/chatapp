package com.chatweb.auth.service.event;

import com.chatweb.auth.entity.Account;
import com.chatweb.auth.infrastructure.kafka.AccountCreatedEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AccountCreatedAfterCommitPublisher {

    private final AccountCreatedEventProducer accountCreatedEventProducer;
    private final AfterCommitExecutor afterCommitExecutor;

    public void publishAfterCommit(Account account) {
        afterCommitExecutor.runAfterCommit("account_created_publish", () -> {
            boolean published = accountCreatedEventProducer.publish(account);
            if (!published) {
                log.warn(
                        "account_created_publish_failed accountId={} email={}",
                        account.getId(),
                        account.getEmail()
                );
            }
        });
    }
}
