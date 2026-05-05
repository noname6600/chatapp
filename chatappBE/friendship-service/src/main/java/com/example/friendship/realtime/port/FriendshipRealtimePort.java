package com.example.friendship.realtime.port;

import com.example.common.realtime.policy.RealtimeFlowId;
import java.util.UUID;

public interface FriendshipRealtimePort {

    void publishUserEvent(UUID userId, String eventType, Object payload);

    void publishRelationshipEvent(UUID userA, UUID userB, String eventType, Object payload);

    /**
     * Publish a user event with explicit flow classification.
     * The adapter SHALL respect the flow's delivery semantics when routing to Kafka/Redis/WebSocket.
     *
     * @param userId the user identifier
     * @param eventType the event type name
     * @param payload the event payload
     * @param flowId the realtime flow identifier (durable-first, ephemeral-only, or mixed)
     */
    void publishUserEvent(UUID userId, String eventType, Object payload, RealtimeFlowId flowId);

    /**
     * Publish a relationship event with explicit flow classification.
     *
     * @param userA first user identifier
     * @param userB second user identifier
     * @param eventType the event type name
     * @param payload the event payload
     * @param flowId the realtime flow identifier (durable-first, ephemeral-only, or mixed)
     */
    void publishRelationshipEvent(UUID userA, UUID userB, String eventType, Object payload, RealtimeFlowId flowId);
}
