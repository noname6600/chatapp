package com.example.chat.modules.room.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomMemberJoinedPayload {

    private UUID roomId;

    private UUID userId;

    private String role;

    private Instant joinedAt;
}
