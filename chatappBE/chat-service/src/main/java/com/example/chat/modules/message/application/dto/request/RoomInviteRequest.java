package com.example.chat.modules.message.application.dto.request;

import jakarta.validation.constraints.NotNull;
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
public class RoomInviteRequest {

    @NotNull(message = "roomInvite.roomId is required")
    private UUID roomId;

    private String roomName;

    private String roomAvatarUrl;

    private Integer memberCount;
}
