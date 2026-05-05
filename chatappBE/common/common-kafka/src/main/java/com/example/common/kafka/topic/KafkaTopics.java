package com.example.common.kafka.topic;

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

    // Friendship routes — aggregate: multiple event types are routed to a single topic
    public static final String TOPIC_FRIENDSHIP_EVENTS         = "friendship.events";
    public static final String TOPIC_FRIENDSHIP_REQUEST_EVENTS = "friendship.request.events";

    // Infrastructure routes — transport-specific, no matching event type in common-events
    public static final String TOPIC_SYSTEM_DEAD_LETTER = "system.dead-letter";
    public static final String TOPIC_SYSTEM_RETRY       = "system.retry";
}
