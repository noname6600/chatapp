package com.chatweb.realtime.dispatch;

import com.chatweb.realtime.connection.RealtimeSession;
import com.chatweb.realtime.connection.RealtimeSessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Metrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Splits local and remote-owned sessions and publishes remote handoff events.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EdgeCrossInstanceDispatchCoordinator {

    private final RealtimeSessionRegistry sessionRegistry;
    private final EdgeDeliveryHandoffPublisher handoffPublisher;
    private final ObjectMapper objectMapper;

    @Value("${realtime.dispatch.handoff.enabled:false}")
    private boolean handoffEnabled;

    public DeliveryOwnershipSplit splitByOwnership(Collection<RealtimeSession> sessions) {
        String localInstanceId = sessionRegistry.getCurrentInstanceId();
        List<RealtimeSession> localOwned = sessions.stream()
                .filter(s -> isOwnedByInstance(s, localInstanceId))
                .collect(Collectors.toList());

        Map<String, List<RealtimeSession>> remoteByInstance = sessions.stream()
                .filter(s -> !isOwnedByInstance(s, localInstanceId))
                .filter(s -> s.getInstanceId() != null && !s.getInstanceId().isBlank())
                .collect(Collectors.groupingBy(RealtimeSession::getInstanceId));

        return new DeliveryOwnershipSplit(localOwned, remoteByInstance);
    }

    public int publishRemoteHandoffs(
            DeliveryOwnershipSplit split,
            String deliveryType,
            String eventType,
            String originalEventId,
            String subscriptionKey,
            UUID targetUserId,
            Object payload
    ) {
        if (!handoffEnabled || split == null || split.remoteByInstance().isEmpty()) {
            return 0;
        }

        int published = 0;
        String sourceInstanceId = sessionRegistry.getCurrentInstanceId();
        for (Map.Entry<String, List<RealtimeSession>> entry : split.remoteByInstance().entrySet()) {
            String targetInstanceId = entry.getKey();
            List<RealtimeSession> targetSessions = entry.getValue();

            EdgeDeliveryHandoffEvent event = EdgeDeliveryHandoffEvent.builder()
                    .handoffEventId(UUID.randomUUID().toString())
                    .createdAt(Instant.now())
                    .sourceInstanceId(sourceInstanceId)
                    .targetInstanceId(targetInstanceId)
                    .deliveryType(deliveryType)
                    .eventType(eventType)
                    .originalEventId(originalEventId)
                    .subscriptionKey(subscriptionKey)
                    .targetUserId(targetUserId == null ? null : targetUserId.toString())
                    .targetSessionIds(targetSessions.stream().map(RealtimeSession::getSessionId).collect(Collectors.toList()))
                    .payload(objectMapper.valueToTree(payload))
                    .build();

            if (handoffPublisher.publish(event)) {
                published++;
                Metrics.counter("realtime.dispatch.remote.handoff.count", "deliveryType", safeTag(deliveryType)).increment();
            } else {
                Metrics.counter("realtime.dispatch.remote.handoff.publish.failure", "deliveryType", safeTag(deliveryType)).increment();
            }
        }

        if (!split.remoteByInstance().isEmpty()) {
            log.debug("[HANDOFF][COORD] deliveryType={} eventType={} originalEventId={} remoteInstances={} handoffsPublished={}",
                    deliveryType,
                    eventType,
                    originalEventId,
                    split.remoteByInstance().size(),
                    published);
        }
        return published;
    }

    private String safeTag(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private boolean isOwnedByInstance(RealtimeSession session, String instanceId) {
        return session != null
                && instanceId != null
                && instanceId.equals(session.getInstanceId());
    }

    public record DeliveryOwnershipSplit(
            List<RealtimeSession> localOwned,
            Map<String, List<RealtimeSession>> remoteByInstance
    ) {
    }
}
