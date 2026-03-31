package com.example.chat.modules.message.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter
@Builder
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EditMessageRequest {
    private UUID messageId;

    private UUID actorId;

    @NotBlank
    private String content;

    // Optional: blocks JSON for mixed message editing (preserves interleaved text/media structure)
    // If provided, blocks will be used instead of decomposing from content
    private String blocksJson;

}