package com.example.chat.modules.message.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomInviteResponse {

    private UUID roomId;

    private String roomName;

    private String roomAvatarUrl;

    private Integer memberCount;
}
