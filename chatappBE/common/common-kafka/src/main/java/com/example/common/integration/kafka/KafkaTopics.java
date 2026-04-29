package com.example.common.integration.kafka;

public final class KafkaTopics {

    private KafkaTopics() {}

    // ========= ACCOUNT =========
    public static final String ACCOUNT_CREATED = "account.account.created";
    public static final String ACCOUNT_DELETED = "account.account.deleted";
    public static final String ACCOUNT_DISABLED = "account.account.disabled";

    // ========= USER =========
    public static final String USER_PROFILE_CREATED = "user.profile.created";
    public static final String USER_PROFILE_UPDATED = "user.profile.updated";

    // ========= FRIEND =========
    public static final String FRIENDSHIP_EVENTS = "friendship.events";
    public static final String FRIENDSHIP_REQUEST_EVENTS = "friendship.request.events";

    // ========= CHAT =========
    public static final String CHAT_MESSAGE_SENT = "chat.message.sent";
    public static final String CHAT_MESSAGE_EDITED = "chat.message.edited";
    public static final String CHAT_MESSAGE_DELETED = "chat.message.deleted";

    // ========= REACTION =========
    public static final String CHAT_REACTION_UPDATED = "chat.reaction.updated";

    // ========= NOTIFICATION =========
    public static final String NOTIFICATION_REQUESTED = "notification.requested";
    public static final String NOTIFICATION_SENT = "notification.sent";

    // ========= INTERNAL =========
    public static final String DEAD_LETTER = "system.dead-letter";
    public static final String RETRY = "system.retry";
}
