package com.example.common.integration.friendship;

public enum FriendshipEventType {

    FRIEND_REQUEST_SENT("friend.request.sent"),
    FRIEND_REQUEST_ACCEPTED("friend.request.accepted"),
    FRIEND_REQUEST_DECLINED("friend.request.declined"),
    FRIEND_REQUEST_CANCELLED("friend.request.cancelled"),
    FRIEND_UNFRIENDED("friend.unfriended"),
    FRIEND_BLOCKED("friend.blocked"),
    FRIEND_UNBLOCKED("friend.unblocked");

    private final String value;

    FriendshipEventType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}


