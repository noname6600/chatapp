package com.chatweb.chat.modules.room.dto;

import com.chatweb.chat.modules.room.enums.Role;
import com.chatweb.chat.modules.room.enums.RoomType;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoomResponse {

    private UUID id;

    private RoomType type;

    private String name;

    private String avatarUrl;

    private UUID createdBy;

    private Instant createdAt;

    private Role myRole;

    private Integer unreadCount;

    private UUID otherUserId;

    private String latestMessageAt;

    private LastMessagePreview lastMessage;
}

