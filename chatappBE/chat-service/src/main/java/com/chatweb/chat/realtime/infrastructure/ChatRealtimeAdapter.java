package com.chatweb.chat.realtime.infrastructure;

import com.chatweb.chat.modules.message.infrastructure.redis.ChatRedisPublisher;
import com.chatweb.chat.modules.room.dto.RoomMessagePinEventPayload;
import com.chatweb.chat.realtime.port.ChatRealtimePort;
import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.event.EventMetadata;
import com.chatweb.common.integration.chat.ChatEventType;
import com.chatweb.common.integration.chat.MessagePinPayload;
import com.chatweb.common.kafka.producer.KafkaEventPublisher;
import com.chatweb.common.kafka.topic.KafkaTopics;
import com.chatweb.common.realtime.policy.RealtimeFlowClassificationPolicy;
import com.chatweb.common.realtime.policy.RealtimeFlowId;
import com.chatweb.common.realtime.policy.RealtimeFlowType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Realtime delivery adapter for chat events.
 *
 * <p><strong>Responsibility:</strong>
 * Routes chat events through appropriate delivery paths (websocket direct fanout, Redis pub/sub, Kafka durable).
 * Implements ChatRealtimePort to provide a stable interface for domain services.
 *
 * <p><strong>Current Implementation Status:</strong>
 * This adapter has INCOMPLETE flow policy implementation:
 * - Durable-first flows (MESSAGE_PINNED, MESSAGE_UNPINNED) have TODO comments for Kafka publish
 * - Currently all flows default to direct fanout (websocket or redis)
 * - Kafka durability is NOT YET IMPLEMENTED despite being in the flow classification
 *
 * <p><strong>Delivery Paths (Current):</strong>
 * - Pin/Unpin events: routed via ChatRedisPublisher (redis pub/sub) OR direct websocket fanout
 * - Room/User events: direct websocket fanout via broadcasters
 *
 * <p><strong>Delivery Paths (Incomplete):</strong>
 * - MESSAGE_PINNED: should publish to Kafka first, then fanout (DURABLE_FIRST)
 * - MESSAGE_UNPINNED: should publish to Kafka first, then fanout (DURABLE_FIRST)
 *
 * <p><strong>Future Work:</strong>
 * Implement Kafka publish in flow policy methods when durable-first semantics are required.
 * Currently, the flow classification logic exists but Kafka publish is missing.
 *
 * @see com.chatweb.common.realtime.policy.RealtimeFlowClassificationPolicy
 * @see ChatMessageEventPublisherAdapter
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRealtimeAdapter implements ChatRealtimePort {

    private final ChatRedisPublisher chatRedisPublisher;
    private final KafkaEventPublisher kafkaEventPublisher;

    @Value("${spring.application.name}")
    private String sourceService;

    @Override
    public void publishRoomEvent(UUID roomId, String eventType, Object payload) {
        if (isPinEvent(eventType)) {
            MessagePinPayload pinPayload = toMessagePinPayload(payload);
            if (pinPayload == null) {
                throw new IllegalArgumentException("Pin/unpin events require MessagePinPayload-compatible payload");
            }
            if (Objects.equals(eventType, ChatEventType.MESSAGE_PINNED.value())) {
                chatRedisPublisher.publishMessagePinned(pinPayload);
                return;
            }
            if (Objects.equals(eventType, ChatEventType.MESSAGE_UNPINNED.value())) {
                chatRedisPublisher.publishMessageUnpinned(pinPayload);
                return;
            }
        }

        chatRedisPublisher.publishRoomRealtimeEvent(roomId, eventType, payload);
    }

    @Override
    public void publishUserEvent(UUID userId, String eventType, Object payload) {
        log.debug("Skipping chat user event fanout in chat-service after realtime-edge migration: userId={}, eventType={}",
            userId, eventType);
    }

    @Override
    public void publishRoomEvent(UUID roomId, String eventType, Object payload, RealtimeFlowId flowId) {
        RealtimeFlowType flowType = RealtimeFlowClassificationPolicy.getFlowType(flowId);
        
        log.debug("Publishing room event for flow {}: eventType={}, flowType={}", 
                flowId, eventType, flowType);

        // Enforce delivery semantics based on flow classification
        if (flowType == RealtimeFlowType.DURABLE_FIRST) {
            // DURABLE-FIRST: publish to Kafka first (pin events only for now), then fanout
            if (isPinEvent(eventType)) {
                publishPinEventToKafka(roomId, eventType, payload);
            }
            publishDurableFirstFlow(roomId, eventType, payload);
        } else if (flowType == RealtimeFlowType.EPHEMERAL_ONLY) {
            // EPHEMERAL-ONLY: direct Redis/WebSocket fanout
            publishEphemeralOnlyFlow(roomId, eventType, payload);
        } else if (flowType == RealtimeFlowType.MIXED_WITH_CONVERGENCE) {
            // MIXED-WITH-CONVERGENCE: publish to both Kafka (pin events only) and fanout
            if (isPinEvent(eventType)) {
                publishPinEventToKafka(roomId, eventType, payload);
            }
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

    private void publishPinEventToKafka(UUID roomId, String eventType, Object payload) {
        if (!(payload instanceof MessagePinPayload pinPayload)) {
            throw new IllegalArgumentException("Pin/unpin events require MessagePinPayload-compatible payload");
        }

        String eventId = UUID.randomUUID().toString();
        EventEnvelope<MessagePinPayload> envelope = new EventEnvelope<>(
                new EventMetadata(eventId, eventType, sourceService, Instant.now(), eventId),
                pinPayload
        );
        kafkaEventPublisher.publish(KafkaTopics.TOPIC_CHAT_MESSAGE_EVENTS, roomId.toString(), envelope);
    }

    private void directFanoutRoom(UUID roomId, String eventType, Object payload) {
        chatRedisPublisher.publishRoomRealtimeEvent(roomId, eventType, payload);
    }

    private void directFanoutUser(UUID userId, String eventType, Object payload) {
        log.debug("Skipping chat user event fanout in chat-service after realtime-edge migration: userId={}, eventType={}",
            userId, eventType);
    }

    private boolean isPinEvent(String eventType) {
        return Objects.equals(eventType, ChatEventType.MESSAGE_PINNED.value())
                || Objects.equals(eventType, ChatEventType.MESSAGE_UNPINNED.value());
    }

    private MessagePinPayload toMessagePinPayload(Object payload) {
        if (payload instanceof MessagePinPayload pinPayload) {
            return pinPayload;
        }
        if (payload instanceof RoomMessagePinEventPayload roomPayload) {
            return MessagePinPayload.builder()
                    .roomId(roomPayload.getRoomId())
                    .messageId(roomPayload.getMessageId())
                    .actorUserId(roomPayload.getActorId())
                    .pinnedAt(roomPayload.getOccurredAt())
                    .build();
        }
        return null;
    }
}

