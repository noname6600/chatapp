package com.chatweb.chat.modules.room.dto;

import com.chatweb.common.integration.chat.MessagePinPayload;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomMessagePinEventPayload {

    private UUID eventId;
    private UUID roomId;
    private UUID messageId;
    private UUID actorId;
    private Instant occurredAt;

    public static RoomMessagePinEventPayload fromExternal(
            MessagePinPayload payload,
            String eventId,
            Instant fallbackOccurredAt
    ) {
        return RoomMessagePinEventPayload.builder()
                .eventId(parseEventId(eventId))
                .roomId(payload.getRoomId())
                .messageId(payload.getMessageId())
                .actorId(payload.getActorUserId())
                .occurredAt(payload.getPinnedAt() != null ? payload.getPinnedAt() : fallbackOccurredAt)
                .build();
    }

    private static UUID parseEventId(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(eventId);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
