package com.chatweb.common.kafka.topic;

/**
 * Kafka route constants owned by common-kafka.
 *
 * <p>Contains only true transport routes (topics that do not correspond 1:1 to event types
 * in {@code common-events}). These include:
 *
 * <ul>
 *   <li>Aggregate routes: multiple event types are routed to a single topic
 *   <li>Infrastructure routes: system-level topics (dead-letter, retry)
 * </ul>
 *
 * <p>For semantic event routing, services should reference event type enums directly
 * from {@code common-events} (e.g., {@code AccountEventType.ACCOUNT_CREATED.value()}).
 * This ensures common-events remains the single authoritative owner of event semantics.
 */
public final class KafkaTopics {

    private KafkaTopics() {
    }

    // Friendship routes â€" aggregate: multiple event types are routed to a single topic
    public static final String TOPIC_FRIENDSHIP_EVENTS         = "friendship.events";
    public static final String TOPIC_FRIENDSHIP_REQUEST_EVENTS = "friendship.request.events";

    // Per-event-type routes â€" Java annotations require compile-time constants so these
    // mirror the values in common-events enums (AccountEventType, ChatEventType).
    public static final String TOPIC_ACCOUNT_CREATED       = "account.created";
    public static final String TOPIC_CHAT_MESSAGE_SENT     = "chat.message.sent";
    public static final String TOPIC_CHAT_MESSAGE_EVENTS   = "chat.message.events";
    public static final String TOPIC_CHAT_REACTION_UPDATED = "chat.reaction.updated";

    // Notification realtime delivery â€" carries pre-built WS payloads from notification-service
    public static final String TOPIC_NOTIFICATION_REALTIME = "notification.realtime";

    // Infrastructure routes â€" transport-specific, no matching event type in common-events
    public static final String TOPIC_SYSTEM_DEAD_LETTER = "system.dead-letter";
    // Voice routes
    public static final String TOPIC_VOICE_ROOM_EVENTS = "voice.room.events";
    public static final String TOPIC_VOICE_CALL_EVENTS = "voice.call.events";

    // Infrastructure routes
    public static final String TOPIC_SYSTEM_RETRY       = "system.retry";
}
