package com.example.common.integration.presence;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PresenceEventType {

    USER_ONLINE("presence.user.online"),
    USER_OFFLINE("presence.user.offline"),
    USER_STATUS_CHANGED("presence.user.status_changed"),
    USER_HEARTBEAT("presence.user.heartbeat"),

    ROOM_TYPING("presence.room.typing"),
    ROOM_STOP_TYPING("presence.room.stop_typing"),

    ROOM_JOIN("presence.room.join"),
    ROOM_LEAVE("presence.room.leave"),

    GLOBAL_ONLINE_USERS("presence.global.online_users"),
    ROOM_ONLINE_USERS("presence.room.online_users");

    private final String value;

    PresenceEventType(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static PresenceEventType fromValue(String value) {
        for (PresenceEventType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown PresenceEventType: " + value);
    }
}
