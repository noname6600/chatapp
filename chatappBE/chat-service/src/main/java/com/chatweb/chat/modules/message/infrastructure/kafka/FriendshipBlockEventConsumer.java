package com.chatweb.chat.modules.message.infrastructure.kafka;

import com.chatweb.chat.modules.message.application.pipeline.send.steps.CheckBlockedPairStep;
import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.integration.friendship.FriendshipPayload;
import com.chatweb.common.kafka.topic.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FriendshipBlockEventConsumer {

    private static final String CONSUMER_GROUP_ID = "chat-service-block-cache";

    private final CheckBlockedPairStep checkBlockedPairStep;

    @KafkaListener(topics = KafkaTopics.TOPIC_FRIENDSHIP_EVENTS, groupId = CONSUMER_GROUP_ID)
    public void onFriendshipEvent(ConsumerRecord<String, EventEnvelope<FriendshipPayload>> record) {
        try {
            EventEnvelope<FriendshipPayload> envelope = record.value();
            if (envelope == null || envelope.payload() == null) {
                return;
            }

            String eventType = envelope.metadata() != null ? envelope.metadata().getEventType() : null;
            if (!"friend.blocked".equals(eventType) && !"friend.unblocked".equals(eventType)) {
                return;
            }

            FriendshipPayload payload = envelope.payload();
            if (payload.getUserLow() == null || payload.getUserHigh() == null) {
                return;
            }

            checkBlockedPairStep.invalidateCache(payload.getUserLow(), payload.getUserHigh());
            log.debug("[BLOCK-CACHE] Invalidated blocked-pair cache for userLow={} userHigh={} event={}",
                    payload.getUserLow(), payload.getUserHigh(), eventType);
        } catch (Exception ex) {
            log.warn("[BLOCK-CACHE] Failed to process friendship event for cache invalidation", ex);
        }
    }
}
