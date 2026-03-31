package com.example.common.integration.account;

public enum AccountEventType {

    ACCOUNT_CREATED("account.account.created"),
    ACCOUNT_DELETED("account.account.deleted"),
    ACCOUNT_DISABLED("account.account.disabled");

    private final String value;

    AccountEventType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
