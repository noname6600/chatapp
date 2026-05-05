package com.example.presence.realtime.port;

import com.example.common.realtime.policy.RealtimeFlowId;
import java.util.UUID;

public interface PresenceRealtimePort {

    void publishGlobalEvent(String eventType, Object payload);

    void publishRoomEvent(UUID roomId, String eventType, Object payload);

    void publishUserEvent(String eventType, Object payload);

    /**
     * Publish a global event with explicit flow classification.
     * The adapter SHALL respect the flow's delivery semantics when routing to Kafka/Redis/WebSocket.
     *
     * @param eventType the event type name
     * @param payload the event payload
     * @param flowId the realtime flow identifier (durable-first, ephemeral-only, or mixed)
     */
    void publishGlobalEvent(String eventType, Object payload, RealtimeFlowId flowId);

    /**
     * Publish a room event with explicit flow classification.
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
     * @param eventType the event type name
     * @param payload the event payload
     * @param flowId the realtime flow identifier (durable-first, ephemeral-only, or mixed)
     */
    void publishUserEvent(String eventType, Object payload, RealtimeFlowId flowId);
}
