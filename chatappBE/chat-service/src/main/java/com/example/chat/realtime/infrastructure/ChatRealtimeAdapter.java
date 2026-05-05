package com.example.chat.realtime.infrastructure;

import com.example.chat.modules.message.infrastructure.redis.ChatRedisPublisher;
import com.example.chat.modules.room.dto.RoomMessagePinEventPayload;
import com.example.chat.realtime.port.ChatRealtimePort;
import com.example.common.integration.chat.ChatEventType;
import com.example.common.realtime.policy.RealtimeFlowClassificationPolicy;
import com.example.common.realtime.policy.RealtimeFlowId;
import com.example.common.realtime.policy.RealtimeFlowType;
import com.example.common.websocket.protocol.RealtimeWsEvent;
import com.example.common.websocket.session.IRoomBroadcaster;
import com.example.common.websocket.session.IUserBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRealtimeAdapter implements ChatRealtimePort {

    private final IRoomBroadcaster roomBroadcaster;
    private final IUserBroadcaster userBroadcaster;
    private final ChatRedisPublisher chatRedisPublisher;

    @Override
    public void publishRoomEvent(UUID roomId, String eventType, Object payload) {
        if (isPinEvent(eventType) && payload instanceof RoomMessagePinEventPayload pinPayload) {
            if (Objects.equals(eventType, ChatEventType.MESSAGE_PINNED.value())) {
                chatRedisPublisher.publishMessagePinned(pinPayload);
                return;
            }
            if (Objects.equals(eventType, ChatEventType.MESSAGE_UNPINNED.value())) {
                chatRedisPublisher.publishMessageUnpinned(pinPayload);
                return;
            }
        }

        roomBroadcaster.sendToRoom(roomId, RealtimeWsEvent.builder()
                .type(eventType)
                .payload(payload)
                .build());
    }

    @Override
    public void publishUserEvent(UUID userId, String eventType, Object payload) {
        userBroadcaster.sendToUser(userId, RealtimeWsEvent.builder()
                .type(eventType)
                .payload(payload)
                .build());
    }

    @Override
    public void publishRoomEvent(UUID roomId, String eventType, Object payload, RealtimeFlowId flowId) {
        RealtimeFlowType flowType = RealtimeFlowClassificationPolicy.getFlowType(flowId);
        
        log.debug("Publishing room event for flow {}: eventType={}, flowType={}", 
                flowId, eventType, flowType);

        // Enforce delivery semantics based on flow classification
        if (flowType == RealtimeFlowType.DURABLE_FIRST) {
            // DURABLE-FIRST: publish to Kafka first, then fanout
            // TODO: Implement Kafka publish in task 6.2/7.1
            publishDurableFirstFlow(roomId, eventType, payload);
        } else if (flowType == RealtimeFlowType.EPHEMERAL_ONLY) {
            // EPHEMERAL-ONLY: direct Redis/WebSocket fanout
            publishEphemeralOnlyFlow(roomId, eventType, payload);
        } else if (flowType == RealtimeFlowType.MIXED_WITH_CONVERGENCE) {
            // MIXED-WITH-CONVERGENCE: publish to both Kafka and fanout
            // TODO: Implement Kafka publish in task 6.2/7.1
            publishMixedConvergenceFlow(roomId, eventType, payload);
        }
    }

    @Override
    public void publishUserEvent(UUID userId, String eventType, Object payload, RealtimeFlowId flowId) {
        RealtimeFlowType flowType = RealtimeFlowClassificationPolicy.getFlowType(flowId);
        
        log.debug("Publishing user event for flow {}: eventType={}, flowType={}", 
                flowId, eventType, flowType);

        // Enforce delivery semantics based on flow classification
        if (flowType == RealtimeFlowType.DURABLE_FIRST) {
            // DURABLE-FIRST: publish to Kafka first, then fanout
            // TODO: Implement Kafka publish in task 6.2/7.1
            publishDurableFirstUserFlow(userId, eventType, payload);
        } else if (flowType == RealtimeFlowType.EPHEMERAL_ONLY) {
            // EPHEMERAL-ONLY: direct Redis/WebSocket fanout
            publishEphemeralOnlyUserFlow(userId, eventType, payload);
        } else if (flowType == RealtimeFlowType.MIXED_WITH_CONVERGENCE) {
            // MIXED-WITH-CONVERGENCE: publish to both Kafka and fanout
            // TODO: Implement Kafka publish in task 6.2/7.1
            publishMixedConvergenceUserFlow(userId, eventType, payload);
        }
    }

    private void publishDurableFirstFlow(UUID roomId, String eventType, Object payload) {
        // For now, direct fanout; Kafka durability added in later tasks
        directFanoutRoom(roomId, eventType, payload);
    }

    private void publishEphemeralOnlyFlow(UUID roomId, String eventType, Object payload) {
        directFanoutRoom(roomId, eventType, payload);
    }

    private void publishMixedConvergenceFlow(UUID roomId, String eventType, Object payload) {
        // For now, direct fanout; Kafka publish and convergence added in later tasks
        directFanoutRoom(roomId, eventType, payload);
    }

    private void publishDurableFirstUserFlow(UUID userId, String eventType, Object payload) {
        // For now, direct fanout; Kafka durability added in later tasks
        directFanoutUser(userId, eventType, payload);
    }

    private void publishEphemeralOnlyUserFlow(UUID userId, String eventType, Object payload) {
        directFanoutUser(userId, eventType, payload);
    }

    private void publishMixedConvergenceUserFlow(UUID userId, String eventType, Object payload) {
        // For now, direct fanout; Kafka publish and convergence added in later tasks
        directFanoutUser(userId, eventType, payload);
    }

    private void directFanoutRoom(UUID roomId, String eventType, Object payload) {
        roomBroadcaster.sendToRoom(roomId, RealtimeWsEvent.builder()
                .type(eventType)
                .payload(payload)
                .build());
    }

    private void directFanoutUser(UUID userId, String eventType, Object payload) {
        userBroadcaster.sendToUser(userId, RealtimeWsEvent.builder()
                .type(eventType)
                .payload(payload)
                .build());
    }

    private boolean isPinEvent(String eventType) {
        return Objects.equals(eventType, ChatEventType.MESSAGE_PINNED.value())
                || Objects.equals(eventType, ChatEventType.MESSAGE_UNPINNED.value());
    }
}

