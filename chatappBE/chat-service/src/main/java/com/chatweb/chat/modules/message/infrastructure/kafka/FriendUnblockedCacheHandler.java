package com.chatweb.chat.modules.message.infrastructure.kafka;

import com.chatweb.chat.modules.message.application.pipeline.send.steps.CheckBlockedPairStep;
import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.integration.friendship.FriendshipPayload;
import com.chatweb.common.kafka.consumer.KafkaEventHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FriendUnblockedCacheHandler implements KafkaEventHandler<FriendshipPayload> {

    private final CheckBlockedPairStep checkBlockedPairStep;

    @Override
    public String eventType() {
        return "friend.unblocked";
    }

    @Override
    public void handle(EventEnvelope<FriendshipPayload> event) {
        FriendshipPayload payload = event.payload();
        if (payload == null || payload.getUserLow() == null || payload.getUserHigh() == null) return;
        checkBlockedPairStep.invalidateCache(payload.getUserLow(), payload.getUserHigh());
        log.debug("[BLOCK-CACHE] Invalidated cache userLow={} userHigh={} event={}",
                payload.getUserLow(), payload.getUserHigh(), "friend.unblocked");
    }
}
