package com.example.chat.realtime.port;

import com.example.common.realtime.policy.RealtimeFlowId;
import java.util.UUID;

public interface ChatRealtimePort {

    void publishRoomEvent(UUID roomId, String eventType, Object payload);

    void publishUserEvent(UUID userId, String eventType, Object payload);

    /**
     * Publish a room event with explicit flow classification.
     * The adapter SHALL respect the flow's delivery semantics when routing to Kafka/Redis/WebSocket.
     *
     * @param roomId the room identifier
     * @param eventType the event type name
     * @param payload the event payload
     * @param flowId the realtime flow identifier (durable-first, ephemeral-only, or mixed)
     */
    void publishRoomEvent(UUID roomId, String eventType, Object payload, RealtimeFlowId flowId);

    /**
     * Publish a user event with explicit flow classification.
     *
     * @param userId the user identifier
     * @param eventType the event type name
     * @param payload the event payload
     * @param flowId the realtime flow identifier (durable-first, ephemeral-only, or mixed)
     */
    void publishUserEvent(UUID userId, String eventType, Object payload, RealtimeFlowId flowId);
}
