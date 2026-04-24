package com.example.chat.modules.room.dto;

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
}
