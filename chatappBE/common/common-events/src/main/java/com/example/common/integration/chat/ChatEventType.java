package com.example.common.integration.chat;

public enum ChatEventType {

    MESSAGE_SENT("chat.message.sent"),
    MESSAGE_EDITED("chat.message.edited"),
    MESSAGE_DELETED("chat.message.deleted"),
    MESSAGE_PINNED("chat.message.pinned"),
    MESSAGE_UNPINNED("chat.message.unpinned"),
    REACTION_UPDATED("chat.reaction.updated"),
    MEMBER_JOINED("chat.room.member.joined"),
    MEMBER_LEFT("chat.room.member.left"),
    MEMBER_REMOVED("chat.room.member.removed");

    private final String value;

    ChatEventType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
