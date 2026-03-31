package com.example.common.integration.account;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.UUID;

@Getter
public final class AccountCreatedPayload {

    private final UUID accountId;
    private final String email;

    @JsonCreator
    public AccountCreatedPayload(
            @JsonProperty("accountId") UUID accountId,
            @JsonProperty("email") String email
    ) {
        this.accountId = accountId;
        this.email = email;
    }
}
