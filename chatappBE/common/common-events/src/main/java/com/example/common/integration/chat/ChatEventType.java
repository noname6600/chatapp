package com.example.common.integration.chat;

public enum ChatEventType {

    MESSAGE_SENT("chat.message.sent"),
    MESSAGE_EDITED("chat.message.edited"),
    MESSAGE_DELETED("chat.message.deleted"),
    REACTION_UPDATED("chat.reaction.updated");

    private final String value;

    ChatEventType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
