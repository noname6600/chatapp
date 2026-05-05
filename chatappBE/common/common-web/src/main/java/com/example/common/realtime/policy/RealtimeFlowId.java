package com.example.common.realtime.policy;

/**
 * Unique identifiers for realtime flows across all services.
 *
 * Each flow SHALL correspond to a distinct realtime delivery path (producer -> broker -> consumer -> client).
 * Naming convention: SERVICE_FLOW_NAME_ACTION.
 */
public enum RealtimeFlowId {
    // Chat Service Flows
    CHAT_MESSAGE_CREATE("chat_message_create"),
    CHAT_MESSAGE_DELETE("chat_message_delete"),
    CHAT_MESSAGE_PIN("chat_message_pin"),
    CHAT_MESSAGE_UNPIN("chat_message_unpin"),
    CHAT_ROOM_CREATE("chat_room_create"),
    CHAT_ROOM_UPDATE("chat_room_update"),
    CHAT_ROOM_MEMBER_ADD("chat_room_member_add"),
    CHAT_ROOM_MEMBER_REMOVE("chat_room_member_remove"),
    CHAT_ROOM_MEMBER_LIST_UPDATE("chat_room_member_list_update"),

    // Notification Service Flows
    NOTIFICATION_PUSH("notification_push"),
    NOTIFICATION_DISMISS("notification_dismiss"),

    // Friendship Service Flows
    FRIENDSHIP_REQUEST_CREATED("friendship_request_created"),
    FRIENDSHIP_REQUEST_ACCEPTED("friendship_request_accepted"),
    FRIENDSHIP_REQUEST_DECLINED("friendship_request_declined"),
    FRIENDSHIP_STATUS_UPDATE("friendship_status_update"),

    // Presence Service Flows
    PRESENCE_USER_ONLINE("presence_user_online"),
    PRESENCE_USER_OFFLINE("presence_user_offline"),
    PRESENCE_USER_STATUS_CHANGED("presence_user_status_changed"),
    PRESENCE_USER_TYPING("presence_user_typing"),
    PRESENCE_USER_STOP_TYPING("presence_user_stop_typing"),
    PRESENCE_ROOM_ACTIVITY("presence_room_activity");

    private final String flowId;

    RealtimeFlowId(String flowId) {
        this.flowId = flowId;
    }

    public String getFlowId() {
        return flowId;
    }

    public static RealtimeFlowId fromFlowId(String flowId) {
        for (RealtimeFlowId id : values()) {
            if (id.flowId.equals(flowId)) {
                return id;
            }
        }
        throw new IllegalArgumentException("Unknown flow ID: " + flowId);
    }
}
