package com.example.common.realtime.policy;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Centralized realtime flow classification policy.
 *
 * Encodes the delivery semantics (durable-first, ephemeral-only, mixed-with-convergence)
 * for each realtime flow in the system.
 *
 * This policy SHALL be used by:
 * - Application services to decide on publish/fanout orchestration
 * - Infrastructure adapters to validate and enforce delivery guarantees
 * - Integration tests to verify flow behavior against declared semantics
 */
public class RealtimeFlowClassificationPolicy {

    private static final Map<RealtimeFlowId, RealtimeFlowType> FLOW_CLASSIFICATION;

    static {
        Map<RealtimeFlowId, RealtimeFlowType> classification = new EnumMap<>(RealtimeFlowId.class);

        // Chat Service: Message and room operations are durable-first (require audit trail)
        classification.put(RealtimeFlowId.CHAT_MESSAGE_CREATE, RealtimeFlowType.DURABLE_FIRST);
        classification.put(RealtimeFlowId.CHAT_MESSAGE_DELETE, RealtimeFlowType.DURABLE_FIRST);
        classification.put(RealtimeFlowId.CHAT_MESSAGE_PIN, RealtimeFlowType.DURABLE_FIRST);
        classification.put(RealtimeFlowId.CHAT_MESSAGE_UNPIN, RealtimeFlowType.DURABLE_FIRST);
        classification.put(RealtimeFlowId.CHAT_ROOM_CREATE, RealtimeFlowType.DURABLE_FIRST);
        classification.put(RealtimeFlowId.CHAT_ROOM_UPDATE, RealtimeFlowType.DURABLE_FIRST);

        // Chat Service: Member list is mixed (durable + convergence) to handle eventual consistency
        classification.put(RealtimeFlowId.CHAT_ROOM_MEMBER_ADD, RealtimeFlowType.MIXED_WITH_CONVERGENCE);
        classification.put(RealtimeFlowId.CHAT_ROOM_MEMBER_REMOVE, RealtimeFlowType.MIXED_WITH_CONVERGENCE);
        classification.put(RealtimeFlowId.CHAT_ROOM_MEMBER_LIST_UPDATE, RealtimeFlowType.MIXED_WITH_CONVERGENCE);

        // Notification Service: Notifications are durable-first (user-critical, require replay)
        classification.put(RealtimeFlowId.NOTIFICATION_PUSH, RealtimeFlowType.DURABLE_FIRST);
        classification.put(RealtimeFlowId.NOTIFICATION_DISMISS, RealtimeFlowType.DURABLE_FIRST);

        // Friendship Service: Status changes are durable-first (relationship state is persistent)
        classification.put(RealtimeFlowId.FRIENDSHIP_REQUEST_CREATED, RealtimeFlowType.DURABLE_FIRST);
        classification.put(RealtimeFlowId.FRIENDSHIP_REQUEST_ACCEPTED, RealtimeFlowType.DURABLE_FIRST);
        classification.put(RealtimeFlowId.FRIENDSHIP_REQUEST_DECLINED, RealtimeFlowType.DURABLE_FIRST);
        classification.put(RealtimeFlowId.FRIENDSHIP_STATUS_UPDATE, RealtimeFlowType.DURABLE_FIRST);

        // Presence Service: Online/offline is ephemeral (transient state, no persistence needed)
        classification.put(RealtimeFlowId.PRESENCE_USER_ONLINE, RealtimeFlowType.EPHEMERAL_ONLY);
        classification.put(RealtimeFlowId.PRESENCE_USER_OFFLINE, RealtimeFlowType.EPHEMERAL_ONLY);
        classification.put(RealtimeFlowId.PRESENCE_USER_STATUS_CHANGED, RealtimeFlowType.EPHEMERAL_ONLY);

        // Presence Service: Typing is ephemeral (transient, bounded by reconnect)
        classification.put(RealtimeFlowId.PRESENCE_USER_TYPING, RealtimeFlowType.EPHEMERAL_ONLY);
        classification.put(RealtimeFlowId.PRESENCE_USER_STOP_TYPING, RealtimeFlowType.EPHEMERAL_ONLY);

        // Presence Service: Room activity is ephemeral (transient, convergence on demand)
        classification.put(RealtimeFlowId.PRESENCE_ROOM_ACTIVITY, RealtimeFlowType.EPHEMERAL_ONLY);

        FLOW_CLASSIFICATION = Collections.unmodifiableMap(classification);
    }

    /**
     * Get the declared delivery semantics for a realtime flow.
     *
     * @param flowId the flow identifier
     * @return the flow type (durable-first, ephemeral-only, or mixed)
     * @throws IllegalArgumentException if the flow is not classified
     */
    public static RealtimeFlowType getFlowType(RealtimeFlowId flowId) {
        RealtimeFlowType type = FLOW_CLASSIFICATION.get(flowId);
        if (type == null) {
            throw new IllegalArgumentException("Flow not classified: " + flowId);
        }
        return type;
    }

    /**
     * Check if a flow has durable-first semantics.
     *
     * @param flowId the flow identifier
     * @return true if the flow requires Kafka durability before fanout
     */
    public static boolean isDurableFirst(RealtimeFlowId flowId) {
        return getFlowType(flowId) == RealtimeFlowType.DURABLE_FIRST;
    }

    /**
     * Check if a flow has ephemeral-only semantics.
     *
     * @param flowId the flow identifier
     * @return true if the flow is non-durable Redis/WebSocket only
     */
    public static boolean isEphemeralOnly(RealtimeFlowId flowId) {
        return getFlowType(flowId) == RealtimeFlowType.EPHEMERAL_ONLY;
    }

    /**
     * Check if a flow has mixed-with-convergence semantics.
     *
     * @param flowId the flow identifier
     * @return true if the flow requires both durability and convergence fetch
     */
    public static boolean isMixedWithConvergence(RealtimeFlowId flowId) {
        return getFlowType(flowId) == RealtimeFlowType.MIXED_WITH_CONVERGENCE;
    }

    /**
     * Get immutable map of all flow classifications.
     *
     * @return map of flow ID to flow type
     */
    public static Map<RealtimeFlowId, RealtimeFlowType> getAllClassifications() {
        return FLOW_CLASSIFICATION;
    }
}
