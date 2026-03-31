package com.example.chat.modules.message.application.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter
@Builder
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DeleteMessageRequest {
    private UUID messageId;
    private UUID actorId;

}