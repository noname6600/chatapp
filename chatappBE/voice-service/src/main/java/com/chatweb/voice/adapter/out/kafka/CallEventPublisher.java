package com.chatweb.voice.adapter.out.kafka;

import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.event.EventMetadata;
import com.chatweb.common.integration.voice.CallEventPayload;
import com.chatweb.common.integration.voice.VoiceEventType;
import com.chatweb.common.kafka.topic.KafkaTopics;
import com.chatweb.voice.domain.model.CallState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class CallEventPublisher {

    private static final String SOURCE = "voice-service";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishInitiated(CallState state,
                                 String callerName, String callerAvatarUrl,
                                 String calleeName, String calleeAvatarUrl,
                                 String lkExternalUrl) {
        publish(VoiceEventType.CALL_INITIATED, state,
                callerName, callerAvatarUrl, calleeName, calleeAvatarUrl,
                null, null, lkExternalUrl, null);
    }

    public void publishAccepted(CallState state,
                                String callerName, String callerAvatarUrl,
                                String calleeName, String calleeAvatarUrl,
                                String callerToken, String calleeToken,
                                String lkExternalUrl) {
        publish(VoiceEventType.CALL_ACCEPTED, state,
                callerName, callerAvatarUrl, calleeName, calleeAvatarUrl,
                callerToken, calleeToken, lkExternalUrl, null);
    }

    public void publishDeclined(CallState state) {
        publish(VoiceEventType.CALL_DECLINED, state, null, null, null, null, null, null, null, null);
    }

    public void publishCancelled(CallState state) {
        publish(VoiceEventType.CALL_CANCELLED, state, null, null, null, null, null, null, null, null);
    }

    public void publishEnded(CallState state, int durationSeconds) {
        publish(VoiceEventType.CALL_ENDED, state, null, null, null, null, null, null, null, durationSeconds);
    }

    public void publishMissed(CallState state) {
        publish(VoiceEventType.CALL_MISSED, state, null, null, null, null, null, null, null, null);
    }

    private void publish(VoiceEventType type, CallState state,
                         String callerName, String callerAvatarUrl,
                         String calleeName, String calleeAvatarUrl,
                         String callerToken, String calleeToken,
                         String lkExternalUrl, Integer durationSeconds) {
        CallEventPayload payload = CallEventPayload.builder()
                .eventType(type.value())
                .callId(state.callId())
                .callerId(state.callerId())
                .callerName(callerName)
                .callerAvatarUrl(callerAvatarUrl)
                .calleeId(state.calleeId())
                .calleeName(calleeName)
                .calleeAvatarUrl(calleeAvatarUrl)
                .callerLkToken(callerToken)
                .calleeLkToken(calleeToken)
                .lkRoomName(state.lkRoomName())
                .lkExternalUrl(lkExternalUrl)
                .durationSeconds(durationSeconds)
                .timestamp(System.currentTimeMillis())
                .build();

        String eventId = UUID.randomUUID().toString();
        EventMetadata metadata = EventMetadata.of(eventId, type.value(), SOURCE, Instant.now());
        EventEnvelope<CallEventPayload> envelope = new EventEnvelope<>(metadata, payload);

        kafkaTemplate.send(KafkaTopics.TOPIC_VOICE_CALL_EVENTS, state.callId().toString(), envelope);
        log.debug("[CALL-KAFKA] Published eventType={} callId={}", type.value(), state.callId());
    }
}
