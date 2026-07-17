package com.chatweb.realtime.dispatch;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Internal edge-to-edge handoff event for cross-instance websocket delivery.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EdgeDeliveryHandoffEvent {
    public static final String REDIS_EVENT_TYPE = "realtime.edge.handoff";
    public static final String REDIS_CHANNEL_PREFIX = "realtime.edge.handoff";

    private String handoffEventId;
    private Instant createdAt;
    private String sourceInstanceId;
    private String targetInstanceId;

    private String deliveryType;
    private String eventType;
    private String originalEventId;

    private String subscriptionKey;
    private String targetUserId;
    private List<String> targetSessionIds;

    private JsonNode payload;
}
