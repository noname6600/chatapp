package com.example.chat.modules.message.application.dto.response;

import com.example.chat.modules.message.domain.enums.MessageType;
import com.example.chat.modules.message.domain.enums.SystemEventType;
import lombok.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageResponse {

    private UUID messageId;

    private UUID roomId;

    private UUID senderId;

    private Long seq;

    private MessageType type;

    private String content;

    private UUID replyToMessageId;

    private UUID forwardedFromMessageId;

    private SystemEventType systemEventType;

    private UUID actorUserId;

    private UUID targetMessageId;

    private Instant createdAt;

    private Instant editedAt;

    private Boolean deleted;

    private List<AttachmentResponse> attachments;

    private List<MessageBlockResponse> blocks;

    private List<ReactionResponse> reactions;

    private String clientMessageId;

}
