package com.example.common.realtime.policy;

/**
 * Classification of realtime delivery semantics for event flows.
 *
 * Each realtime flow SHALL declare whether it is durable-first (requires Kafka persistence before fanout),
 * ephemeral-only (transient, no persistence), or mixed-with-convergence (durable + convergence fetch for consistency).
 */
public enum RealtimeFlowType {
    /**
     * DURABLE-FIRST: Fanout publication occurs only after durable event publication is accepted.
     *
     * Use for: messages, notifications, room/friendship state mutations that require audit trail
     * and replay capability.
     *
     * Guarantees: event is persisted and replayable; fanout is eventual but durable.
     * Risks: higher latency from dual publish; requires idempotency in consumers.
     */
    DURABLE_FIRST("durable-first"),

    /**
     * EPHEMERAL-ONLY: Direct Redis/WebSocket fanout, no Kafka durability.
     *
     * Use for: typing indicators, online/offline presence, room activity that is inherently transient.
     *
     * Guarantees: none beyond best-effort Redis pub/sub; no replay capability.
     * Risks: loss under broker failure; requires reconnect/convergence fetch by client.
     */
    EPHEMERAL_ONLY("ephemeral-only"),

    /**
     * MIXED-WITH-CONVERGENCE: Both durable publication (Kafka) and fanout (Redis),
     * with client-initiated convergence fetch to resolve missing updates under unstable connectivity.
     *
     * Use for: member list, room profile updates, and other semi-persistent state where
     * occasional losses can be tolerated but eventual consistency is required.
     *
     * Guarantees: events are durable and fanout is attempted; clients can fetch on reconnect
     * to converge to true state.
     * Risks: bounded reconnect/fetch overhead; requires idempotency and convergence logic.
     */
    MIXED_WITH_CONVERGENCE("mixed-with-convergence");

    private final String label;

    RealtimeFlowType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static RealtimeFlowType fromLabel(String label) {
        for (RealtimeFlowType type : values()) {
            if (type.label.equals(label)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown flow type: " + label);
    }
}
