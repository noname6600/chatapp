package com.chatweb.common.realtime.policy;

/**
 * Unique identifiers for realtime flows across all services.
 *
 * Each flow SHALL correspond to a distinct realtime delivery path (producer -> broker -> consumer -> client).
 * Naming convention: SERVICE_FLOW_NAME_ACTION.
 */
public enum RealtimeFlowId {
    // Chat Service Flows
    CHAT_MESSAGE_CREATE("chat.message.create"),
    CHAT_MESSAGE_DELETE("chat.message.delete"),
    CHAT_MESSAGE_PIN("chat.message.pin"),
    CHAT_MESSAGE_UNPIN("chat.message.unpin"),
    CHAT_ROOM_CREATE("chat.room.create"),
    CHAT_ROOM_UPDATE("chat.room.update"),
    CHAT_ROOM_MEMBER_ADD("chat.room.member.add"),
    CHAT_ROOM_MEMBER_REMOVE("chat.room.member.remove"),
    CHAT_ROOM_MEMBER_LIST_UPDATE("chat.room.member.list-update"),

    // Notification Service Flows
    NOTIFICATION_PUSH("notification.push"),
    NOTIFICATION_DISMISS("notification.dismiss"),

    // Friendship Service Flows
    FRIENDSHIP_REQUEST_CREATED("friendship.request.created"),
    FRIENDSHIP_REQUEST_ACCEPTED("friendship.request.accepted"),
    FRIENDSHIP_REQUEST_DECLINED("friendship.request.declined"),
    FRIENDSHIP_STATUS_UPDATE("friendship.status.update"),

    // Presence Service Flows
    PRESENCE_USER_ONLINE("presence.user.online"),
    PRESENCE_USER_OFFLINE("presence.user.offline"),
    PRESENCE_USER_STATUS_CHANGED("presence.user.status-changed"),
    PRESENCE_USER_TYPING("presence.user.typing"),
    PRESENCE_USER_STOP_TYPING("presence.user.stop-typing"),
    PRESENCE_ROOM_ACTIVITY("presence.room.activity");

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
