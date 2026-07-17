package com.chatweb.common.integration.voice;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum VoiceEventType {

    // Voice room events
    VOICE_ROOM_JOINED("voice.room.joined"),
    VOICE_ROOM_LEFT("voice.room.left"),
    VOICE_ROOM_CREATED("voice.room.created"),
    VOICE_ROOM_CLOSED("voice.room.closed"),

    // 1:1 call events
    CALL_INITIATED("voice.call.initiated"),
    CALL_ACCEPTED("voice.call.accepted"),
    CALL_DECLINED("voice.call.declined"),
    CALL_CANCELLED("voice.call.cancelled"),
    CALL_ENDED("voice.call.ended"),
    CALL_MISSED("voice.call.missed");

    private final String value;

    VoiceEventType(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static VoiceEventType fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Unknown VoiceEventType: null");
        }
        String lower = value.toLowerCase();
        for (VoiceEventType type : values()) {
            if (type.value.equals(lower)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown VoiceEventType: " + value);
    }
}
