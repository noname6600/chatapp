package com.chatweb.chat.modules.message.infrastructure.kafka;

import com.chatweb.chat.modules.message.application.service.ISystemMessageService;
import com.chatweb.chat.modules.message.domain.enums.SystemEventType;
import com.chatweb.chat.modules.room.repository.PrivateRoomRepository;
import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.integration.voice.CallEventPayload;
import com.chatweb.common.kafka.topic.KafkaTopics;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class CallEventConsumer {

    private static final String GROUP_ID = "chat-service-call-history";

    private final ISystemMessageService systemMessageService;
    private final PrivateRoomRepository privateRoomRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.TOPIC_VOICE_CALL_EVENTS, groupId = GROUP_ID)
    public void onCallEvent(EventEnvelope<?> envelope) {
        if (envelope == null || envelope.payload() == null) return;
        try {
            CallEventPayload payload = objectMapper.convertValue(envelope.payload(), CallEventPayload.class);
            if (payload == null || payload.getCallerId() == null || payload.getCalleeId() == null) return;

            String eventType = payload.getEventType();
            if (eventType == null) return;

            UUID callerId = payload.getCallerId();
            UUID calleeId = payload.getCalleeId();

            Optional<UUID> roomIdOpt = privateRoomRepository
                    .findByUser1IdAndUser2Id(callerId, calleeId)
                    .or(() -> privateRoomRepository.findByUser1IdAndUser2Id(calleeId, callerId))
                    .map(r -> r.getRoomId());

            if (roomIdOpt.isEmpty()) {
                log.debug("[CALL-HISTORY] No DM room found for caller={} callee={}", callerId, calleeId);
                return;
            }

            UUID roomId = roomIdOpt.get();
            String callerName = payload.getCallerName() != null ? payload.getCallerName() : "Someone";
            String calleeName = payload.getCalleeName() != null ? payload.getCalleeName() : "Someone";

            switch (eventType) {
                case "voice.call.initiated" -> systemMessageService.sendRawSystemMessage(
                        roomId, callerId, SystemEventType.CALL_STARTED,
                        "📞 " + callerName + " started a call");

                case "voice.call.ended" -> {
                    String duration = formatDuration(payload.getDurationSeconds());
                    systemMessageService.sendRawSystemMessage(
                            roomId, callerId, SystemEventType.CALL_ENDED,
                            "📞 Call ended" + (duration != null ? " · " + duration : ""));
                }

                case "voice.call.missed" -> systemMessageService.sendRawSystemMessage(
                        roomId, calleeId, SystemEventType.CALL_MISSED,
                        "📞 Missed call from " + callerName);

                case "voice.call.declined" -> systemMessageService.sendRawSystemMessage(
                        roomId, calleeId, SystemEventType.CALL_DECLINED,
                        "📞 " + calleeName + " declined the call");

                case "voice.call.cancelled" -> systemMessageService.sendRawSystemMessage(
                        roomId, callerId, SystemEventType.CALL_CANCELLED,
                        "📞 " + callerName + " cancelled the call");

                default -> log.debug("[CALL-HISTORY] Ignoring event type={}", eventType);
            }
        } catch (Exception ex) {
            log.warn("[CALL-HISTORY] Failed to process call event", ex);
        }
    }

    private String formatDuration(Integer seconds) {
        if (seconds == null || seconds <= 0) return null;
        int m = seconds / 60;
        int s = seconds % 60;
        if (m == 0) return s + "s";
        return m + "m " + s + "s";
    }
}
