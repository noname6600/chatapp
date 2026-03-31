package com.example.common.integration.presence;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PresenceMode {

    AUTO("AUTO"),
    MANUAL("MANUAL");

    private final String value;

    PresenceMode(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static PresenceMode fromValue(String value) {
        for (PresenceMode mode : values()) {
            if (mode.value.equalsIgnoreCase(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown PresenceMode: " + value);
    }
}