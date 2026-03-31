package com.example.common.integration.presence;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PresenceStatus {

    ONLINE("ONLINE"),
    AWAY("AWAY"),
    OFFLINE("OFFLINE");

    private final String value;

    PresenceStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static PresenceStatus fromValue(String value) {
        for (PresenceStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown PresenceStatus: " + value);
    }
}