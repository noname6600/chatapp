package com.example.chat.modules.message.application.dto.request;

import com.example.chat.modules.message.domain.enums.MessageBlockType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageBlockRequest {

    @NotNull(message = "block type is required")
    private MessageBlockType type;

    @Size(max = 5000, message = "block text too long")
    private String text;

    @Valid
    private AttachmentRequest attachment;
}