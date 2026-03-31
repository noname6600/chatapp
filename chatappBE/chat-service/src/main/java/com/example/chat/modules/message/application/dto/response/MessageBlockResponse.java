package com.example.chat.modules.message.application.dto.response;

import com.example.chat.modules.message.domain.enums.MessageBlockType;
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
public class MessageBlockResponse {

    private MessageBlockType type;

    private String text;

    private AttachmentResponse attachment;
}