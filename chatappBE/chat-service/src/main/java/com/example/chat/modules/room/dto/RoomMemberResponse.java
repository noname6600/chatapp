package com.example.chat.modules.room.dto;

import com.example.chat.modules.room.enums.Role;
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
public class RoomMemberResponse {

    private UUID userId;

    private String name;

    private String avatarUrl;

    private Role role;

    private Instant joinedAt;
}
