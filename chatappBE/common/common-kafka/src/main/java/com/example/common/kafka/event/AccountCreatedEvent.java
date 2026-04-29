package com.example.common.integration.kafka.event;

import java.time.Instant;
import java.util.UUID;

import com.example.common.integration.account.AccountCreatedPayload;
import com.example.common.integration.account.AccountEventType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public final class AccountCreatedEvent extends AbstractKafkaEvent {

    private final AccountCreatedPayload payload;

    @JsonCreator
    public AccountCreatedEvent(
            @JsonProperty("sourceService") String sourceService,
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("createdAt") Instant createdAt,
            @JsonProperty("payload") AccountCreatedPayload payload
    ) {
        super(
                sourceService,
                AccountEventType.ACCOUNT_CREATED.value(),
                createdAt,
                eventId
        );
        this.payload = payload;
    }

    public static AccountCreatedEvent from(String sourceService,
                                           AccountCreatedPayload payload) {
        return new AccountCreatedEvent(sourceService, null, null, payload);
    }
}




