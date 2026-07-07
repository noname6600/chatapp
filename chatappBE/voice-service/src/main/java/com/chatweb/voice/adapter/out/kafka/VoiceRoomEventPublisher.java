package com.chatweb.voice.adapter.out.kafka;

import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.event.EventMetadata;
import com.chatweb.common.integration.voice.VoiceEventType;
import com.chatweb.common.integration.voice.VoiceRoomEventPayload;
import com.chatweb.common.kafka.topic.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class VoiceRoomEventPublisher {

    private static final String SOURCE = "voice-service";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishJoined(UUID chatRoomId, UUID userId, String username, String avatarUrl, int participantCount, String lkRoomName) {
        publish(VoiceEventType.VOICE_ROOM_JOINED, chatRoomId, userId, username, avatarUrl, participantCount, lkRoomName);
    }

    public void publishLeft(UUID chatRoomId, UUID userId, String username, String avatarUrl, int participantCount, String lkRoomName) {
        publish(VoiceEventType.VOICE_ROOM_LEFT, chatRoomId, userId, username, avatarUrl, participantCount, lkRoomName);
    }

    public void publishClosed(UUID chatRoomId, String lkRoomName) {
        publish(VoiceEventType.VOICE_ROOM_CLOSED, chatRoomId, null, null, null, 0, lkRoomName);
    }

    private void publish(VoiceEventType type, UUID chatRoomId, UUID userId, String username, String avatarUrl, int participantCount, String lkRoomName) {
        VoiceRoomEventPayload payload = VoiceRoomEventPayload.builder()
                .eventType(type.value())
                .chatRoomId(chatRoomId)
                .lkRoomName(lkRoomName)
                .userId(userId)
                .username(username)
                .avatarUrl(avatarUrl)
                .participantCount(participantCount)
                .timestamp(System.currentTimeMillis())
                .build();

        String eventId = UUID.randomUUID().toString();
        EventMetadata metadata = EventMetadata.of(eventId, type.value(), SOURCE, Instant.now());
        EventEnvelope<VoiceRoomEventPayload> envelope = new EventEnvelope<>(metadata, payload);

        kafkaTemplate.send(KafkaTopics.TOPIC_VOICE_ROOM_EVENTS, chatRoomId.toString(), envelope);
        log.debug("[VOICE-KAFKA] Published eventType={} chatRoomId={} userId={}", type.value(), chatRoomId, userId);
    }
}
