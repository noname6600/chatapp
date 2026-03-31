package com.example.chat.modules.message.application.dto.request;

import com.example.common.integration.enums.MessageType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SendMessageRequest {

    @NotNull(message = "roomId is required")
    private UUID roomId;

    private UUID senderId;

    @Size(max = 5000, message = "content too long")
    private String content;

    private UUID replyToMessageId;

    @Valid
    private List<AttachmentRequest> attachments;

    @Valid
    private List<MessageBlockRequest> blocks;

    @Size(max = 100, message = "clientMessageId too long")
    private String clientMessageId;

    private List<UUID> mentionedUserIds;

}
