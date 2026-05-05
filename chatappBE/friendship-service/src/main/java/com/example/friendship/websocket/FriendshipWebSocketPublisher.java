package com.example.friendship.websocket;

import com.example.common.integration.friendship.FriendRequestEvent;
import com.example.common.integration.friendship.FriendshipEventType;
import com.example.common.integration.friendship.FriendshipPayload;
import com.example.common.realtime.policy.RealtimeFlowClassificationPolicy;
import com.example.common.realtime.policy.RealtimeFlowId;
import com.example.common.realtime.policy.RealtimeFlowType;
import com.example.common.websocket.protocol.RealtimeWsEvent;
import com.example.friendship.realtime.port.FriendshipRealtimePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class FriendshipWebSocketPublisher implements FriendshipRealtimePort {

    private static final String WS_FRIEND_REQUEST_RECEIVED = "friendship.request.received";
    private static final String WS_FRIEND_REQUEST_ACCEPTED = "friendship.request.accepted";
    private static final String WS_FRIEND_REQUEST_DECLINED = "friendship.request.declined";
    private static final String WS_FRIEND_REQUEST_CANCELLED = "friendship.request.cancelled";
    private static final String WS_FRIEND_STATUS_CHANGED = "friendship.status.changed";

    private final WebSocketFriendshipBroadcaster broadcaster;

    @Override
    public void publishUserEvent(UUID userId, String eventType, Object payload) {
        broadcaster.sendToUser(
                userId,
                RealtimeWsEvent.builder()
                    .type(eventType)
                        .payload(payload)
                        .build()
        );
    }

    @Override
    public void publishRelationshipEvent(UUID userA, UUID userB, String eventType, Object payload) {
        if (payload instanceof FriendRequestEvent friendRequestEvent) {
            publishFriendRequestEvent(friendRequestEvent);
            return;
        }

        if (payload instanceof FriendshipPayload friendshipPayload) {
            FriendshipEventType normalized = java.util.Arrays.stream(FriendshipEventType.values())
                    .filter(type -> type.value().equals(eventType))
                    .findFirst()
                    .orElse(null);
            if (normalized == null) {
                log.warn("[FRIEND] Unknown friendship event type: {}", eventType);
                return;
            }
            publishFriendshipStatusChange(normalized, friendshipPayload);
            return;
        }

        // Fallback behavior for simple relationship fanout payloads.
        RealtimeWsEvent wsMessage = RealtimeWsEvent.builder()
            .type(eventType)
            .payload(payload)
            .build();
        broadcaster.sendToUser(userA, wsMessage);
        broadcaster.sendToUser(userB, wsMessage);
    }

    @Override
    public void publishUserEvent(UUID userId, String eventType, Object payload, RealtimeFlowId flowId) {
        RealtimeFlowType flowType = RealtimeFlowClassificationPolicy.getFlowType(flowId);
        
        log.debug("Publishing user event for flow {}: eventType={}, flowType={}", 
                flowId, eventType, flowType);

        // Enforce delivery semantics based on flow classification
        if (flowType == RealtimeFlowType.DURABLE_FIRST) {
            // DURABLE-FIRST: publish to Kafka first, then fanout
            // TODO: Implement Kafka publish in task 6.2/7.3
            publishDurableFirstFlow(userId, eventType, payload);
        } else if (flowType == RealtimeFlowType.EPHEMERAL_ONLY) {
            // EPHEMERAL-ONLY: direct Redis/WebSocket fanout
            publishEphemeralOnlyFlow(userId, eventType, payload);
        } else if (flowType == RealtimeFlowType.MIXED_WITH_CONVERGENCE) {
            // MIXED-WITH-CONVERGENCE: publish to both Kafka and fanout
            // TODO: Implement Kafka publish in task 6.2/7.3
            publishMixedConvergenceFlow(userId, eventType, payload);
        }
    }

    @Override
    public void publishRelationshipEvent(UUID userA, UUID userB, String eventType, Object payload, RealtimeFlowId flowId) {
        RealtimeFlowType flowType = RealtimeFlowClassificationPolicy.getFlowType(flowId);
        
        log.debug("Publishing relationship event for flow {}: eventType={}, flowType={}", 
                flowId, eventType, flowType);

        // Enforce delivery semantics based on flow classification
        if (flowType == RealtimeFlowType.DURABLE_FIRST) {
            // DURABLE-FIRST: publish to Kafka first, then fanout
            // TODO: Implement Kafka publish in task 6.2/7.3
            publishDurableFirstRelationshipFlow(userA, userB, eventType, payload);
        } else if (flowType == RealtimeFlowType.EPHEMERAL_ONLY) {
            // EPHEMERAL-ONLY: direct Redis/WebSocket fanout
            publishEphemeralOnlyRelationshipFlow(userA, userB, eventType, payload);
        } else if (flowType == RealtimeFlowType.MIXED_WITH_CONVERGENCE) {
            // MIXED-WITH-CONVERGENCE: publish to both Kafka and fanout
            // TODO: Implement Kafka publish in task 6.2/7.3
            publishMixedConvergenceRelationshipFlow(userA, userB, eventType, payload);
        }
    }

    private void publishDurableFirstFlow(UUID userId, String eventType, Object payload) {
        directFanoutUser(userId, eventType, payload);
    }

    private void publishEphemeralOnlyFlow(UUID userId, String eventType, Object payload) {
        directFanoutUser(userId, eventType, payload);
    }

    private void publishMixedConvergenceFlow(UUID userId, String eventType, Object payload) {
        directFanoutUser(userId, eventType, payload);
    }

    private void publishDurableFirstRelationshipFlow(UUID userA, UUID userB, String eventType, Object payload) {
        directFanoutRelationship(userA, userB, eventType, payload);
    }

    private void publishEphemeralOnlyRelationshipFlow(UUID userA, UUID userB, String eventType, Object payload) {
        directFanoutRelationship(userA, userB, eventType, payload);
    }

    private void publishMixedConvergenceRelationshipFlow(UUID userA, UUID userB, String eventType, Object payload) {
        directFanoutRelationship(userA, userB, eventType, payload);
    }

    private void directFanoutUser(UUID userId, String eventType, Object payload) {
        broadcaster.sendToUser(
                userId,
                RealtimeWsEvent.builder()
                    .type(eventType)
                        .payload(payload)
                        .build()
        );
    }

    private void directFanoutRelationship(UUID userA, UUID userB, String eventType, Object payload) {
        if (payload instanceof FriendRequestEvent friendRequestEvent) {
            publishFriendRequestEvent(friendRequestEvent);
            return;
        }

        if (payload instanceof FriendshipPayload friendshipPayload) {
            FriendshipEventType normalized = java.util.Arrays.stream(FriendshipEventType.values())
                    .filter(type -> type.value().equals(eventType))
                    .findFirst()
                    .orElse(null);
            if (normalized == null) {
                log.warn("[FRIEND] Unknown friendship event type: {}", eventType);
                return;
            }
            publishFriendshipStatusChange(normalized, friendshipPayload);
            return;
        }

        RealtimeWsEvent wsMessage = RealtimeWsEvent.builder()
            .type(eventType)
            .payload(payload)
            .build();
        broadcaster.sendToUser(userA, wsMessage);
        broadcaster.sendToUser(userB, wsMessage);
    }

    /**
     * Publishes friend request events (SENT, ACCEPTED) to affected users via WebSocket
     * For SENT events: only notify the recipient (sender already knows they sent it)
     * For ACCEPTED events: notify both users
     */
    public void publishFriendRequestEvent(FriendRequestEvent event) {
        String messageType = mapRequestEventToWsType(event.getType());
        if (messageType == null) {
            log.warn("[FRIEND] Unknown friend request event type: {}", event.getType());
            return;
        }

        Map<String, Object> payload = buildFriendRequestPayload(event);
        RealtimeWsEvent wsMessage = RealtimeWsEvent.builder()
            .type(messageType)
            .payload(payload)
            .build();

        if (event.getType() == FriendRequestEvent.Type.SENT) {
            // For outgoing requests: only notify recipient, not sender
            // (sender already knows they sent it; don't increment their own unread badge)
            broadcaster.sendToUser(event.getRecipientId(), wsMessage);
            log.info("[FRIEND] Published {} to recipient: {}", messageType, event.getRecipientId());
        } else {
            // For ACCEPTED events: notify both users
            broadcaster.sendToUser(event.getRecipientId(), wsMessage);
            broadcaster.sendToUser(event.getSenderId(), wsMessage);
            log.info("[FRIEND] Published {} to users: {} and {}", 
                    messageType, event.getRecipientId(), event.getSenderId());
        }
    }

    /**
     * Publishes friendship status changes (ACCEPTED, DECLINED, BLOCKED, UNFRIENDED, etc.)
     * to both affected users via WebSocket
     */
    public void publishFriendshipStatusChange(FriendshipEventType eventType, FriendshipPayload payload) {
        String messageType = mapFriendshipEventToWsType(eventType);
        if (messageType == null) {
            log.warn("[FRIEND] Skipping non-broadcastable event: {}", eventType);
            return;
        }

        Map<String, Object> wsPayload = buildFriendshipStatusPayload(eventType, payload);
        RealtimeWsEvent wsMessage = RealtimeWsEvent.builder()
            .type(messageType)
            .payload(wsPayload)
            .build();

        // Send to both users
        broadcaster.sendToUser(payload.getUserLow(), wsMessage);
        broadcaster.sendToUser(payload.getUserHigh(), wsMessage);

        log.info("[FRIEND] Published {} to users: {} and {}",
                messageType, payload.getUserLow(), payload.getUserHigh());
    }

    /**
     * Maps FriendRequestEvent.Type to WebSocket message type
     */
    private String mapRequestEventToWsType(FriendRequestEvent.Type requestType) {
        return switch (requestType) {
            case SENT -> WS_FRIEND_REQUEST_RECEIVED;
            case ACCEPTED -> WS_FRIEND_REQUEST_ACCEPTED;
            default -> null;
        };
    }

    /**
     * Maps FriendshipEventType to WebSocket message type.
     * Returns null for events that should not be broadcast (e.g., SENT already handled by request events)
     */
    private String mapFriendshipEventToWsType(FriendshipEventType eventType) {
        return switch (eventType) {
            case FRIEND_REQUEST_ACCEPTED -> WS_FRIEND_REQUEST_ACCEPTED;
            case FRIEND_REQUEST_DECLINED -> WS_FRIEND_REQUEST_DECLINED;
            case FRIEND_REQUEST_CANCELLED -> WS_FRIEND_REQUEST_CANCELLED;
            case FRIEND_UNFRIENDED, FRIEND_BLOCKED, FRIEND_UNBLOCKED -> WS_FRIEND_STATUS_CHANGED;
            case FRIEND_REQUEST_SENT -> null; // Handled by FriendRequestEventConsumer
            default -> null;
        };
    }

    /**
     * Builds payload for friend request events
     */
    private Map<String, Object> buildFriendRequestPayload(FriendRequestEvent event) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("senderId", event.getSenderId());
        payload.put("recipientId", event.getRecipientId());
        payload.put("requestId", event.getRequestId());
        payload.put("type", event.getType().name());
        return payload;
    }

    /**
     * Builds payload for friendship status change events
     */
    private Map<String, Object> buildFriendshipStatusPayload(FriendshipEventType eventType, FriendshipPayload payload) {
        Map<String, Object> wsPayload = new HashMap<>();
        wsPayload.put("userLow", payload.getUserLow());
        wsPayload.put("userHigh", payload.getUserHigh());
        wsPayload.put("actionUserId", payload.getActionUserId());
        wsPayload.put("newStatus", payload.getStatus());
        wsPayload.put("eventType", eventType.value());
        return wsPayload;
    }

}
