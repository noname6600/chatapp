package com.example.chat.modules.message.application.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ForwardMessageRequest {

    @NotNull(message = "sourceMessageId is required")
    private UUID sourceMessageId;

    @NotNull(message = "targetRoomId is required")
    private UUID targetRoomId;

    private UUID actorId;
}
